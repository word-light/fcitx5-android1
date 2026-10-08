/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SogaKey: fast speech-to-text through Groq's hosted Whisper (OpenAI-compatible API).
 */
package org.fcitx.fcitx5.android.input.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

object GroqClient {
    private const val URL_STR = "https://api.groq.com/openai/v1/audio/transcriptions"
    const val MODEL = "whisper-large-v3-turbo"

    suspend fun transcribe(apiKey: String, wav: ByteArray): String = withContext(Dispatchers.IO) {
        val boundary = "----sogakey" + System.nanoTime()
        val out = ByteArrayOutputStream()
        fun field(name: String, value: String) {
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n"
                .toByteArray(Charsets.UTF_8))
        }
        field("model", MODEL)
        field("response_format", "json")
        field("temperature", "0")
        // a Traditional-Chinese prompt nudges Whisper towards 繁體 output
        field("prompt", "以下是繁體中文的句子，可能夾雜英文。")
        out.write(("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.wav\"\r\n" +
                "Content-Type: audio/wav\r\n\r\n").toByteArray(Charsets.UTF_8))
        out.write(wav)
        out.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))

        val conn = (URL(URL_STR).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        try {
            conn.outputStream.use { it.write(out.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(resp).getJSONObject("error").getString("message") }
                    .getOrDefault(resp.take(150))
                throw AiVoiceException(
                    when (code) {
                        401, 403 -> "Groq 金鑰無效，請到設定檢查"
                        429 -> "Groq 用量超過限制，請稍後再試"
                        else -> "Groq 錯誤 $code：$msg"
                    }, code
                )
            }
            JSONObject(resp).optString("text").trim()
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
}
