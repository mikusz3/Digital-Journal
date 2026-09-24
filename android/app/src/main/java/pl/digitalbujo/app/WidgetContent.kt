package pl.digitalbujo.app

import org.json.JSONObject

object WidgetContent {
    data class Shelf(val name: String, val journals: List<Pair<String,String>>)
    fun shelf(state: JSONObject, profileId: String?): Shelf? {
        val profile = JournalData.profiles(state).find { it.getString("id") == profileId } ?: return null
        return Shelf(profile.getString("name"), JournalData.journals(profile).take(3).map { it.getString("id") to it.getString("title") })
    }
    fun target(state: JSONObject, profileId: String?, journalId: String?): Pair<String,String?>? {
        val p = JournalData.profiles(state).find { it.getString("id") == profileId } ?: return null
        val j = journalId?.takeIf { id -> JournalData.journals(p).any { it.getString("id") == id } }
        return p.getString("id") to j
    }
}
