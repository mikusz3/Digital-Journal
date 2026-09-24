package pl.digitalbujo.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject

class JournalWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { updateAll(context) }
    override fun onDeleted(context: Context, ids: IntArray) {
        prefs(context).edit().apply { ids.forEach { remove(it.toString()) } }.apply()
    }
    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) {
        val p=prefs(context);val editor=p.edit()
        oldIds.zip(newIds).forEach { (old,new) -> p.getString(old.toString(),null)?.let { editor.putString(new.toString(),it) };editor.remove(old.toString()) }
        editor.apply();updateAll(context)
    }
    companion object {
        fun prefs(context: Context)=context.getSharedPreferences("journal_widgets",Context.MODE_PRIVATE)
        fun updateAll(context: Context, state: JSONObject? = null) {
            val manager=AppWidgetManager.getInstance(context)
            val ids=manager.getAppWidgetIds(ComponentName(context,JournalWidget::class.java))
            if(ids.isEmpty())return
            val data=state ?: runCatching { JournalRepository(context).read() }.getOrNull()
            ids.forEach { update(context,manager,it,data) }
        }
        fun update(context: Context, manager: AppWidgetManager, id: Int, state: JSONObject?) {
            val profileId=prefs(context).getString(id.toString(),null)
            val shelf=state?.let { WidgetContent.shelf(it,profileId) }
            val views=RemoteViews(context.packageName,R.layout.journal_widget)
            views.setTextViewText(R.id.widget_title,shelf?.name ?: I18n.t(context,"Journal shortcuts"))
            views.setTextViewText(R.id.widget_empty,I18n.t(context,if(shelf==null) "Choose a profile for this widget." else "No journals yet."))
            views.setViewVisibility(R.id.widget_empty,if(shelf?.journals.isNullOrEmpty()) View.VISIBLE else View.GONE)
            fun open(slot: Int, journal: String?): PendingIntent {
                val intent=Intent(context,MainActivity::class.java).setData(Uri.parse("digitaljournal://widget/$id/$slot"))
                    .putExtra("widget_profile",profileId).putExtra("widget_journal",journal)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                return PendingIntent.getActivity(context,id*10+slot,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
            listOf(R.id.widget_journal_1,R.id.widget_journal_2,R.id.widget_journal_3).forEachIndexed { index, viewId ->
                val journal=shelf?.journals?.getOrNull(index)
                views.setViewVisibility(viewId,if(journal==null)View.GONE else View.VISIBLE)
                views.setTextViewText(viewId,journal?.second ?: "")
                if(journal!=null)views.setOnClickPendingIntent(viewId,open(index,journal.first))
            }
            views.setTextViewText(R.id.widget_open,I18n.t(context,"Open app"));views.setOnClickPendingIntent(R.id.widget_open,open(3,null))
            val config=Intent(context,WidgetConfigureActivity::class.java).setData(Uri.parse("digitaljournal://widget/$id/config"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
            views.setTextViewText(R.id.widget_configure,I18n.t(context,"Choose profile"))
            views.setOnClickPendingIntent(R.id.widget_configure,PendingIntent.getActivity(context,id,config,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(id,views)
        }
    }
}
