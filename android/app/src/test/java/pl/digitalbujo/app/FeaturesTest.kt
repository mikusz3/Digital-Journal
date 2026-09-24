package pl.digitalbujo.app
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
class FeaturesTest {
    @Test fun profileRenameAndWidgetTargetsPreserveOwnership() {
        val state=JournalData.fresh();val id=JournalData.addProfile(state,"Old name");val other=JournalData.addProfile(state,"Other")
        val journal=JournalData.saveJournal(state,id,null,"Notebook",100,"A5","",null)
        val another=JournalData.saveJournal(state,other,null,"Private notebook",100,"A5","",null)
        val before=JournalData.profile(state,id).getJSONArray("journals").toString()
        JournalData.renameProfile(state,id," New name ")
        assertEquals("New name",JournalData.profile(state,id).getString("name"));assertEquals(before,JournalData.profile(state,id).getJSONArray("journals").toString())
        for(name in listOf("", "other", "x".repeat(81)))assertThrows(IllegalArgumentException::class.java){JournalData.renameProfile(state,id,name)}
        assertEquals("New name",WidgetContent.shelf(state,id)!!.name)
        assertEquals(id to journal,WidgetContent.target(state,id,journal))
        assertEquals(id to null,WidgetContent.target(state,id,another))
        assertNull(WidgetContent.shelf(state,"missing"));assertNull(WidgetContent.target(state,"missing",journal))
        repeat(4){JournalData.saveJournal(state,id,null,"Journal $it",100,"A5","",null)}
        assertEquals(3,WidgetContent.shelf(state,id)!!.journals.size)
        JournalData.deleteProfile(state,id,"New name");assertNull(WidgetContent.shelf(state,id))
    }

    @Test fun tasksMigrateRoundTripAndRegenerateIdentifiers() {
        val state=JournalData.validate(JSONObject("""{"version":1,"profiles":[{"id":"legacy","name":"Old profile","journals":[]}]}"""))
        assertEquals(2,state.getInt("version"));val p=JournalData.profiles(state).single()
        val id=TaskData.save(p,null,"Plan","Notes","2028-02-29",listOf("Find pen","Open book"));val t=TaskData.tasks(p).single();val step=t.getJSONArray("subtasks").getJSONObject(0).getString("id")
        TaskData.toggle(p,id,step);TaskData.toggle(p,id)
        val imported=JournalData.parseBackup(JournalData.backup(state));JournalData.importCopies(state,imported);JournalData.validate(state)
        val copy=TaskData.tasks(JournalData.profiles(state)[1]).single();assertNotEquals(id,copy.getString("id"));assertNotEquals(step,copy.getJSONArray("subtasks").getJSONObject(0).getString("id"));assertTrue(copy.getBoolean("done"))
        assertThrows(java.time.DateTimeException::class.java){TaskData.save(p,id,"Bad","","2027-02-29",emptyList())}
        TaskData.delete(p,id);assertEquals(1,TaskData.tasks(JournalData.profiles(state)[1]).size)
        java.io.File("build/interop/android-tasks.json").apply {parentFile.mkdirs();writeText(JournalData.backup(state))}
    }
    @Test fun generatedQrDecodesToExactLinkOffline() {
        val link="https://example.org/journal?entry=123";val matrix=QRCodeWriter().encode(link,BarcodeFormat.QR_CODE,400,400)
        val pixels=IntArray(400*400){i->if(matrix[i%400,i/400]) 0xff000000.toInt() else 0xffffffff.toInt()}
        val decoded=MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(400,400,pixels))))
        assertEquals(link,decoded.text)
    }
}
