package pl.digitalbujo.app
import android.content.Context
import org.json.JSONObject
object I18n {
    private var catalog:JSONObject?=null
    private fun data(context:Context):JSONObject=catalog ?: JSONObject(context.assets.open("locales.json").bufferedReader().use {it.readText()}).also {catalog=it}
    fun language(context:Context)=context.getSharedPreferences("language",Context.MODE_PRIVATE).getString("code","en")!!
    fun languages(context:Context)=data(context).getJSONObject("languages")
    fun save(context:Context,code:String){require(languages(context).has(code));context.getSharedPreferences("language",Context.MODE_PRIVATE).edit().putString("code",code).apply();runCatching { JournalWidget.updateAll(context) }}
    fun t(context:Context,source:String):String {
        val messages=data(context).getJSONObject("messages").getJSONObject(language(context))
        if(messages.has(source))return messages.getString(source)
        for(prefix in listOf("Complete ","Reopen ","Edit ","Open ","Delete profile ")) if(source.startsWith(prefix) && messages.has(prefix+"{name}"))return messages.getString(prefix+"{name}").replace("{name}",source.removePrefix(prefix))
        return source
    }
}
