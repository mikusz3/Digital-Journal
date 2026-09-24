package pl.digitalbujo.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection

class KeyVault(context: Context) {
    private val prefs = context.getSharedPreferences("api_keys",Context.MODE_PRIVATE)
    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias("digital_journal_keys")) KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply { init(KeyGenParameterSpec.Builder("digital_journal_keys",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); generateKey() }
        return store.getKey("digital_journal_keys",null) as SecretKey
    }
    fun has(provider: String) = prefs.contains(provider)
    fun save(provider: String, key: String) {
        require(provider in listOf("openai","deepseek","gemini") && key.length <= 512 && key.none { it.isWhitespace() || it.code < 32 }) { "Invalid API key." }
        if (key.isEmpty()) { prefs.edit().remove(provider).apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,secret()) }
        val value = Base64.encodeToString(cipher.iv,Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(key.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)
        check(prefs.edit().putString(provider,value).commit()) { "Could not save API key." }
    }
    fun read(provider: String): String {
        val value = prefs.getString(provider,null) ?: error("Add your API key in Settings first.")
        try { val parts = value.split(":"); val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,secret(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP))) }; return String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),Charsets.UTF_8) }
        catch (_: Exception) { error("Saved key unavailable. Enter it again in Settings.") }
    }
}
class AiClient {
    @Volatile private var connection: HttpsURLConnection? = null
    @Volatile private var canceled = false
    fun cancel() { canceled = true; connection?.disconnect() }
    fun generate(provider: String, model: String, kind: String, context: String, key: String, language: String = "English"): List<JSONObject> {
        require(provider in listOf("openai","deepseek","gemini") && kind in listOf("steps","spreads")) { "Unknown provider or suggestion type." }
        require(context.isNotBlank() && context.length <= 6000 && Regex("[a-zA-Z0-9._:-]{1,100}").matches(model)) { "Check model name and context (1–6000 characters)." }
        val instruction = "Return JSON only: {\"items\":[{\"title\":\"short title\",\"detail\":\"short practical explanation\"}]}. Suggest 3 to 8 ${if(kind=="steps") "small achievable task steps" else "paper journal spreads"}. Titles under 120 characters; explanations under 800. No markdown, links, commands or personal assumptions. Write in $language. ${if(kind=="spreads") "In detail, provide a usable paper layout with named sections, positions and short example content." else ""} The next message is user context."
        val messages = JSONArray().put(JSONObject().put("role","system").put("content",instruction)).put(JSONObject().put("role","user").put("content",context))
        val body = if (provider == "gemini") geminiBody(instruction,context) else if (provider == "openai") JSONObject().put("model",model).put("store",false).put("instructions",instruction).put("input",context).put("max_output_tokens",2000).put("text",JSONObject().put("format",JSONObject().put("type","json_object"))) else JSONObject().put("model",model).put("messages",messages).put("max_tokens",2000).put("thinking",JSONObject().put("type","disabled")).put("response_format",JSONObject().put("type","json_object"))
        if(canceled) error("Request canceled.")
        val conn = (URL(if(provider=="gemini") "https://generativelanguage.googleapis.com/v1beta/models/${java.net.URLEncoder.encode(model, "UTF-8")}:generateContent" else if(provider=="openai") "https://api.openai.com/v1/responses" else "https://api.deepseek.com/chat/completions").openConnection() as HttpsURLConnection)
        connection = conn
        try {
            conn.connectTimeout=15000; conn.readTimeout=60000; conn.instanceFollowRedirects=false; conn.requestMethod="POST"; conn.doOutput=true
            conn.setRequestProperty("Content-Type","application/json"); if(provider=="gemini") conn.setRequestProperty("x-goog-api-key",key) else conn.setRequestProperty("Authorization","Bearer $key")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val bytes = (if(status in 200..299) conn.inputStream else conn.errorStream)?.use { stream -> val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(4096); while(true) { val n=stream.read(buffer); if(n<0) break; require(output.size()+n <= 128000) { "Provider response too large." }; output.write(buffer,0,n) }; output.toByteArray() } ?: ByteArray(0)
            if(canceled) error("Request canceled.")
            if(status !in 200..299) error(providerError(provider,status,String(bytes,Charsets.UTF_8)))
            return parse(provider,JSONObject(String(bytes,Charsets.UTF_8)))
        } finally { conn.disconnect(); connection=null }
    }
    companion object {
        fun defaultModel(provider: String) = when(provider) { "gemini" -> "gemini-2.5-flash-lite"; "deepseek" -> "deepseek-flash"; else -> "gpt-4.1-mini" }
        fun geminiBody(instruction: String, context: String): JSONObject {
            val schema=JSONObject("""{"type":"object","properties":{"items":{"type":"array","items":{"type":"object","properties":{"title":{"type":"string"},"detail":{"type":"string"}},"required":["title","detail"],"additionalProperties":false}}},"required":["items"],"additionalProperties":false}""")
            fun parts(text: String) = JSONArray().put(JSONObject().put("text",text))
            return JSONObject().put("systemInstruction",JSONObject().put("parts",parts(instruction)))
                .put("contents",JSONArray().put(JSONObject().put("role","user").put("parts",parts(context))))
                .put("generationConfig",JSONObject().put("maxOutputTokens",2000).put("responseFormat",JSONObject().put("text",JSONObject().put("mimeType","application/json").put("schema",schema))))
        }

        fun providerError(provider: String, status: Int, raw: String = ""): String {
            val error = runCatching { JSONObject(raw).optJSONObject("error") }.getOrNull()
            val details = error?.optJSONArray("details")
            val codes = listOf(error?.optString("code"), error?.optString("type")) + (0 until (details?.length() ?: 0)).map { details?.optJSONObject(it)?.optString("reason") }
            // Never display raw error messages: providers may echo keys or user context.
            val advice = when {
                status == 402 || codes.any { it in listOf("insufficient_quota","billing_hard_limit_reached","billing_not_active","usage_limit_reached","organization_usage_limit_exceeded") } -> "API credits or spending limit exhausted. Check billing and limits in your provider API account. A chat subscription does not supply API credits. Retrying will not fix billing."
                status == 401 || "API_KEY_INVALID" in codes || "API_KEY_EXPIRED" in codes -> "API key rejected. Enter a valid key for this provider in Settings."
                status == 403 -> "Access denied. Check this key’s permissions, model access and supported region."
                provider == "gemini" && status == 429 -> "Gemini request or daily quota reached. Check your project limits in Google AI Studio; wait for the indicated reset. Free-tier availability depends on your model and project. No automatic paid retry was made."
                status == 429 -> "Too many requests. Wait before trying again; check your provider rate limits."
                status == 404 || "model_not_found" in codes -> "Model unavailable. Check the model name in Settings and access for this API account."
                status == 400 || status == 422 -> "Request rejected. Check the model name and whether it supports JSON suggestions. Try the default model."
                status >= 500 -> "Provider temporarily unavailable. Try again later."
                else -> "Unexpected provider response. Report this HTTP number and provider name; never share your API key."
            }
            return "${if(provider == "gemini") "Gemini" else if(provider == "deepseek") "DeepSeek" else "OpenAI"} HTTP $status. $advice Nothing was changed."
        }
        fun parse(provider: String, data: JSONObject): List<JSONObject> {
            val text = if(provider=="gemini") {
                val candidate=data.optJSONArray("candidates")?.optJSONObject(0)
                require(data.optJSONObject("promptFeedback")?.optString("blockReason").isNullOrEmpty() && candidate?.optString("finishReason")=="STOP") { "Gemini returned blocked or incomplete suggestions. Nothing was changed." }
                JournalData.list(candidate!!.getJSONObject("content").getJSONArray("parts")).filter { !it.optBoolean("thought",false) && it.has("text") }.joinToString("") { it.getString("text") }
            } else if(provider=="openai") {
                require(data.optString("status")=="completed") { "Provider did not finish. Nothing was changed." }
                JournalData.list(data.getJSONArray("output")).flatMap { JournalData.list(it.optJSONArray("content") ?: JSONArray()) }.filter { it.optString("type")=="output_text" }.joinToString("") { it.getString("text") }
            } else { val choice=data.getJSONArray("choices").getJSONObject(0); require(choice.optString("finish_reason")=="stop") { "Provider did not finish." }; choice.getJSONObject("message").getString("content") }
            val items=JournalData.list(JSONObject(text).getJSONArray("items")); require(items.size in 1..12) { "Invalid suggestion count." }
            return items.map { JSONObject().put("title",JournalData.label(it.getString("title"),"Suggestion title")).put("detail",it.getString("detail").also { detail -> require(detail.length<=800) { "Suggestion too long." } }) }
        }
    }
}
