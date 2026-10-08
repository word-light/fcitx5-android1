/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SogaKey: one Gemini call = speech recognition + clean-up + zh<->en translation.
 */
package org.fcitx.fcitx5.android.input.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

data class VoiceResult(
    /** "zh", "en", "ko" or "other" */
    val sourceLang: String,
    val text: String,
    val translation: String
) {
    /** Label for the translate button, e.g. "翻成英文". */
    val translationLabel: String
        get() = if (sourceLang == "zh") "翻成英文" else "翻成中文"
}

class AiVoiceException(message: String, val code: Int = 0) : Exception(message)

object GeminiClient {

    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/"

    private val PROMPT = """
你是語音輸入法的聽寫員。請聽這段錄音，完成以下工作，只輸出 JSON：

1. 判斷說話的主要語言 source_lang："zh"（中文）、"en"（英文）、"ko"（韓文）或 "other"。
2. text：把說的話寫成可以直接送出的文字。
   - 中文一律用台灣慣用的繁體中文與全形標點（，。？！）。
   - 刪掉口頭禪與贅字（嗯、呃、那個、就是說、um、uh），以及說錯後立刻更正的重複部分。
   - 依語意補上標點，修正明顯的同音錯字。
   - 不要增加原本沒說的內容，不要回答問題，不要加說明。
   - 中英夾雜時保留原本的英文單字。
3. translation：
   - source_lang 是 "zh" → 翻成自然、口語的英文。
   - 其他語言 → 翻成台灣慣用的繁體中文。
4. 如果錄音裡沒有清楚的人聲，text 和 translation 都填空字串。

輸出格式：{"source_lang":"zh","text":"...","translation":"..."}
""".trimIndent()

    /**
     * Thinking is the main source of delay for dictation, so turn it off. If Google answers with a
     * server error (overloaded, or the model dislikes the setting) retry: first without the
     * thinking setting, then once more after a short pause.
     */
    suspend fun recognize(apiKey: String, model: String, wav: ByteArray): VoiceResult {
        var last: AiVoiceException? = null
        val plans = listOf(true to 0L, false to 0L, false to 1500L)
        for ((noThinking, delayMs) in plans) {
            if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
            try {
                return recognize(apiKey, model, wav, noThinking)
            } catch (e: AiVoiceException) {
                val retryable = e.code in 500..599 || e.message?.contains("think", true) == true
                if (!retryable) throw e
                last = e
            }
        }
        throw last!!
    }

    private suspend fun recognize(
        apiKey: String, model: String, wav: ByteArray, noThinking: Boolean
    ): VoiceResult =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) throw AiVoiceException("還沒設定 Gemini API 金鑰")
            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray()
                        .put(JSONObject().put("text", PROMPT))
                        .put(JSONObject().put("inline_data", JSONObject().apply {
                            put("mime_type", "audio/wav")
                            put("data", Base64.encodeToString(wav, Base64.NO_WRAP))
                        }))
                    )
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("responseMimeType", "application/json")
                    if (noThinking) put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                })
            }.toString()

            val url = URL(ENDPOINT + URLEncoder.encode(model, "UTF-8") + ":generateContent")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-goog-api-key", apiKey)
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val resp = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (code !in 200..299) throw AiVoiceException(describeError(code, resp), code)
                parse(resp)
            } catch (e: AiVoiceException) {
                throw e
            } catch (e: UnknownHostException) {
                throw AiVoiceException("沒有網路連線")
            } catch (e: SocketTimeoutException) {
                throw AiVoiceException("網路太慢，等不到回應，請再試一次")
            } catch (e: IOException) {
                throw AiVoiceException("連線失敗：${e.message ?: e.javaClass.simpleName}")
            } finally {
                conn.disconnect()
            }
        }

    private val TEXT_PROMPT = """
你是語音輸入法的校稿員。下面是語音辨識出來的文字，請只輸出 JSON：

1. source_lang："zh"（中文）、"en"（英文）、"ko"（韓文）或 "other"。
2. text：整理成可以直接送出的文字。
   - 中文一律用台灣慣用的繁體中文（簡體要轉成繁體）與全形標點（，。？！）。
   - 刪掉口頭禪與贅字（嗯、呃、那個、就是說、um、uh），以及說錯後立刻更正的重複部分。
   - 依語意補上標點，修正明顯的同音錯字。
   - 不要增加原本沒說的內容，不要回答問題，不要加說明。
   - 中英夾雜時保留原本的英文單字。
3. translation：source_lang 是 "zh" → 翻成自然、口語的英文；其他語言 → 翻成台灣慣用的繁體中文。
4. 如果文字是空的或沒有意義，text 和 translation 都填空字串。

輸出格式：{"source_lang":"zh","text":"...","translation":"..."}

辨識文字：
""".trimIndent()

    const val FAST_TEXT_MODEL = "gemini-flash-lite-latest"

    /** Text-only clean-up + translation of an already transcribed sentence (fast). */
    suspend fun refine(apiKey: String, model: String, raw: String): VoiceResult {
        return try {
            callText(apiKey, FAST_TEXT_MODEL, raw)
        } catch (e: AiVoiceException) {
            if (model == FAST_TEXT_MODEL) throw e
            callText(apiKey, model, raw)
        }
    }

    private suspend fun callText(apiKey: String, model: String, raw: String): VoiceResult =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", TEXT_PROMPT + raw)))
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("responseMimeType", "application/json")
                    put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                })
            }.toString()
            val url = URL(ENDPOINT + URLEncoder.encode(model, "UTF-8") + ":generateContent")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-goog-api-key", apiKey)
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val resp = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (code !in 200..299) throw AiVoiceException(describeError(code, resp), code)
                parse(resp)
            } catch (e: AiVoiceException) {
                throw e
            } catch (e: IOException) {
                throw AiVoiceException("連線失敗：${e.message ?: e.javaClass.simpleName}")
            } finally {
                conn.disconnect()
            }
        }

    private fun describeError(code: Int, resp: String): String {
        val msg = runCatching {
            JSONObject(resp).getJSONObject("error").getString("message")
        }.getOrDefault(resp.take(200))
        return when (code) {
            400 -> if (msg.contains("API key", true)) "API 金鑰不正確，請到設定重新貼上" else "要求有誤：$msg"
            401, 403 -> "API 金鑰無效或沒有權限，請到設定檢查"
            404 -> "找不到模型，請到設定檢查模型名稱（預設 ${AiVoicePrefs.DEFAULT_MODEL}）"
            429 -> "用量超過限制（免費額度用完或太頻繁），請稍後再試"
            in 500..599 -> "Google 伺服器忙碌中，請再試一次（$code：${msg.take(80)}）"
            else -> "錯誤 $code：$msg"
        }
    }

    private fun parse(resp: String): VoiceResult {
        val parts = JSONObject(resp)
            .optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw AiVoiceException("AI 沒有回傳內容，請再說一次")
        val text = buildString {
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                if (p.optBoolean("thought", false)) continue
                append(p.optString("text"))
            }
        }.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = runCatching { JSONObject(text) }.getOrNull()
            ?: return VoiceResult("other", text, "")
        return VoiceResult(
            sourceLang = json.optString("source_lang", "other"),
            text = json.optString("text").trim(),
            translation = json.optString("translation").trim()
        )
    }
}
