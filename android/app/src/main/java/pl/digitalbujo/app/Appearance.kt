package pl.digitalbujo.app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.*
import android.view.View
import org.json.JSONObject
import java.io.File

class Appearance(private val context: Context) {
    private val prefs = context.getSharedPreferences("appearance",Context.MODE_PRIVATE)
    val themes = JSONObject(context.assets.open("themes.json").bufferedReader().use { it.readText() })
    val name get() = prefs.getString("theme","Light")!!
    fun value(key: String) = if (name == "Custom") prefs.getString(key,themes.getJSONObject("Custom").getString(key))!! else themes.getJSONObject(name).getString(key)
    fun color(key: String) = Color.parseColor(value(key))
    fun stored(key: String, fallback: String = "") = prefs.getString(key,fallback)!!
    val reduceMotion get() = prefs.getBoolean("reduceMotion",false)
    fun save(theme: String, custom: Map<String,String>, gradient: String, dim: Int, motion: Boolean) {
        require(themes.has(theme) && dim in 0..90) { "Choose a theme and dimming from 0 to 90." }
        for (v in custom.values + listOf(gradient).filter { it.isNotEmpty() }) require(Regex("#[0-9a-fA-F]{6}").matches(v)) { "Colors need six hexadecimal digits, e.g. #123456." }
        prefs.edit().apply { putString("theme",theme); custom.forEach { (k,v) -> putString(k,v) }; putString("gradient",gradient); putString("dim",dim.toString()); putBoolean("reduceMotion",motion) }.apply()
    }
    fun adapter(options: List<String>) = object : android.widget.ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, options.map { I18n.t(context,it) }) {
        private fun colors(view: View): View { (view as? android.widget.TextView)?.setTextColor(color("text")); view.setBackgroundColor(color("surface")); return view }
        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View = colors(super.getView(position,convertView,parent))
        override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View = colors(super.getDropDownView(position,convertView,parent))
    }
    fun background(view: View) {
        val file = File(context.filesDir,"wallpaper.jpg")
        val image = if (file.exists()) Drawable.createFromPath(file.path) else null
        val gradient = stored("gradient")
        view.background = if (image != null) LayerDrawable(arrayOf(image,ColorDrawable(Color.argb(((stored("dim","0").toIntOrNull() ?: 0)*2.55).toInt(),0,0,0))))
        else if (gradient.isNotEmpty()) GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(color("background"),Color.parseColor(gradient))) else ColorDrawable(color("background"))
    }
}
