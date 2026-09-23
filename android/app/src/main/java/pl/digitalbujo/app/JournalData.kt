package pl.digitalbujo.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

object JournalData {
    const val MAX_BACKUP_BYTES = 10 * 1024 * 1024
    val formats = listOf("A6", "B6", "A5", "B5", "Custom")
    fun fresh() = JSONObject().put("version", 2).put("profiles", JSONArray())
    fun id() = UUID.randomUUID().toString()
    fun list(array: JSONArray): List<JSONObject> = (0 until array.length()).map { array.getJSONObject(it) }
    fun profiles(state: JSONObject) = list(state.getJSONArray("profiles"))
    fun journals(profile: JSONObject) = list(profile.getJSONArray("journals"))
    fun spreads(journal: JSONObject) = list(journal.getJSONArray("spreads")).sortedBy { it.getInt("start") }
    fun label(value: String, field: String, max: Int = 120): String {
        require(value.trim().isNotEmpty()) { "$field is required." }
        require(value.trim().length <= max) { "$field must be $max characters or fewer." }
        return value.trim()
    }
    private fun number(obj: JSONObject, key: String, min: Int, max: Int): Int {
        val value = obj.get(key)
        require(value is Number && value.toDouble().isFinite() && value.toDouble() == value.toInt().toDouble() && value.toInt() in min..max) { "$key must be a whole number from $min to $max." }
        return value.toInt()
    }
    private fun text(obj: JSONObject, key: String, max: Int): String {
        require(obj.get(key) is String) { "$key must be text." }
        return label(obj.getString(key), key, max)
    }
    fun validate(state: JSONObject): JSONObject {
        require(number(state, "version", 1, 2) in 1..2) { "Unsupported data version." }
        val ids = mutableSetOf<String>()
        fun checkId(obj: JSONObject) { val id = text(obj, "id", 200); require(ids.add(id)) { "Duplicate journal identifiers." } }
        for (p in profiles(state)) {
            checkId(p); text(p, "name", 80)
            if (!p.has("tasks")) p.put("tasks", JSONArray())
            TaskData.validate(TaskData.tasks(p), ::checkId)
            for (j in journals(p)) {
                checkId(j); text(j, "title", 120)
                val pages = number(j, "pages", 1, 10000)
                require(j.getString("format") in formats) { "Unsupported notebook size." }
                if (j.getString("format") == "Custom") text(j, "formatDetail", 60)
                var last = 0
                for (s in spreads(j)) {
                    checkId(s); text(s, "title", 120)
                    val start = number(s, "start", 1, pages)
                    val end = number(s, "end", start, pages)
                    require(start > last) { "Spread page ranges overlap." }
                    last = end
                }
            }
        }
        state.put("version", 2)
        return state
    }
    fun profile(state: JSONObject, id: String) = profiles(state).find { it.getString("id") == id } ?: error("Profile not found.")
    fun journal(state: JSONObject, profileId: String, id: String) = journals(profile(state, profileId)).find { it.getString("id") == id } ?: error("Journal does not belong to this profile.")
    fun addProfile(state: JSONObject, name: String): String {
        val cleaned = label(name, "Name", 80)
        require(profiles(state).none { it.getString("name").equals(cleaned, true) }) { "A profile with that name already exists." }
        val id = id()
        state.getJSONArray("profiles").put(JSONObject().put("id", id).put("name", cleaned).put("journals", JSONArray()))
        return id
    }
    fun deleteProfile(state: JSONObject, profileId: String, confirmation: String) {
        val selected = profile(state, profileId)
        require(confirmation == selected.getString("name")) { "Type the profile name exactly to confirm deletion." }
        state.put("profiles", JSONArray(profiles(state).filter { it.getString("id") != profileId }))
    }
    fun saveJournal(state: JSONObject, profileId: String, journalId: String?, title: String, pages: Int, format: String, detail: String, template: JSONObject? = null): String {
        require(pages in 1..10000) { "Page count must be from 1 to 10000." }
        require(format in formats) { "Choose a supported notebook size." }
        val cleaned = label(title, "Journal title")
        val custom = if (format == "Custom") label(detail, "Custom size", 60) else ""
        val j = if (journalId != null) journal(state, profileId, journalId) else JSONObject().put("id", id()).put("spreads", JSONArray())
        require(spreads(j).none { it.getInt("end") > pages }) { "Page count cannot exclude an existing spread." }
        if (journalId == null && template != null) {
            require(pages >= 200) { "Starter indexes need at least 200 pages." }
            for (s in list(template.getJSONArray("spreads"))) j.getJSONArray("spreads").put(JSONObject(s.toString()).put("id", id()))
        }
        j.put("title", cleaned).put("pages", pages).put("format", format).put("formatDetail", custom)
        if (journalId == null) profile(state, profileId).getJSONArray("journals").put(j)
        return j.getString("id")
    }
    fun saveSpread(state: JSONObject, profileId: String, journalId: String, spreadId: String?, title: String, start: Int, end: Int): String {
        val j = journal(state, profileId, journalId)
        val cleaned = label(title, "Spread title")
        require(start >= 1 && end >= start && end <= j.getInt("pages")) { "Use a page range within this journal, with last page after or equal to first." }
        val all = spreads(j)
        val existing = if (spreadId == null) null else all.find { it.getString("id") == spreadId } ?: error("Spread not found in this journal.")
        require(all.none { it.getString("id") != spreadId && start <= it.getInt("end") && end >= it.getInt("start") }) { "These pages already belong to another spread." }
        val s = existing ?: JSONObject().put("id", id())
        s.put("title", cleaned).put("start", start).put("end", end)
        if (existing == null) j.getJSONArray("spreads").put(s)
        return s.getString("id")
    }
    fun backup(state: JSONObject): String {
        val text = JSONObject().put("format", "digital-journal").put("backupVersion", 2)
            .put("exportedAt", Instant.now().toString()).put("state", validate(state)).toString(2)
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "Backup exceeds the supported 10 MB limit." }
        return text
    }
    fun parseBackup(text: String): JSONObject {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "Backup exceeds the 10 MB limit." }
        val backup = JSONObject(text)
        require((backup.optString("format") == "digital-bujo" && backup.optInt("backupVersion") == 1) || (backup.optString("format") == "digital-journal" && backup.optInt("backupVersion") == 2)) { "Unsupported backup format or version." }
        val state = validate(backup.getJSONObject("state"))
        require(profiles(state).isNotEmpty()) { "Backup contains no profiles." }
        // Normalize to supported data fields, matching the desktop importer.
        val clean = fresh()
        for (p in profiles(state)) {
            val copy = JSONObject().put("id", p.getString("id")).put("name", p.getString("name")).put("journals", JSONArray())
            copy.put("tasks", JSONArray(TaskData.tasks(p).map { TaskData.clean(it) }))
            for (j in journals(p)) {
                val cj = JSONObject().put("id", j.getString("id")).put("title", j.getString("title")).put("pages", j.getInt("pages"))
                    .put("format", j.getString("format")).put("formatDetail", j.optString("formatDetail", "")).put("spreads", JSONArray())
                for (s in spreads(j)) cj.getJSONArray("spreads").put(JSONObject().put("id", s.getString("id")).put("title", s.getString("title")).put("start", s.getInt("start")).put("end", s.getInt("end")))
                copy.getJSONArray("journals").put(cj)
            }
            clean.getJSONArray("profiles").put(copy)
        }
        return clean
    }
    fun importCopies(current: JSONObject, imported: JSONObject) {
        validate(imported)
        for (source in profiles(imported)) {
            val copy = JSONObject(source.toString())
            val original = copy.getString("name")
            var name = original
            var count = 1
            while (profiles(current).any { it.getString("name").equals(name, true) }) {
                val suffix = " (import ${count++})"
                name = original.take(80 - suffix.length) + suffix
            }
            copy.put("id", id()).put("name", name)
            for (t in TaskData.tasks(copy)) { t.put("id", id()); for (s in list(t.getJSONArray("subtasks"))) s.put("id", id()) }
            for (j in journals(copy)) { j.put("id", id()); for (s in spreads(j)) s.put("id", id()) }
            current.getJSONArray("profiles").put(copy)
        }
    }
}
