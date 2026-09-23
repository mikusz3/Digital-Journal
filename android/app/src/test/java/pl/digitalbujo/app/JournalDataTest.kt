package pl.digitalbujo.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class JournalDataTest {
    private fun fixture() = javaClass.classLoader!!.getResource("desktop-backup.json")!!.readText()
    @Test fun desktopBackupImportsWithoutLosingUnicodeOrCustomSize() {
        val state = JournalData.parseBackup(fixture())
        val profile = JournalData.profiles(state).single()
        assertEquals("Interop Łukasz", profile.getString("name"))
        val journal = JournalData.journals(profile).single()
        assertEquals("15 × 21 cm", journal.getString("formatDetail"))
        assertEquals("Wdzięczność", JournalData.spreads(journal).single().getString("title"))
        val out = File("build/interop/android-backup.json")
        out.parentFile!!.mkdirs(); out.writeText(JournalData.backup(state))
    }
    @Test fun duplicateImportPreservesOriginalAndRenamesCopy() {
        val state = JournalData.parseBackup(fixture())
        val original = JournalData.profiles(state).single().toString()
        JournalData.importCopies(state, JournalData.parseBackup(fixture()))
        JournalData.validate(state)
        val profiles = JournalData.profiles(state)
        assertEquals(original, profiles[0].toString())
        assertEquals("Interop Łukasz (import 1)", profiles[1].getString("name"))
        assertNotEquals(profiles[0].getString("id"), profiles[1].getString("id"))
        assertNotEquals(JournalData.journals(profiles[0])[0].getString("id"), JournalData.journals(profiles[1])[0].getString("id"))
    }
    @Test fun createEditAndValidatePageRanges() {
        val state = JournalData.fresh()
        val profileId = JournalData.addProfile(state, "Alex")
        val journalId = JournalData.saveJournal(state, profileId, null, "Notebook", 96, "A5", "")
        val spreadId = JournalData.saveSpread(state, profileId, journalId, null, "Old", 90, 96)
        assertEquals(spreadId, JournalData.saveSpread(state, profileId, journalId, spreadId, "New", 80, 85))
        JournalData.saveJournal(state, profileId, journalId, "Renamed", 85, "B5", "")
        JournalData.validate(state)
        assertThrows(IllegalArgumentException::class.java) { JournalData.saveJournal(state, profileId, journalId, "Small", 84, "B5", "") }
        assertThrows(IllegalArgumentException::class.java) { JournalData.saveSpread(state, profileId, journalId, null, "Overlap", 85, 85) }
        assertThrows(IllegalArgumentException::class.java) { JournalData.saveSpread(state, profileId, journalId, null, "Invalid", 0, 1) }
        val second = JournalData.addProfile(state, "Sam")
        assertThrows(IllegalStateException::class.java) { JournalData.saveSpread(state, second, journalId, null, "Wrong", 1, 1) }
    }
    @Test fun rejectsFutureDamagedAndOversizedBackups() {
        val future = JSONObject(fixture()).put("backupVersion", 2)
        assertThrows(IllegalArgumentException::class.java) { JournalData.parseBackup(future.toString()) }
        val damaged = JSONObject(fixture())
        val state = damaged.getJSONObject("state")
        state.getJSONArray("profiles").put(JournalData.profiles(state).first())
        assertThrows(IllegalArgumentException::class.java) { JournalData.parseBackup(damaged.toString()) }
        assertThrows(IllegalArgumentException::class.java) { JournalData.parseBackup(" ".repeat(JournalData.MAX_BACKUP_BYTES + 1)) }
    }
    @Test fun rejectsCoercedNumericFields() {
        val broken = JSONObject(fixture())
        val j = JournalData.journals(JournalData.profiles(broken.getJSONObject("state")).first()).first()
        j.put("pages", "96")
        assertThrows(IllegalArgumentException::class.java) { JournalData.parseBackup(broken.toString()) }
    }
    @Test fun deleteProfileRequiresExactNameAndPreservesOtherProfiles() {
        val state = JournalData.parseBackup(fixture())
        val original = JournalData.profiles(state).single()
        val alex = JournalData.addProfile(state, "QA Alex")
        assertThrows(IllegalArgumentException::class.java) { JournalData.deleteProfile(state, alex, "Wrong") }
        assertEquals(2, JournalData.profiles(state).size)
        JournalData.deleteProfile(state, alex, "QA Alex")
        assertEquals(original.toString(), JournalData.profiles(state).single().toString())
        JournalData.deleteProfile(state, original.getString("id"), "Interop Łukasz")
        assertEquals(0, JournalData.profiles(JournalData.validate(state)).size)
    }
}
