package pl.digitalbujo.app

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

class JournalRepository(context: Context) {
    init { LegacyCredentials.remove(context) }
    private val appContext = context.applicationContext
    private val file = AtomicFile(File(context.filesDir, "journals.json"))
    private var state: JSONObject = try {
        JournalData.validate(JSONObject(file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }))
    } catch (error: java.io.FileNotFoundException) {
        if (file.baseFile.exists()) throw error
        JournalData.fresh()
    }
    fun read() = JSONObject(state.toString())
    fun change(action: (JSONObject) -> Unit) {
        val next = read()
        action(next)
        JournalData.validate(next)
        val output = file.startWrite()
        try {
            output.write(next.toString(2).toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw java.io.IOException("Could not save. Previous journal data is unchanged. Check free storage.", error)
        }
        state = next
        // Widget refresh must never turn a successful journal save into a reported failure.
        runCatching { JournalWidget.updateAll(appContext,next) }
    }
}
