package pl.digitalbujo.app
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
class FeaturesTest {
    @Test fun geminiStructuredRequestsAndSafetyResponses() {
        assertEquals("gemini-2.5-flash-lite",AiClient.defaultModel("gemini"))
        val body=AiClient.geminiBody("JSON instruction","My plan")
        assertEquals("My plan",body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text"))
        assertEquals("application/json",body.getJSONObject("generationConfig").getJSONObject("responseFormat").getJSONObject("text").getString("mimeType"))
        val content=JSONObject().put("items",JSONArray().put(JSONObject().put("title","Plan").put("detail","Notes column"))).toString()
        val reply=JSONObject().put("candidates",JSONArray().put(JSONObject().put("finishReason","STOP").put("content",JSONObject().put("parts",JSONArray().put(JSONObject().put("thought",true).put("text","reasoning")).put(JSONObject().put("text",content))))))
        assertEquals("Plan",AiClient.parse("gemini",reply).single().getString("title"))
        assertThrows(IllegalArgumentException::class.java){AiClient.parse("gemini",JSONObject("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))}
        reply.getJSONArray("candidates").getJSONObject(0).put("finishReason","MAX_TOKENS")
        assertThrows(IllegalArgumentException::class.java){AiClient.parse("gemini",reply)}
        assertTrue(AiClient.providerError("gemini",429).contains("daily quota"))
        assertTrue(AiClient.providerError("gemini",400,"""{"error":{"details":[{"reason":"API_KEY_INVALID"}]}}""").contains("API key rejected"))
    }

    @Test fun providerErrorsAreActionableAndDoNotEchoSecrets() {
        assertTrue(AiClient.providerError("deepseek",402).contains("credits"))
        val raw = """{"error":{"type":"insufficient_quota","message":"secret-key-private-context"}}"""
        assertTrue(AiClient.providerError("openai",429,raw).contains("credits"))
        assertFalse(AiClient.providerError("openai",429,raw).contains("secret-key"))
        assertTrue(AiClient.providerError("openai",429,"{}").contains("Too many requests"))
        assertTrue(AiClient.providerError("deepseek",420,"<html>secret</html>").contains("HTTP 420"))
        assertTrue(AiClient.providerError("openai",404).contains("Model unavailable"))
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
    @Test fun aiRefusesIncompleteOrUnusableOutput() {
        val items=JSONObject().put("items",JSONArray().put(JSONObject().put("title","Choose a page").put("detail","One is enough.")))
        val good=JSONObject().put("status","completed").put("output",JSONArray().put(JSONObject().put("content",JSONArray().put(JSONObject().put("type","output_text").put("text",items.toString())))))
        assertEquals("Choose a page",AiClient.parse("openai",good).single().getString("title"))
        good.put("status","incomplete");assertThrows(IllegalArgumentException::class.java){AiClient.parse("openai",good)}
        val deep=JSONObject().put("choices",JSONArray().put(JSONObject().put("finish_reason","stop").put("message",JSONObject().put("content",items.toString()))))
        assertEquals(1,AiClient.parse("deepseek",deep).size)
    }
    @Test fun generatedQrDecodesToExactLinkOffline() {
        val link="https://example.org/journal?entry=123";val matrix=QRCodeWriter().encode(link,BarcodeFormat.QR_CODE,400,400)
        val pixels=IntArray(400*400){i->if(matrix[i%400,i/400]) 0xff000000.toInt() else 0xffffffff.toInt()}
        val decoded=MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(400,400,pixels))))
        assertEquals(link,decoded.text)
    }
}
