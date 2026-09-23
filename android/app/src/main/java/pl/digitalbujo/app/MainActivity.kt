package pl.digitalbujo.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : Activity() {
    private lateinit var repository: JournalRepository
    private lateinit var content: LinearLayout
    private var profileId: String? = null
    private var journalId: String? = null
    private var query = ""
    private var currentDialog: AlertDialog? = null
    private var dialogKind = ""
    private var editingId: String? = null
    private val fields = linkedMapOf<String, View>()
    private val appearance by lazy { Appearance(this) }
    private val green get() = appearance.color("accent")
    private val ink get() = appearance.color("text")
    private val paper get() = appearance.color("background")
    private val muted get() = ink
    private fun tools(mode: String) { startActivity(Intent(this, CompanionActivity::class.java).putExtra("mode", mode).putExtra("profile",profileId).putExtra("journal",journalId)) }
    override fun onResume() { super.onResume(); if (::repository.isInitialized && currentDialog == null) { try { repository=JournalRepository(this);render() } catch(e:Exception) { problem(e) } } }
    private val pendingFile by lazy { File(cacheDir, "import-preview.json") }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun state() = repository.read()
    private fun profile() = profileId?.let { JournalData.profile(state(), it) }
    private fun journal() = journalId?.let { JournalData.journal(state(), profileId!!, it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { repository = JournalRepository(this) }
        catch (error: Exception) {
            setContentView(TextView(this).apply {
                text = getString(R.string.load_error, error.message)
                setPadding(dp(24), dp(60), dp(24), dp(24)); textSize = 18f
            })
            return
        }
        val profiles = JournalData.profiles(state())
        if (savedInstanceState != null) {
            profileId = savedInstanceState.getString("profile")
            journalId = savedInstanceState.getString("journal")
            query = savedInstanceState.getString("query", "")
        } else if (profiles.size == 1) profileId = profiles.first().getString("id")
        if (Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(0) { goBack() }
        render()
        savedInstanceState?.let { saved ->
            val id = saved.getString("editing")
            when (saved.getString("dialog")) {
                "profile" -> profileForm()
                "journal" -> journalForm(id)
                "spread" -> spreadForm(id)
                "delete" -> if (id != null) deleteProfileForm(id)
                "backups" -> backups()
                "import" -> if (pendingFile.exists()) previewImport()
            }
            saved.getBundle("fields")?.let { values -> fields.forEach { (name, view) ->
                when (view) {
                    is EditText -> view.setText(values.getString(name, ""))
                    is Spinner -> view.setSelection(values.getInt(name, 0))
                }
            } }
        }
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("profile", profileId); out.putString("journal", journalId); out.putString("query", query)
        out.putString("dialog", dialogKind); out.putString("editing", editingId)
        out.putBundle("fields", Bundle().apply { fields.forEach { (name, view) -> when (view) {
            is EditText -> putString(name, view.text.toString())
            is Spinner -> putInt(name, view.selectedItemPosition)
        } } })
        super.onSaveInstanceState(out)
    }
    @Deprecated("Uses platform predictive back on API 33 and newer")
    @android.annotation.SuppressLint("GestureBackNavigation") // Only legacy devices use this; API 33+ registers OnBackInvokedCallback above.
    override fun onBackPressed() { goBack() }
    private fun goBack() {
        when {
            currentDialog?.isShowing == true -> currentDialog?.dismiss()
            journalId != null -> { journalId = null; query = ""; render() }
            profileId != null -> { profileId = null; render() }
            else -> finish()
        }
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun text(parent: LinearLayout, value: String, size: Float = 15f, color: Int = ink): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setPadding(0, dp(5), 0, dp(9))
        if (size >= 23) setTypeface(typeface, Typeface.BOLD)
        parent.addView(this)
    }
    private fun button(parent: LinearLayout, label: String, primary: Boolean = false, action: () -> Unit): Button = Button(this).apply {
        text = I18n.t(this@MainActivity,label); isAllCaps = false; minHeight = dp(48)
        setTextColor(if (primary) paper else ink)
        background = GradientDrawable().apply { setColor(if (primary) green else appearance.color("bar")); cornerRadius = dp(10).toFloat() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(5), 0, dp(9)) }
        setPadding(dp(14), dp(10), dp(14), dp(10)); setOnClickListener { action() }
        parent.addView(this)
    }
    private fun card(parent: LinearLayout): LinearLayout = column().apply {
        setPadding(dp(17), dp(13), dp(17), dp(13))
        background = GradientDrawable().apply { setColor(appearance.color("surface")); cornerRadius = dp(15).toFloat(); setStroke(dp(1), Color.rgb(220, 226, 215)) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(5), 0, dp(12)) }
        parent.addView(this)
    }
    private fun render() {
        val outer = column().apply { appearance.background(this) }
        outer.setPadding(dp(20), dp(12), dp(20), dp(10))
        outer.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(dp(20) + bars.left, dp(12) + bars.top, dp(20) + bars.right, dp(10) + bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(dp(20) + insets.systemWindowInsetLeft, dp(12) + insets.systemWindowInsetTop, dp(20) + insets.systemWindowInsetRight, dp(10) + insets.systemWindowInsetBottom)
            }
            insets
        }
        text(outer, I18n.t(this,"Digital Journal"), 24f, green)
        val toolbar=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; outer.addView(this) }
        for ((label,mode) in listOf("Settings" to "settings","QR" to "qr","About" to "about")) button(toolbar,label) { tools(mode) }.layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = column()
        scroll.addView(content); outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(outer); outer.requestApplyInsets()
        if (profileId == null) {
            text(content, I18n.t(this,"Your journal. Your own pace."), 27f)
            text(content, I18n.t(this,"A quiet companion to your paper pages. No streaks to keep, no catching up required."), color = muted)
            JournalData.profiles(state()).forEach { p ->
                val tile = card(content)
                button(tile, "${p.getString("name")} · ${JournalData.journals(p).size} journals") {
                    profileId = p.getString("id"); journalId = null; render()
                }
                button(tile, I18n.t(this,"Delete profile")) { deleteProfileForm(p.getString("id")) }.apply {
                    setTextColor(Color.rgb(151, 54, 41)); contentDescription = "Delete profile ${p.getString("name")}"
                }
            }
            button(content, I18n.t(this,"Create a profile"), true) { profileForm() }
            button(content, I18n.t(this,"Backups")) { backups() }
            text(content, I18n.t(this,"Profiles and journals stay on this device. No email or password needed."), 13f, muted)
            return
        }
        button(content,I18n.t(this,"Tasks & plans")) { tools("tasks") }
        if (journalId == null) {
            text(content, "${profile()!!.getString("name")} · ${I18n.t(this,"Your journal shelf")}", 26f)
            button(content, I18n.t(this,"Switch or add profile")) { profileId = null; render() }
            val journals = JournalData.journals(profile()!!)
            if (journals.isEmpty()) text(content, I18n.t(this,"Start with the notebook beside you. Add its page index whenever you feel like it."), color = muted)
            journals.forEach { j -> val tile = card(content)
                text(tile, j.getString("title"), 23f)
                text(tile, "${j.getString("format")} · ${j.getInt("pages")} pages · ${JournalData.spreads(j).size} spreads", 13f, muted)
                button(tile, "Open ${j.getString("title")}") { journalId = j.getString("id"); query = ""; render() }
            }
            button(content, I18n.t(this,"Add journal"), true) { journalForm() }
            button(content, I18n.t(this,"Backups")) { backups() }
            return
        }
        val j = journal()!!
        button(content, I18n.t(this,"Back to journals")) { journalId = null; query = ""; render() }
        text(content, j.getString("title"), 27f)
        val size = if (j.getString("format") == "Custom") j.getString("formatDetail") else j.getString("format")
        text(content, "$size · ${j.getInt("pages")} pages · saved on this device", 13f, muted)
        button(content, I18n.t(this,"Edit journal")) { journalForm(j.getString("id")) }
        button(content, I18n.t(this,"Add spread"), true) { spreadForm() }
        button(content, I18n.t(this,"AI spread ideas")) { tools("ai") }
        val search = EditText(this).apply { setTextColor(ink); setHintTextColor(muted); hint = "Find a spread"; contentDescription = "Find a spread"; setSingleLine(); setText(query) }
        content.addView(search)
        val list = column(); content.addView(list)
        fun updateList() {
            list.removeAllViews()
            val spreads = JournalData.spreads(j).filter { (it.getString("title") + " " + it.getInt("start") + " " + it.getInt("end")).contains(query.trim(), true) }
            if (spreads.isEmpty()) text(list, if (query.isEmpty()) "Your index starts here. Give a page a name." else "No matching spreads.", color = muted)
            for (s in spreads) {
                val tile = card(list)
                text(tile, "Pages ${s.getInt("start")}–${s.getInt("end")}", 12f, muted)
                text(tile, s.getString("title"), 19f)
                button(tile,I18n.t(this,"View spread template")) { SpreadLayouts.show(this,s) }
                button(tile, "Edit ${s.getString("title")}") { spreadForm(s.getString("id")) }
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString(); updateList() }
            override fun afterTextChanged(s: Editable?) {}
        })
        updateList()
    }
    private fun form(kind: String, title: String, id: String? = null, build: (LinearLayout) -> Unit, save: () -> Unit) {
        currentDialog?.dismiss(); fields.clear(); dialogKind = kind; editingId = id
        val body = column().apply { setBackgroundColor(appearance.color("surface")); setPadding(dp(24), dp(8), dp(24), dp(12)) }
        build(body)
        val error = text(body, I18n.t(this,""), 13f, Color.rgb(151, 54, 41))
        val scroll = ScrollView(this).apply { addView(body) }
        val dialog = AlertDialog.Builder(this).setTitle(I18n.t(this,title)).setView(scroll).setNegativeButton(I18n.t(this@MainActivity,"Cancel"), null).setPositiveButton(if (kind == "delete") "Delete profile" else I18n.t(this,"Save"), null).create()
        currentDialog = dialog
        dialog.setOnDismissListener { dialogKind = ""; editingId = null; fields.clear(); currentDialog = null }
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try { save(); dialog.dismiss(); render(); toast(if (kind == "delete") "Profile deleted from this device." else "Saved on this device.") }
            catch (e: Exception) { error.text = I18n.t(this,e.message ?: "Please try again.") ?: "Could not save. Please try again." }
        } }
        dialog.show()
    }
    private fun input(parent: LinearLayout, key: String, label: String, value: String = "", numeric: Boolean = false, max: Int = 120): EditText {
        val labelView = text(parent, I18n.t(this,label), 13f)
        return EditText(this).apply {
            id = View.generateViewId(); labelView.labelFor = id
            contentDescription = I18n.t(this@MainActivity,label); setTextColor(ink); setSingleLine(); setText(value)
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(max)); parent.addView(this); fields[key] = this
        }
    }
    private fun select(parent: LinearLayout, key: String, label: String, options: List<String>, selected: Int = 0): Spinner {
        val labelView = text(parent, I18n.t(this,label), 13f)
        return Spinner(this).apply {
            id = View.generateViewId(); labelView.labelFor = id; contentDescription = label
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, options.map {I18n.t(this@MainActivity,it)})
            setSelection(selected); minimumHeight = dp(48); parent.addView(this); fields[key] = this
        }
    }
    private fun value(key: String) = (fields[key] as EditText).text.toString()
    private fun selected(key: String) = (fields[key] as Spinner).selectedItemPosition
    private fun integer(key: String) = value(key).toIntOrNull() ?: error("Enter a whole number for $key.")
    private fun profileForm() = form("profile", "A space of your own", build = { body ->
        text(body, I18n.t(this,"Only stored here. Use your name or a name you prefer."), 13f, muted)
        input(body, "name", "Full name", max = 80)
    }, save = {
        var newId: String? = null
        repository.change { newId = JournalData.addProfile(it, value("name")) }
        profileId = newId; journalId = null
    })
    private fun deleteProfileForm(id: String) {
        val p = JournalData.profile(state(), id)
        form("delete", "Delete this profile?", id, build = { body ->
            val journals = JournalData.journals(p)
            val spreads = journals.sumOf { JournalData.spreads(it).size }
            text(body, "Permanently remove ${p.getString("name")} and its ${journals.size} journals ($spreads spreads) from this device? Export first if you want to keep them. Existing backup files are not deleted.", 14f)
            input(body, "confirmation", "Type profile name to confirm", max = 80)
            text(body, p.getString("name"), 16f)
        }, save = {
            repository.change { JournalData.deleteProfile(it, id, value("confirmation")) }
            if(Pomodoro(this).read()?.optString("profileId")==id) Pomodoro(this).reset()
            profileId = null; journalId = null; query = ""
        })
    }
    private fun journalForm(id: String? = null) {
        val existing = id?.let { JournalData.journal(state(), profileId!!, it) }
        form("journal", if (id == null) "Add paper journal" else "Edit paper journal", id, build = { body ->
            input(body, "title", "Journal title", existing?.getString("title") ?: "")
            input(body, "pages", "Page count", (existing?.getInt("pages") ?: 200).toString(), true, 5)
            select(body, "format", "Notebook size", JournalData.formats, JournalData.formats.indexOf(existing?.getString("format") ?: "A5"))
            input(body, "detail", "Custom size (only for Custom)", existing?.optString("formatDetail") ?: "", max = 60)
            if (id == null) select(body, "template", "Starting index", listOf("Blank", "2026/2027/2028 starter index", "DLC starter index"))
            text(body, I18n.t(this,"Starter indexes need 200 pages. Unnamed pages stay open."), 12f, muted)
        }, save = {
            var newId: String? = null
            val template = if (id == null && selected("template") > 0) {
                val templates = JSONArray(assets.open("templates.json").bufferedReader().use { it.readText() })
                templates.getJSONObject(selected("template") - 1)
            } else null
            repository.change { newId = JournalData.saveJournal(it, profileId!!, id, value("title"), integer("pages"), JournalData.formats[selected("format")], value("detail"), template) }
            journalId = newId; query = ""
        })
    }
    private fun spreadForm(id: String? = null) {
        val existing = id?.let { key -> JournalData.spreads(journal()!!).find { it.getString("id") == key } }
        form("spread", if (id == null) "Add spread" else "Edit spread", id, build = { body ->
            select(body,"layout","Template",listOf("Blank","Calendar with notes","Tracker","Log","Wishlist","AI-assisted / custom"),listOf("blank","calendar","tracker","log","wishlist","custom").indexOf(existing?.optJSONObject("layout")?.optString("kind") ?: "blank"))
            input(body,"layoutNotes","Layout notes",existing?.optJSONObject("layout")?.optString("notes") ?: "",max=2000)
            button(body,I18n.t(this,"Let AI help with this spread/page")) { val context="Help design a ${listOf("blank","calendar","tracker","log","wishlist","custom")[selected("layout")]} spread: ${value("title")}. ${value("layoutNotes")}";currentDialog?.dismiss();startActivity(Intent(this,CompanionActivity::class.java).putExtra("mode","ai").putExtra("profile",profileId).putExtra("journal",journalId).putExtra("context",context)) }
            input(body, "title", "Spread title", existing?.getString("title") ?: "")
            input(body, "start", "First page", existing?.getInt("start")?.toString() ?: "", true, 5)
            input(body, "end", "Last page", existing?.getInt("end")?.toString() ?: "", true, 5)
            text(body, "For a single page use the same number twice. This journal has ${journal()!!.getInt("pages")} pages.", 13f, muted)
        }, save = { repository.change { JournalData.saveSpread(it, profileId!!, journalId!!, id, value("title"), integer("start"), integer("end"),JSONObject().put("kind",listOf("blank","calendar","tracker","log","wishlist","custom")[selected("layout")]).put("notes",value("layoutNotes"))) } })
    }
    private fun toast(message: String) = Toast.makeText(this, I18n.t(this,message), Toast.LENGTH_LONG).show()
    private fun problem(error: Exception) = AlertDialog.Builder(this).setTitle(I18n.t(this@MainActivity,"Could not complete that action")).setMessage(error.message ?: "Please try again.").setPositiveButton(I18n.t(this@MainActivity,"OK"), null).show()
    private fun backups() {
        currentDialog?.dismiss(); dialogKind = "backups"
        val dialog = AlertDialog.Builder(this).setTitle(I18n.t(this@MainActivity,"Journal backups"))
            .setMessage(I18n.t(this@MainActivity,"Export all profiles, or import separate copies. Existing journals are never replaced. Backups contain names and journal content in plain text."))
            .setNegativeButton(I18n.t(this@MainActivity,"Close"), null)
            .setNeutralButton(I18n.t(this@MainActivity,"Export")) { _, _ ->
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE, "Digital-Journal-${java.time.LocalDate.now()}.json"), 10)
            }
            .setPositiveButton(I18n.t(this@MainActivity,"Import")) { _, _ ->
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 11)
            }.create()
        currentDialog = dialog
        dialog.setOnDismissListener { dialogKind = ""; currentDialog = null }
        dialog.show()
    }
    private fun previewImport() {
        try {
            val imported = JournalData.parseBackup(pendingFile.readText())
            val summary = JournalData.profiles(imported).joinToString("\n") { "${it.getString("name")} · ${JournalData.journals(it).size} journals" }
            currentDialog?.dismiss(); dialogKind = "import"
            val dialog = AlertDialog.Builder(this).setTitle(I18n.t(this@MainActivity,"Import as new profiles?"))
                .setMessage("$summary\n\nMatching names receive an import suffix. This adds copies; it is not synchronization.")
                .setNegativeButton(I18n.t(this@MainActivity,"Cancel")) { _, _ -> pendingFile.delete() }
                .setPositiveButton(I18n.t(this@MainActivity,"Import"), null).create()
            currentDialog = dialog
            dialog.setOnDismissListener { dialogKind = ""; currentDialog = null }
            dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    repository.change { JournalData.importCopies(it, imported) }
                    pendingFile.delete(); dialog.dismiss(); profileId = null; journalId = null; render(); toast("Backup imported as separate profiles.")
                } catch (e: Exception) { problem(e) }
            } }
            dialog.show()
        } catch (e: Exception) { pendingFile.delete(); problem(e) }
    }
    @Deprecated("Platform result API retained to avoid an extra activity dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            when (requestCode) {
                10 -> {
                    val bytes = JournalData.backup(state()).toByteArray(Charsets.UTF_8)
                    val output = contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open this backup destination.")
                    output.use { it.write(bytes); it.flush() }
                    toast("Backup exported.")
                }
                11 -> {
                    val input = contentResolver.openInputStream(uri) ?: error("Cannot open this backup.")
                    val bytes = input.use { stream ->
                        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) {
                            val count = stream.read(buffer); if (count < 0) break
                            require(out.size() + count <= JournalData.MAX_BACKUP_BYTES) { "Backup exceeds the 10 MB limit." }
                            out.write(buffer, 0, count)
                        }; out.toByteArray()
                    }
                    JournalData.parseBackup(bytes.toString(Charsets.UTF_8))
                    pendingFile.writeBytes(bytes); previewImport()
                }
            }
        } catch (e: Exception) { problem(e) }
    }
}
