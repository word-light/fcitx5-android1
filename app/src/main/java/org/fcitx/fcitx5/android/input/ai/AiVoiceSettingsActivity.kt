/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SogaKey: settings screen for AI voice input (API key, model, microphone).
 */
package org.fcitx.fcitx5.android.input.ai

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class AiVoiceSettingsActivity : AppCompatActivity() {

    private lateinit var micStatus: TextView
    private lateinit var micButton: Button
    private lateinit var keyInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var groqInput: EditText
    private lateinit var testButton: Button

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            // permanently denied -> send the user to the app's permission page
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null))
            )
            Toast.makeText(this, "請在「權限」裡允許麥克風", Toast.LENGTH_LONG).show()
        }
        refreshMic()
    }

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun heading(t: String) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(24), 0, dp(6))
    }

    private fun note(t: String) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        alpha = 0.8f
    }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        minHeight = dp(56)
        setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        title = "AI 語音設定"

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(32))
        }
        fun add(v: android.view.View) = col.addView(v, LinearLayout.LayoutParams(-1, -2))

        add(TextView(this).apply {
            text = "SogaKey AI 語音設定"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        add(note("用法：在鍵盤上方按 🎤，說完按「說完了」，再選「輸入原文」或「翻譯」。"))

        // 0. Keyboard setup shortcuts
        add(heading("⓪ 輸入法設定"))
        add(note("啟用 SogaKey 鍵盤、加入「新酷音」(注音) 和韓文，請按下面的按鈕。"))
        add(button("設定輸入法（加入注音／韓文）") {
            startActivity(
                Intent(this, org.fcitx.fcitx5.android.ui.main.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        })
        add(button("開啟系統鍵盤設定（啟用 SogaKey）") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })

        // 1. Microphone
        add(heading("① 麥克風"))
        micStatus = note("")
        add(micStatus)
        micButton = button("允許使用麥克風") { askMic.launch(Manifest.permission.RECORD_AUDIO) }
        add(micButton)

        // 2. API key
        add(heading("② Gemini API 金鑰"))
        add(note("到 Google AI Studio 用 Google 帳號登入，按「Create API key」，複製後回來按「貼上」。"))
        add(button("開啟 Google AI Studio 取得金鑰") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
        })
        keyInput = EditText(this).apply {
            hint = "AIza…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(AiVoicePrefs.apiKey(this@AiVoiceSettingsActivity))
        }
        add(keyInput)
        add(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button("貼上") {
                val cm = getSystemService(ClipboardManager::class.java)
                val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(this@AiVoiceSettingsActivity)
                if (clip.isNullOrBlank()) toast("剪貼簿是空的") else keyInput.setText(clip.toString().trim())
                save()
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(button("儲存") { save(); toast("已儲存") },
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        })
        testButton = button("測試連線") { test() }
        add(testButton)

        // 2b. Groq key (optional, much faster)
        add(heading("②-2 Groq 金鑰（選填，更快）"))
        add(note("有填的話，錄音會先用 Groq 的 Whisper 很快轉成文字，再交給 Gemini 整理和翻譯。不填就維持原本的做法。到 console.groq.com 註冊後建立 API Key。"))
        add(button("開啟 Groq 取得金鑰") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys")))
        })
        groqInput = EditText(this).apply {
            hint = "gsk_…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(AiVoicePrefs.groqKey(this@AiVoiceSettingsActivity))
        }
        add(groqInput)
        add(button("貼上 Groq 金鑰") {
            val cm = getSystemService(ClipboardManager::class.java)
            val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(this@AiVoiceSettingsActivity)
            if (clip.isNullOrBlank()) toast("剪貼簿是空的") else groqInput.setText(clip.toString().trim())
            save()
        })

        // 3. Behaviour
        add(heading("③ 其他"))
        add(CheckBox(this).apply {
            text = "按 🎤 後立刻開始聽（不用再按一次）"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            isChecked = AiVoicePrefs.autoStart(this@AiVoiceSettingsActivity)
            setOnCheckedChangeListener { _, c -> AiVoicePrefs.setAutoStart(this@AiVoiceSettingsActivity, c) }
        })
        add(note("模型名稱（一般不用改，留空＝${AiVoicePrefs.DEFAULT_MODEL}）"))
        modelInput = EditText(this).apply {
            hint = AiVoicePrefs.DEFAULT_MODEL
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            val m = AiVoicePrefs.model(this@AiVoiceSettingsActivity)
            setText(if (m == AiVoicePrefs.DEFAULT_MODEL) "" else m)
        }
        add(modelInput)
        add(note("隱私說明：只有按 🎤 之後的錄音會送出處理（有填 Groq 金鑰時送到 Groq，否則送到 Google Gemini）；平常打字不會上傳。"))

        val scroll = ScrollView(this).apply {
            addView(col, ViewGroup.LayoutParams(-1, -2))
        }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        setContentView(scroll)
        refreshMic()
    }

    override fun onResume() {
        super.onResume()
        refreshMic()
    }

    override fun onPause() {
        super.onPause()
        save()
    }

    private fun refreshMic() {
        if (!::micStatus.isInitialized) return
        val ok = AiVoicePrefs.hasMicPermission(this)
        micStatus.text = if (ok) "✓ 已允許" else "尚未允許"
        micButton.isEnabled = !ok
    }

    private fun save() {
        AiVoicePrefs.setApiKey(this, keyInput.text.toString())
        AiVoicePrefs.setGroqKey(this, groqInput.text.toString())
        AiVoicePrefs.setModel(this, modelInput.text.toString())
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun test() {
        save()
        testButton.isEnabled = false
        testButton.text = "測試中…"
        // half a second of silence is enough to validate key + model
        val silence = ByteArray(WavRecorder.SAMPLE_RATE) // 0.5 s of 16-bit samples
        val wav = WavRecorder.toWav(silence)
        lifecycleScope.launch {
            val msg = try {
                GeminiClient.recognize(AiVoicePrefs.apiKey(this@AiVoiceSettingsActivity),
                    AiVoicePrefs.model(this@AiVoiceSettingsActivity), wav)
                "✓ 連線成功，可以開始使用了"
            } catch (e: AiVoiceException) {
                "✗ ${e.message}"
            } catch (e: Exception) {
                "✗ ${e.message ?: e.javaClass.simpleName}"
            }
            testButton.isEnabled = true
            testButton.text = "測試連線"
            Toast.makeText(this@AiVoiceSettingsActivity, msg, Toast.LENGTH_LONG).show()
        }
    }
}
