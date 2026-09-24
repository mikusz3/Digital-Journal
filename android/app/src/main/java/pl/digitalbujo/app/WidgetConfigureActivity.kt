package pl.digitalbujo.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class WidgetConfigureActivity : Activity() {
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setResult(RESULT_CANCELED)
        val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)
        val manager=AppWidgetManager.getInstance(this)
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID || manager.getAppWidgetInfo(id)?.provider!=ComponentName(this,JournalWidget::class.java)){finish();return}
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24);setBackgroundColor(0xfff7f6f1.toInt()) }
        val scroll=ScrollView(this).apply {addView(body)};setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { v,i ->
            if(android.os.Build.VERSION.SDK_INT>=30){val bars=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom)}
            else { @Suppress("DEPRECATION") v.setPadding(i.systemWindowInsetLeft,i.systemWindowInsetTop,i.systemWindowInsetRight,i.systemWindowInsetBottom) };i
        }
        scroll.requestApplyInsets()
        fun text(value:String){body.addView(TextView(this).apply{text=I18n.t(this@WidgetConfigureActivity,value);textSize=18f;setTextColor(0xff253b32.toInt());setPadding(8,12,8,12)})}
        text("Journal shortcuts");text("Choose a profile. The widget shows its first three journals.")
        val data=runCatching { JournalRepository(this).read() }.getOrElse { text("Could not load journals. Open the app to check your data.");return }
        val profiles=JournalData.profiles(data)
        if(profiles.isEmpty())text("Create a profile in the app first.")
        profiles.forEach { p -> body.addView(Button(this).apply {
            text=p.getString("name");isAllCaps=false
            setOnClickListener {
                JournalWidget.prefs(this@WidgetConfigureActivity).edit().putString(id.toString(),p.getString("id")).apply()
                JournalWidget.update(this@WidgetConfigureActivity,manager,id,data)
                setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id));finish()
            }
        }) }
        body.addView(Button(this).apply {text=I18n.t(this@WidgetConfigureActivity,"Open app");setOnClickListener{startActivity(Intent(this@WidgetConfigureActivity,MainActivity::class.java));finish()} })
        body.addView(Button(this).apply {text=I18n.t(this@WidgetConfigureActivity,"Cancel");setOnClickListener{finish()} })
    }
}
