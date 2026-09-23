package pl.digitalbujo.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object TaskData {
    fun tasks(profile: JSONObject) = JournalData.list(if (profile.has("tasks")) profile.getJSONArray("tasks") else JSONArray())
    fun validate(tasks: List<JSONObject>, checkId: (JSONObject) -> Unit) {
        require(tasks.size <= 10000) { "Too many tasks." }
        for (t in tasks) {
            checkId(t); JournalData.label(t.getString("title"), "Task title")
            require(t.get("notes") is String && t.getString("notes").length <= 4000 && t.get("done") is Boolean) { "Invalid task details." }
            val due = t.getString("due")
            require(due.isEmpty() || (Regex("\\d{4}-\\d{2}-\\d{2}").matches(due) && LocalDate.parse(due).toString() == due)) { "Use a valid YYYY-MM-DD date." }
            val steps = JournalData.list(t.getJSONArray("subtasks")); require(steps.size <= 100) { "Use at most 100 subtasks." }
            for (s in steps) { checkId(s); JournalData.label(s.getString("title"), "Subtask title"); require(s.get("done") is Boolean) { "Invalid subtask." } }
        }
    }
    fun clean(t: JSONObject): JSONObject = JSONObject().put("id",t.getString("id")).put("title",t.getString("title")).put("notes",t.getString("notes")).put("due",t.getString("due")).put("done",t.getBoolean("done")).put("subtasks",JSONArray(JournalData.list(t.getJSONArray("subtasks")).map { JSONObject().put("id",it.getString("id")).put("title",it.getString("title")).put("done",it.getBoolean("done")) }))
    fun save(profile: JSONObject, id: String?, title: String, notes: String, due: String, lines: List<String>): String {
        val existing = id?.let { key -> tasks(profile).find { it.getString("id") == key } ?: error("Task not found.") }
        val oldSteps = existing?.let { JournalData.list(it.getJSONArray("subtasks")) } ?: emptyList()
        val steps = lines.filter { it.isNotBlank() }.mapIndexed { i, line -> JSONObject().put("id",oldSteps.getOrNull(i)?.getString("id") ?: JournalData.id()).put("title",JournalData.label(line,"Subtask title")).put("done",oldSteps.getOrNull(i)?.getBoolean("done") ?: false) }
        val t = JSONObject().put("id",id ?: JournalData.id()).put("title",JournalData.label(title,"Task title")).put("notes",notes.trim()).put("due",due.trim()).put("done",existing?.getBoolean("done") ?: false).put("subtasks",JSONArray(steps))
        validate(listOf(t)) {}
        profile.put("tasks",JSONArray(if (existing == null) tasks(profile) + t else tasks(profile).map { if (it.getString("id") == id) t else it }))
        return t.getString("id")
    }
    fun toggle(profile: JSONObject, id: String, step: String? = null) {
        val task = tasks(profile).find { it.getString("id") == id } ?: error("Task not found.")
        val target = if (step == null) task else JournalData.list(task.getJSONArray("subtasks")).find { it.getString("id") == step } ?: error("Subtask not found.")
        target.put("done",!target.getBoolean("done"))
    }
    fun delete(profile: JSONObject, id: String) { require(tasks(profile).any { it.getString("id") == id }) { "Task not found." }; profile.put("tasks",JSONArray(tasks(profile).filter { it.getString("id") != id })) }
}
