package com.jarvis.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Brain {
    const val MODEL = "llama-3.3-70b-versatile"
    private val history = ArrayList<JSONObject>()

    private const val SYSTEM = """You are JARVIS, a voice assistant living inside the user's Android phone. You control the phone.
Reply with ONLY one JSON object, no other text:
{"say":"...","actions":[...],"more":false}
"say": what you speak aloud. Max 2 short sentences. Confident, bold tone. Reply in the language the user spoke (English or Hinglish).
"actions": list of objects, each with a "do" field:
 open_app{name} call{to} sms{to,text} alarm{hour,minute,label} timer{seconds} flashlight{on:true/false} volume{level:0-100}
 url{url} search{query} navigate{place} music{query} settings{page: wifi|bluetooth|location|display|sound|battery|apps|airplane|data|main}
 system{what: home|back|recents|notifications|quick_settings|lock|screenshot|power}
 click{text} type{text} enter{} scroll{dir: up|down} wait{ms}
"to" can be a contact name or a phone number.
For jobs that need several screens (WhatsApp, YouTube, settings toggles...), do the first steps, set "more":true and you will get the SCREEN text back, then continue. Set "more":false when finished.
Use click/type only on text you actually saw in SCREEN. Never invent screen content.
If anyone asks who your boss is, say exactly: Prem is my boss.
Never send money or delete things unless the user clearly confirmed; ask first with no actions.
If no action is needed, return "actions":[]."""

    fun ask(key: String, user: String, info: String): JSONObject {
        history.add(JSONObject().put("role", "user").put("content", user))
        while (history.size > 14) history.removeAt(0)
        try {
            val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", SYSTEM + "\n" + info))
            for (m in history) msgs.put(m)
            val body = JSONObject().put("model", MODEL).put("messages", msgs).put("temperature", 0.4)
                .put("response_format", JSONObject().put("type", "json_object"))
            val c = URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("Authorization", "Bearer $key")
            c.setRequestProperty("Content-Type", "application/json")
            c.doOutput = true
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val txt = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) throw Exception("Groq error $code")
            val content = JSONObject(txt).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
            history.add(JSONObject().put("role", "assistant").put("content", content))
            return try { JSONObject(content) } catch (e: Exception) { JSONObject().put("say", content) }
        } catch (e: Exception) {
            history.removeAt(history.size - 1)
            throw e
        }
    }
}
