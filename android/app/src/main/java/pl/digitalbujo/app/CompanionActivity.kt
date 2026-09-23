package pl.digitalbujo.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.widget.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.integration.android.IntentIntegrator
import com.journeyapps.barcodescanner.BarcodeEncoder
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Optional tools live outside the journal index and never mutate it without review. */
class CompanionActivity : Activity() {
    private lateinit var repository: JournalRepository
    private lateinit var appearance: Appearance
    private lateinit var body: LinearLayout
    private val fields = linkedMapOf<String,EditText>()
    private var mode = "settings"
    private var formKind = ""
    private var editing: String? = null
    private var dialog: AlertDialog? = null
    private var draft: Bundle? = null
    private var selectedProvider = "openai"
    private var selectedTheme = "Light"
    private var request: AiClient? = null
    private val worker = Executors.newSingleThreadExecutor()
    private var qrValue = ""
    private var aiKind = "spreads"
    private var taskId: String? = null
    private val timerHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private var timerAnnounced=false
    private var suggestions: List<JSONObject> = emptyList()
    private var checkedSuggestions = booleanArrayOf()
    private val pid get() = intent.getStringExtra("profile") ?: error("Choose a profile first.")
    private val jid get() = intent.getStringExtra("journal") ?: error("Choose a journal first.")
    private fun profile(state: JSONObject = repository.read()) = JournalData.profile(state,pid)
    private fun dp(v: Int) = (v*resources.displayMetrics.density).toInt()
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        try { repository=JournalRepository(this); appearance=Appearance(this) } catch(e: Exception) { AlertDialog.Builder(this).setMessage(e.message).setPositiveButton(I18n.t(this@CompanionActivity,"Close")) { _,_->finish() }.show(); return }
        mode=saved?.getString("mode") ?: intent.getStringExtra("mode") ?: "settings"
        editing=saved?.getString("editing"); draft=saved?.getBundle("fields"); qrValue=saved?.getString("qr","") ?: ""
        selectedProvider=saved?.getString("provider","openai") ?: "openai"; selectedTheme=saved?.getString("theme") ?: appearance.name
        aiKind=saved?.getString("aiKind") ?: intent.getStringExtra("kind") ?: "spreads"; taskId=saved?.getString("taskId")
        saved?.getString("suggestions")?.let { suggestions=JournalData.list(org.json.JSONArray(it)) }
        checkedSuggestions=saved?.getBooleanArray("checked") ?: BooleanArray(suggestions.size)
        render()
        draft=saved?.getBundle("fields")
        when(saved?.getString("form")) { "task" -> taskForm(editing); "spread" -> spreadForm(); "review" -> review() }
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("mode",mode);out.putString("form",formKind);out.putString("editing",editing);out.putString("qr",qrValue);out.putString("provider",selectedProvider);out.putString("theme",selectedTheme);out.putString("aiKind",aiKind);out.putString("taskId",taskId)
        out.putString("suggestions",org.json.JSONArray(suggestions).toString());out.putBooleanArray("checked",checkedSuggestions)
        out.putBundle("fields",Bundle().apply { fields.filterKeys { it != "key" }.forEach { (k,v)->putString(k,v.text.toString()) } })
        super.onSaveInstanceState(out)
    }
    override fun onDestroy() { request?.cancel(); worker.shutdownNow(); timerHandler.removeCallbacksAndMessages(null); super.onDestroy() }
    private fun column()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun text(parent: LinearLayout, value: String, size: Float=16f)=TextView(this).apply { text=value; textSize=size; setTextColor(appearance.color("text"));setPadding(0,dp(8),0,dp(8));parent.addView(this) }
    private fun button(parent: LinearLayout, label: String, action:()->Unit)=Button(this).apply { text=I18n.t(this@CompanionActivity,label);isAllCaps=false;minHeight=dp(48);setTextColor(appearance.color("text"));backgroundTintList=android.content.res.ColorStateList.valueOf(appearance.color("bar"));parent.addView(this);setOnClickListener { try { action() } catch(e: Exception) { problem(e) } } }
    private fun input(parent: LinearLayout, key: String, label: String, value: String="", multiline: Boolean=false, max: Int=120):EditText {
        text(parent,I18n.t(this,label),13f)
        return EditText(this).apply { contentDescription=I18n.t(this@CompanionActivity,label);setTextColor(appearance.color("text"));setHintTextColor(appearance.color("text"));inputType=InputType.TYPE_CLASS_TEXT or if(multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0;setSingleLine(!multiline);filters=arrayOf(android.text.InputFilter.LengthFilter(max));setText(draft?.getString(key) ?: value);parent.addView(this);fields[key]=this }
    }
    private fun value(key: String)=fields[key]?.text?.toString() ?: ""
    private fun select(parent: LinearLayout,label: String,options: List<String>,initial: String,change:(String)->Unit):Spinner {
        text(parent,I18n.t(this,label),13f)
        return Spinner(this).apply { contentDescription=I18n.t(this@CompanionActivity,label);adapter=appearance.adapter(options);setSelection(options.indexOf(initial).coerceAtLeast(0));minimumHeight=dp(48);parent.addView(this);onItemSelectedListener=object:AdapterView.OnItemSelectedListener {override fun onNothingSelected(p:AdapterView<*>?){};override fun onItemSelected(p:AdapterView<*>?,v:View?,position:Int,id:Long){change(options[position])}} }
    }
    private fun root(title: String) {
        fields.clear(); val outer=column();appearance.background(outer)
        outer.setPadding(dp(20),dp(16),dp(20),dp(16))
        outer.setOnApplyWindowInsetsListener { v,i -> if(android.os.Build.VERSION.SDK_INT>=30){val bars=i.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime());v.setPadding(dp(20)+bars.left,dp(12)+bars.top,dp(20)+bars.right,dp(12)+bars.bottom)} else { @Suppress("DEPRECATION") v.setPadding(dp(20),dp(12)+i.systemWindowInsetTop,dp(20),dp(12)+i.systemWindowInsetBottom) };i }
        button(outer,I18n.t(this,"Back to journal")) { finish() };text(outer,I18n.t(this,title),25f)
        body=column();val scroll=ScrollView(this);scroll.addView(body);outer.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));setContentView(outer);outer.requestApplyInsets()
    }
    private fun render() { when(mode) { "timer"->timer();"tasks"->tasks();"ai"->ai();"qr"->qr();"about"->about();"keys"->keys();else->settings() }; draft=null }
    private fun problem(e: Exception) { AlertDialog.Builder(this).setTitle(I18n.t(this@CompanionActivity,"Could not complete that action")).setMessage(e.message ?: "Please try again.").setPositiveButton(I18n.t(this@CompanionActivity,"OK"),null).show() }
    private fun toast(message:String)=Toast.makeText(this,I18n.t(this,message),Toast.LENGTH_LONG).show()
    private fun form(kind:String,title:String,build:(LinearLayout)->Unit,save:()->Unit) {
        fields.clear();formKind=kind;val content=column().apply {setPadding(dp(22),dp(8),dp(22),dp(12));setBackgroundColor(appearance.color("surface"))};build(content)
        val error=text(content,I18n.t(this,""),13f);val scroll=ScrollView(this).apply {addView(content)}
        val d=AlertDialog.Builder(this).setTitle(I18n.t(this,title)).setView(scroll).setNegativeButton(I18n.t(this@CompanionActivity,"Cancel"),null).setPositiveButton(I18n.t(this@CompanionActivity,"Save"),null).create();dialog=d
        d.setOnDismissListener { formKind="";fields.clear();draft=null;dialog=null }
        d.setOnShowListener {d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {try {save();d.dismiss();render()} catch(e:Exception){error.text=I18n.t(this,e.message ?: "Please try again.")}}};d.show();draft=null
    }
    private fun tasks() {
        root("Tasks & plans");text(body,I18n.t(this,"A small next step counts. Dates and subtasks are optional."))
        button(body,I18n.t(this,"Add task")) {editing=null;taskForm(null)}
        val all=TaskData.tasks(profile())
        if(all.isEmpty())text(body,I18n.t(this,"No tasks yet."))
        for(t in all) {
            val id=t.getString("id");text(body,t.getString("title"),22f);text(body,t.getString("due").ifEmpty {I18n.t(this,"No date")});text(body,t.getString("notes"))
            button(body,if(t.getBoolean("done")) "Reopen ${t.getString("title")}" else "Complete ${t.getString("title")}") {val done=t.getBoolean("done");repository.change {TaskData.toggle(profile(it),id)};render();if(!done)celebrate()}
            for(s in JournalData.list(t.getJSONArray("subtasks"))) CheckBox(this).apply {text=s.getString("title");setTextColor(appearance.color("text"));isChecked=s.getBoolean("done");body.addView(this);setOnCheckedChangeListener {_,checked -> try {repository.change {TaskData.toggle(profile(it),id,s.getString("id"))};if(checked)celebrate()} catch(e:Exception){problem(e)} }}
            button(body,I18n.t(this,"🍅 Focus timer")) {taskId=id;mode="timer";render()}
            button(body,I18n.t(this,"Edit task")) {editing=id;taskForm(id)}
            button(body,I18n.t(this,"Suggest small steps")) {taskId=id;aiKind="steps";mode="ai";render()}
            button(body,I18n.t(this,"Delete task")) {AlertDialog.Builder(this).setTitle(I18n.t(this@CompanionActivity,"Delete task?")).setMessage(I18n.t(this@CompanionActivity,"Remove this task and its subtasks from this profile?")).setNegativeButton(I18n.t(this@CompanionActivity,"Cancel"),null).setPositiveButton(I18n.t(this@CompanionActivity,"Delete")) {_,_->try{repository.change {TaskData.delete(profile(it),id)};if(Pomodoro(this).read()?.optString("taskId")==id)Pomodoro(this).reset();render()}catch(e:Exception){problem(e)}}.show()}
        }
    }
    private fun taskForm(id:String?, added:List<String> = emptyList()) {
        val t=id?.let {key->TaskData.tasks(profile()).find {it.getString("id")==key}}
        form("task",if(t==null)"Add task" else "Edit task",{p->input(p,"title","Task title",t?.getString("title") ?: "");input(p,"due","Planned date (YYYY-MM-DD, optional)",t?.getString("due") ?: "");input(p,"notes","Notes",t?.getString("notes") ?: "",true,4000);input(p,"steps","Subtasks (one per line)",((t?.let {JournalData.list(it.getJSONArray("subtasks")).map {s->s.getString("title")}} ?: emptyList())+added).joinToString("\n"),true,12100)}, {repository.change {TaskData.save(profile(it),id,value("title"),value("notes"),value("due"),value("steps").lines())};mode="tasks"})
    }
    private fun celebrate() {
        toast("Done. A small step forward!")
        if(appearance.reduceMotion || Settings.Global.getFloat(contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f) return
        val overlay=FrameLayout(this);val decor=window.decorView as android.view.ViewGroup;decor.addView(overlay,android.view.ViewGroup.LayoutParams(-1,-1));overlay.isClickable=false
        for(i in 0..19){val piece=View(this).apply {setBackgroundColor(intArrayOf(0xffdfb44c.toInt(),0xff4faa83.toInt(),0xff818cff.toInt())[i%3]);layoutParams=FrameLayout.LayoutParams(dp(7),dp(12));translationX=(resources.displayMetrics.widthPixels*Math.random()).toFloat();translationY=dp(80).toFloat()};overlay.addView(piece);piece.animate().translationY(resources.displayMetrics.heightPixels*.8f).rotation(450f).alpha(0f).setDuration(1200).start()};overlay.postDelayed({decor.removeView(overlay)},1300)
    }
    private fun settings() {
        root("Appearance & AI")
        val languages=I18n.languages(this);val codes=languages.keys().asSequence().toList();var language=I18n.language(this)
        select(body,"Language",codes.map {languages.getString(it)},languages.getString(language)){label->language=codes.first {languages.getString(it)==label}}
        button(body,I18n.t(this,"Save language")) {I18n.save(this,language);render()}
        select(body,"Theme",appearance.themes.keys().asSequence().toList(),selectedTheme){selectedTheme=it}
        text(body,I18n.t(this,"Custom palette: use #RRGGBB colors. Palette fields apply when Custom is selected."),13f)
        for(k in listOf("background","surface","text","accent","bar")) input(body,k,k,appearance.stored(k,appearance.themes.getJSONObject("Custom").getString(k)))
        input(body,"gradient","Gradient end color (optional)",appearance.stored("gradient"));input(body,"dim","Wallpaper dimming (0–90%)",appearance.stored("dim","0"))
        val motion=CheckBox(this).apply {text=I18n.t(this@CompanionActivity,"Reduce motion");setTextColor(appearance.color("text"));isChecked=appearance.reduceMotion;body.addView(this)}
        button(body,I18n.t(this,"Save appearance")) {appearance.save(selectedTheme,listOf("background","surface","text","accent","bar").associateWith {value(it)},value("gradient"),value("dim").toIntOrNull() ?: error("Enter dimming from 0 to 90."),motion.isChecked);render();toast("Appearance saved.")}
        button(body,I18n.t(this,"Choose wallpaper")) {startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),21)}
        button(body,I18n.t(this,"Remove wallpaper")) {File(filesDir,"wallpaper.jpg").delete();render()}
        button(body,I18n.t(this,"AI provider keys")) {mode="keys";render()}
    }
    private fun keys() {
        root("AI provider keys");text(body,I18n.t(this,"Your account may incur API charges. Keys are encrypted with Android Keystore and excluded from journal backups."))
        val vault=KeyVault(this);val status=text(body,I18n.t(this,""))
        select(body,"Provider",listOf("openai","deepseek"),selectedProvider){selectedProvider=it;status.text=if(vault.has(it))"A key is configured." else "No key configured."}
        input(body,"key","New API key",max=512).apply {inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;isSaveEnabled=false;setAutofillHints(View.AUTOFILL_HINT_PASSWORD)}
        button(body,I18n.t(this,"Save key securely")) {val key=value("key");require(key.isNotEmpty()){ "Enter an API key." };vault.save(selectedProvider,key);fields["key"]?.setText("");toast("Key saved.");render()}
        button(body,I18n.t(this,"Remove selected provider key")) {vault.save(selectedProvider,"");render()}
        button(body,I18n.t(this,"Back to settings")) {mode="settings";render()}
    }
    private fun ai() {
        root(if(aiKind=="steps")"AI task breakdown" else "AI spread ideas")
        text(body,I18n.t(this,"Only the text below is sent when you press Generate. Your selected provider may charge your account. Suggestions need your review before saving."))
        select(body,"Provider",listOf("openai","deepseek"),selectedProvider){ if(it!=selectedProvider){selectedProvider=it;fields["model"]?.setText(if(it=="openai")"gpt-4.1-mini" else "deepseek-flash")} }
        input(body,"model","Model",if(selectedProvider=="openai")"gpt-4.1-mini" else "deepseek-flash",max=100)
        val initial=if(aiKind=="steps") TaskData.tasks(profile()).find {it.getString("id")==taskId}?.let {it.getString("title")+"\n"+it.getString("notes")} ?: "" else intent.getStringExtra("context") ?: "Suggest paper journal spreads. My interests: "
        input(body,"context","Text to send",initial,true,6000)
        val status=text(body,I18n.t(this,""))
        val generate=button(body,I18n.t(this,"Generate")) {}
        generate.setOnClickListener {
            if(request!=null)return@setOnClickListener
            try {
                val provider=selectedProvider;val key=KeyVault(this).read(provider);val model=value("model");val context=value("context");val client=AiClient();request=client;generate.isEnabled=false;status.text="Waiting for suggestions…"
                worker.execute {try {val result=client.generate(provider,model,aiKind,context,key,I18n.languages(this).getString(I18n.language(this)));runOnUiThread {if(!isDestroyed && request===client){request=null;generate.isEnabled=true;status.text="";suggestions=result;checkedSuggestions=BooleanArray(result.size){it==0};review()}}} catch(e:Exception){runOnUiThread {if(!isDestroyed && request===client){request=null;generate.isEnabled=true;status.text=I18n.t(this,e.message ?: "Could not complete that action") ?: "Provider request failed. Nothing was changed."}}} }
            }catch(e:Exception){status.text=I18n.t(this,e.message ?: "Could not complete that action")}
        }
        button(body,I18n.t(this,"Cancel request")) {request?.cancel();request=null;generate.isEnabled=true;status.text="Canceled. Nothing was changed."}
    }
    private fun review() {
        formKind="review"
        val builder=AlertDialog.Builder(this).setTitle(I18n.t(this@CompanionActivity,"Review suggestions")).setNegativeButton(I18n.t(this@CompanionActivity,"Cancel"),null)
        val labels=suggestions.map {it.getString("title")+"\n"+it.getString("detail")}.toTypedArray()
        if(aiKind=="steps")builder.setMultiChoiceItems(labels,checkedSuggestions){_,i,checked->checkedSuggestions[i]=checked}
        else builder.setSingleChoiceItems(labels,checkedSuggestions.indexOfFirst {it}){_,i->checkedSuggestions=BooleanArray(labels.size){it==i}}
        val d=builder.setPositiveButton(I18n.t(this@CompanionActivity,"Use selected"),null).create();dialog=d
        d.setOnDismissListener {formKind="";dialog=null};d.setOnShowListener {d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { val selected=suggestions.filterIndexed {i,_->checkedSuggestions[i]};if(selected.isEmpty()){toast("Select a suggestion.");return@setOnClickListener};d.dismiss();if(aiKind=="steps"){editing=taskId;taskForm(taskId,selected.map {it.getString("title")})}else {draft=Bundle().apply {putString("title",selected.first().getString("title"));putString("layoutNotes",selected.first().getString("detail"))};spreadForm()} }};d.show()
    }
    private fun spreadForm() {form("spread","Choose pages for this idea",{p->input(p,"title","Spread title");input(p,"layoutNotes","Layout notes",multiline=true,max=2000);input(p,"start","First page");input(p,"end","Last page")},{repository.change {JournalData.saveSpread(it,pid,jid,null,value("title"),value("start").toIntOrNull() ?: error("Enter first page."),value("end").toIntOrNull() ?: error("Enter last page."),JSONObject().put("kind","custom").put("notes",value("layoutNotes")))};toast("Spread saved.")})}
    private fun timer() {
        timerHandler.removeCallbacksAndMessages(null)
        root("🍅 Tomato focus timer")
        val task=TaskData.tasks(profile()).find {it.getString("id")==taskId}
        val model=Pomodoro(this)
        text(body,task?.getString("title") ?: "Focus timer",22f)
        text(body,I18n.t(this,"Choose 5–30 minutes. The countdown survives leaving this screen. A completion message appears while this screen is open; no background alarm is scheduled."))
        val time=text(body,I18n.t(this,""),48f)
        input(body,"minutes","Minutes","25")
        button(body,I18n.t(this,"Start focus")) {require(task!=null){"Choose a task first."};model.start(pid,taskId!!,task.getString("title"),value("minutes").toIntOrNull() ?: error("Choose 5–30 minutes."));timerAnnounced=false}
        button(body,I18n.t(this,"Pause")) {model.pause()};button(body,I18n.t(this,"Resume")) {model.resume()};button(body,I18n.t(this,"Stop timer")) {model.reset()}
        button(body,I18n.t(this,"Tasks & plans")) {mode="tasks";timerHandler.removeCallbacksAndMessages(null);render()}
        val tick=object:Runnable {override fun run(){val t=model.read();val left=if(t==null)0 else model.remaining(t);val seconds=(left+999)/1000;time.text=String.format(java.util.Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);if(t!=null && left==0L && !timerAnnounced){toast("Focus session finished. Take a gentle break.");timerAnnounced=true};timerHandler.postDelayed(this,500)}}
        timerHandler.post(tick)
    }
    private fun validLink(value:String):String {
        val uri=java.net.URI(value.trim());require(uri.scheme?.lowercase() in listOf("http","https") && !uri.host.isNullOrBlank() && uri.userInfo==null && value.length<=2000) {"Use an HTTP or HTTPS link without embedded credentials."};return uri.toASCIIString()
    }
    private fun qr() {
        root("QR links");text(body,I18n.t(this,"Create a code for a web link, or scan a code and review its destination. Scanning never opens a link automatically."))
        input(body,"url","Website link",qrValue,max=2000)
        button(body,I18n.t(this,"Generate QR code")) {qrValue=validLink(value("url"));render()}
        if(qrValue.isNotEmpty()) {val bitmap=BarcodeEncoder().encodeBitmap(qrValue,BarcodeFormat.QR_CODE,600,600);val image=ImageView(this).apply {setImageBitmap(bitmap);contentDescription="QR code for $qrValue"};body.addView(image,LinearLayout.LayoutParams(-1,dp(260)));button(body,I18n.t(this,"Save QR as PNG")) {startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/png").putExtra(Intent.EXTRA_TITLE,"Digital-Journal-QR.png"),22)}}
        button(body,I18n.t(this,"Scan QR code")) {startScan()}
    }
    private fun startScan() {
        require(packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)) { "This device has no camera. QR generation is still available." }
        if(checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA),31); return
        }
        IntentIntegrator(this).setDesiredBarcodeFormats(IntentIntegrator.QR_CODE).setPrompt(I18n.t(this,"Scan a web link")).setBeepEnabled(false).setOrientationLocked(false).initiateScan()
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code,permissions,results)
        if(code==31) {
            if(results.firstOrNull()==android.content.pm.PackageManager.PERMISSION_GRANTED) startScan()
            else toast("Camera access was denied. QR generation is still available.")
        }
    }
    private fun about() {
        root("About Digital Journal")
        text(body,I18n.t(this,"Digital Journal is an independent, fan-made application developed for personal use and offered without profit. The Bullet Journal method was created by Ryder Carroll. Bullet Journal® and BuJo® are trademarks of Lightcage, LLC. This project is not affiliated with, sponsored by, or endorsed by Ryder Carroll or Lightcage, LLC."))
        button(body,I18n.t(this,"Original method: bulletjournal.com")) {startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://bulletjournal.com")))}
        text(body,I18n.t(this,"Version 0.3.0 · Source: github.com/mikusz3/Digital-Journal. Themes are original visual interpretations; no third-party artwork is bundled. QR support uses ZXing and JourneyApps (Apache-2.0)."))
        button(body,I18n.t(this,"Open-source licenses")) { val content=TextView(this).apply {text=assets.open("notices.txt").bufferedReader().use {it.readText()};setPadding(dp(16),dp(16),dp(16),dp(16))};AlertDialog.Builder(this).setTitle(I18n.t(this@CompanionActivity,"Open-source licenses")).setView(ScrollView(this).apply {addView(content)}).setPositiveButton(I18n.t(this@CompanionActivity,"Close"),null).show() }
        text(body,I18n.t(this,"Compatibility investigations"),22f);text(body,I18n.t(this,"Cover to Cover Club and Xiaomi Home can be opened below when installed. No reading data is imported and no printer connection is claimed. Collection and printing features are planned for later."))
        for((label,pkg) in listOf("Cover to Cover Club" to "com.quillguild.covertocoverclub","Xiaomi Home" to "com.xiaomi.smarthome")) button(body,"Open $label") {val launch=packageManager.getLaunchIntentForPackage(pkg) ?: error("$label is not installed on this device.");startActivity(launch)}
    }
    @Deprecated("Platform result API used by the existing native Views application")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        val scan=IntentIntegrator.parseActivityResult(requestCode,resultCode,data)
        if(scan!=null){if(scan.contents!=null)try {val link=validLink(scan.contents);AlertDialog.Builder(this).setTitle(I18n.t(this@CompanionActivity,"Open this link?")).setMessage(link).setNegativeButton(I18n.t(this@CompanionActivity,"Cancel"),null).setPositiveButton(I18n.t(this@CompanionActivity,"Open in browser")){_,_->try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(link)))}catch(e:Exception){problem(e)}}.show()}catch(e:Exception){problem(e)};return}
        if(resultCode!=RESULT_OK)return
        val uri=data?.data ?: return
        try {when(requestCode){
            21->{val bytes=contentResolver.openInputStream(uri)!!.use {stream->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=stream.read(buffer);if(n<0)break;require(out.size()+n<=5*1024*1024){"Choose an image smaller than 5 MB."};out.write(buffer,0,n)};out.toByteArray()};val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds);require(bounds.outWidth>0 && bounds.outHeight>0){"Choose a supported image."};val opts=BitmapFactory.Options().apply {inSampleSize=maxOf(1,maxOf(bounds.outWidth,bounds.outHeight)/1600)};val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,opts) ?: error("Could not decode image.");val file=File(filesDir,"wallpaper.jpg");val tmp=File(filesDir,"wallpaper.tmp");tmp.outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,85,it)};check(tmp.renameTo(file)){"Could not save wallpaper."};bitmap.recycle();render()}
            22->{val bitmap=BarcodeEncoder().encodeBitmap(validLink(qrValue),BarcodeFormat.QR_CODE,800,800);contentResolver.openOutputStream(uri,"wt")!!.use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)){"Could not save QR."}};bitmap.recycle();toast("QR image saved.")}
        }}catch(e:Exception){problem(e)}
    }
}
