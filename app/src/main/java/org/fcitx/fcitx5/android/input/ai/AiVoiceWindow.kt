/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SogaKey: the AI voice panel that replaces the keyboard while dictating.
 *
 * Flow: mic button -> listening -> "說完了" -> AI result ->
 *       user picks [輸入原文] or [翻成英文/翻成中文]. Nothing is sent without a choice.
 */
package org.fcitx.fcitx5.android.input.ai

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.keyboard.CommonKeyActionListener
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import org.fcitx.fcitx5.android.input.keyboard.KeyActionListener
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.mechdancer.dependency.manager.must

class AiVoiceWindow : InputWindow.ExtendedInputWindow<AiVoiceWindow>() {

    private val service: FcitxInputMethodService by manager.inputMethodService()
    private val windowManager: InputWindowManager by manager.must()
    private val commonKeyActionListener: CommonKeyActionListener by manager.must()
    private val theme by manager.theme()

    override val title: String get() = "AI 語音輸入"

    private val recorder = WavRecorder()
    private var job: Job? = null
    private var result: VoiceResult? = null
    private val handler = Handler(Looper.getMainLooper())

    // ---------- small view helpers ----------

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics
    ).toInt()

    private fun rounded(color: Int, radius: Int = 14) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun bigButton(label: String, accent: Boolean, onClick: () -> Unit) =
        Button(context).apply {
            text = label
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (accent) theme.accentKeyTextColor else theme.keyTextColor)
            background = rounded(if (accent) theme.accentKeyBackgroundColor else theme.keyBackgroundColor)
            stateListAnimator = null
            minHeight = dp(64)
            setOnClickListener { onClick() }
        }

    private fun row(vararg buttons: Pair<View, Float>) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        buttons.forEachIndexed { i, (v, weight) ->
            addView(v, LinearLayout.LayoutParams(0, dp(64), weight).apply {
                // generous gap so buttons are hard to hit by mistake
                if (i > 0) marginStart = dp(16)
            })
        }
    }

    private fun resultCard(label: String, body: String) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(theme.keyBackgroundColor, 10)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        addView(TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.altKeyTextColor)
        })
        addView(TextView(context).apply {
            text = body.ifEmpty { "（沒有內容）" }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            setTextColor(theme.keyTextColor)
            setTextIsSelectable(false)
        })
    }

    // ---------- layout ----------

    private val statusText by lazy {
        TextView(context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(theme.keyTextColor)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    private val levelBar by lazy {
        ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(theme.accentKeyBackgroundColor)
        }
    }

    private val spinner by lazy { ProgressBar(context) }

    private val content by lazy {
        LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    }

    private val actions by lazy {
        LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    }

    private val root by lazy {
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(16), dp(8), dp(16), dp(12))
            addView(ScrollView(context).apply {
                isFillViewport = true
                addView(content, ViewGroup.LayoutParams(-1, -2))
            }, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
    }

    override fun onCreateView(): View = root

    private fun setContent(vararg views: View) {
        content.removeAllViews()
        content.gravity = Gravity.CENTER
        views.forEach {
            content.addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    private fun setActions(vararg rows: View) {
        actions.removeAllViews()
        rows.forEachIndexed { i, r ->
            actions.addView(r, LinearLayout.LayoutParams(-1, -2).apply {
                if (i > 0) topMargin = dp(12)
            })
        }
    }

    // ---------- states ----------

    private fun showIdle(message: String = "按下按鈕開始說話") {
        statusText.text = message
        setContent(statusText)
        setActions(
            row(bigButton("🎤 開始說話", true) { startListening() } to 1f),
            row(bigButton("⌨ 回鍵盤", false) { backToKeyboard() } to 1f,
                bigButton("⚙ 設定", false) { openSettings() } to 1f)
        )
    }

    private fun showNeedsSetup() {
        val missingKey = AiVoicePrefs.apiKey(context).isEmpty()
        val missingMic = !AiVoicePrefs.hasMicPermission(context)
        statusText.text = buildString {
            append("第一次使用需要設定：\n")
            if (missingKey) append("・貼上 Gemini API 金鑰\n")
            if (missingMic) append("・允許使用麥克風")
        }.trim()
        setContent(statusText)
        setActions(
            row(bigButton("⚙ 前往設定", true) { openSettings() } to 1f),
            row(bigButton("⌨ 回鍵盤", false) { backToKeyboard() } to 1f)
        )
    }

    private fun startListening() {
        if (AiVoicePrefs.apiKey(context).isEmpty() || !AiVoicePrefs.hasMicPermission(context)) {
            showNeedsSetup()
            return
        }
        try {
            recorder.start()
        } catch (e: Exception) {
            showError(e.message ?: "麥克風無法啟動")
            return
        }
        statusText.text = "正在聽…"
        levelBar.progress = 0
        setContent(statusText, levelBar)
        setActions(
            row(bigButton("✕ 取消", false) { cancelListening() } to 1f,
                bigButton("✓ 說完了", true) { finishListening() } to 2.2f)
        )
        tick()
    }

    private val ticker = Runnable { tick() }

    private fun tick() {
        if (!recorder.isRunning) {
            if (recorder.reachedLimit) finishListening()
            return
        }
        statusText.text = "正在聽… ${recorder.seconds} 秒"
        levelBar.progress = (recorder.level * 140).toInt().coerceAtMost(100)
        handler.postDelayed(ticker, 100)
    }

    private fun cancelListening() {
        handler.removeCallbacks(ticker)
        recorder.cancel()
        backToKeyboard()
    }

    private fun finishListening() {
        handler.removeCallbacks(ticker)
        val wav = recorder.stop()
        if (wav.size < 44 + WavRecorder.SAMPLE_RATE / 2) { // under ~0.25 s
            showIdle("太短了，沒聽到聲音，再試一次")
            return
        }
        statusText.text = "AI 整理中…"
        setContent(statusText, spinner)
        setActions(row(bigButton("✕ 取消", false) {
            job?.cancel()
            showIdle()
        } to 1f))
        val key = AiVoicePrefs.apiKey(context)
        val model = AiVoicePrefs.model(context)
        job = service.lifecycleScope.launch {
            try {
                val groq = AiVoicePrefs.groqKey(context)
                var raw: String? = null
                if (groq.isNotEmpty()) {
                    // fast path: Whisper first (show the raw text at once), then Gemini text clean-up
                    raw = try {
                        GroqClient.transcribe(groq, wav)
                    } catch (e: AiVoiceException) {
                        null // fall back to Gemini listening to the audio
                    }
                }
                if (raw != null) {
                    if (raw.isEmpty()) {
                        showIdle("沒有聽清楚，再說一次")
                    } else {
                        showResult(VoiceResult("other", raw, ""))
                        val refined = try {
                            GeminiClient.refine(key, model, raw)
                        } catch (e: AiVoiceException) {
                            null
                        }
                        if (refined != null && refined.text.isNotEmpty()) showResult(refined)
                    }
                } else {
                    val r = GeminiClient.recognize(key, model, wav)
                    if (r.text.isEmpty()) showIdle("沒有聽清楚，再說一次")
                    else showResult(r)
                }
            } catch (e: AiVoiceException) {
                showError(e.message ?: "發生錯誤")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                showError("發生錯誤：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun showResult(r: VoiceResult) {
        result = r
        val cards = mutableListOf<View>(resultCard("原文", r.text))
        if (r.translation.isNotEmpty()) cards.add(resultCard(r.translationLabel, r.translation))
        content.removeAllViews()
        content.gravity = Gravity.TOP
        cards.forEach {
            content.addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        val main = if (r.translation.isNotEmpty()) {
            row(bigButton("輸入原文", true) { commit(r.text) } to 1f,
                bigButton(r.translationLabel, true) { commit(r.translation) } to 1f)
        } else {
            row(bigButton("輸入原文", true) { commit(r.text) } to 1f)
        }
        setActions(
            main,
            row(bigButton("🎤 重說", false) { startListening() } to 1f,
                bigButton("✕ 取消", false) { backToKeyboard() } to 1f)
        )
    }

    private fun showError(message: String) {
        statusText.text = message
        setContent(statusText)
        setActions(
            row(bigButton("🎤 再試一次", true) { startListening() } to 1f),
            row(bigButton("⌨ 回鍵盤", false) { backToKeyboard() } to 1f,
                bigButton("⚙ 設定", false) { openSettings() } to 1f)
        )
    }

    // ---------- actions ----------

    private fun commit(text: String) {
        commonKeyActionListener.listener.onKeyAction(
            KeyAction.CommitAction(text), KeyActionListener.Source.Keyboard
        )
        backToKeyboard()
    }

    private fun backToKeyboard() {
        windowManager.attachWindow(KeyboardWindow)
    }

    private fun openSettings() {
        service.requestHideSelf(0)
        context.startActivity(
            Intent(context, AiVoiceSettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun onAttached() {
        result = null
        if (AiVoicePrefs.apiKey(context).isEmpty() || !AiVoicePrefs.hasMicPermission(context)) {
            showNeedsSetup()
        } else if (AiVoicePrefs.autoStart(context)) {
            startListening()
        } else {
            showIdle()
        }
    }

    override fun onDetached() {
        handler.removeCallbacks(ticker)
        if (recorder.isRunning) recorder.cancel()
        job?.cancel()
        job = null
    }
}
