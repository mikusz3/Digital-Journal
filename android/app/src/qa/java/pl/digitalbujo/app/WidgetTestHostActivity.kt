package pl.digitalbujo.app

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView

/** Isolated QA-only real RemoteViews host; never included in the release APK. */
class WidgetTestHostActivity : Activity() {
    private lateinit var host: AppWidgetHost
    private var widgetId=AppWidgetManager.INVALID_APPWIDGET_ID
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        host=AppWidgetHost(this,5041)
        val prefs=getSharedPreferences("widget_test",MODE_PRIVATE)
        widgetId=prefs.getInt("id",AppWidgetManager.INVALID_APPWIDGET_ID)
        val manager=AppWidgetManager.getInstance(this)
        if(widgetId!=AppWidgetManager.INVALID_APPWIDGET_ID && manager.getAppWidgetInfo(widgetId)!=null){showWidget();return}
        widgetId=host.allocateAppWidgetId();prefs.edit().putInt("id",widgetId).apply()
        val provider=ComponentName(this,JournalWidget::class.java)
        if(manager.bindAppWidgetIdIfAllowed(widgetId,provider))configure()
        else startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,provider),1)
    }
    private fun configure(){startActivityForResult(Intent(this,WidgetConfigureActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId),2)}
    private fun showWidget(){
        val manager=AppWidgetManager.getInstance(this);val info=manager.getAppWidgetInfo(widgetId) ?: return
        val box=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(20,100,20,50)}
        box.addView(TextView(this).apply{text="Widget QA host";textSize=20f})
        val view=host.createView(this,widgetId,info)
        box.addView(view,LinearLayout.LayoutParams(-1,(310*resources.displayMetrics.density).toInt()))
        setContentView(box);JournalWidget.updateAll(this)
    }
    override fun onStart(){super.onStart();if(::host.isInitialized)host.startListening()}
    override fun onStop(){if(::host.isInitialized)host.stopListening();super.onStop()}
    @Deprecated("QA host result API")
    override fun onActivityResult(request:Int,result:Int,data:Intent?){super.onActivityResult(request,result,data);if(result!=RESULT_OK){host.deleteAppWidgetId(widgetId);finish();return};if(request==1)configure() else showWidget()}
}
