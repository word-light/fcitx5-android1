/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SogaKey: handwriting input panel (offline recognition by Google ML Kit).
 */
package org.fcitx.fcitx5.android.input.ai

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.keyboard.CommonKeyActionListener
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import org.fcitx.fcitx5.android.input.keyboard.KeyActionListener
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.mechdancer.dependency.manager.must

/** A canvas that records strokes as an ML Kit [Ink]. */
@SuppressLint("ViewConstructor")
private class InkView(
    context: Context,
    theme: Theme,
    private val onStrokeStart: () -> Unit,
    private val onStrokeEnd: (Ink) -> Unit
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.keyTextColor
        style = Paint.Style.STROKE
        strokeWidth = 7f * resources.displayMetrics.density / 2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val paths = mutableListOf<Path>()
    private var current: Path? = null
    private var inkBuilder = Ink.builder()
    private var strokeBuilder: Ink.Stroke.Builder? = null

    fun clearInk() {
        paths.clear()
        current = null
        inkBuilder = Ink.builder()
        strokeBuilder = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        paths.forEach { canvas.drawPath(it, paint) }
        current?.let { canvas.drawPath(it, paint) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val t = System.currentTimeMillis()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                onStrokeStart()
                current = Path().apply { moveTo(x, y) }
                strokeBuilder = Ink.Stroke.builder().apply {
                    addPoint(Ink.Point.create(x, y, t))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                current?.lineTo(x, y)
                strokeBuilder?.addPoint(Ink.Point.create(x, y, t))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                current?.lineTo(x, y)
                strokeBuilder?.addPoint(Ink.Point.create(x, y, t))
                current?.let { paths.add(it) }
                current = null
                strokeBuilder?.let { inkBuilder.addStroke(it.build()) }
                strokeBuilder = null
                onStrokeEnd(inkBuilder.build())
            }
        }
        invalidate()
        return true
    }
}

class HandwritingWindow : InputWindow.ExtendedInputWindow<HandwritingWindow>() {

    private val windowManager: InputWindowManager by manager.must()
    private val commonKeyActionListener: CommonKeyActionListener by manager.must()
    private val theme by manager.theme()

    override val title: String get() = "手寫輸入"

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: DigitalInkRecognizer? = null
    private var modelReady = false

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics
    ).toInt()

    private fun rounded(color: Int, radius: Int = 12) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun button(label: String, accent: Boolean = false, onClick: () -> Unit) =
        Button(context).apply {
            text = label
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (accent) theme.accentKeyTextColor else theme.keyTextColor)
            background = rounded(if (accent) theme.accentKeyBackgroundColor else theme.keyBackgroundColor)
            stateListAnimator = null
            minHeight = 0
            minimumHeight = 0
            setOnClickListener { onClick() }
        }

    private val status by lazy {
        TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.altKeyTextColor)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 2
            text = "用手指在下面寫字"
        }
    }

    private val candidateRow by lazy {
        LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    }

    private val recognizeRunnable = Runnable { recognize() }
    private var lastInk: Ink? = null

    private val inkView by lazy {
        InkView(context, theme, onStrokeStart = {
            handler.removeCallbacks(recognizeRunnable)
        }, onStrokeEnd = {
            lastInk = it
            handler.postDelayed(recognizeRunnable, 650)
        }).apply {
            background = rounded(theme.keyBackgroundColor, 14)
        }
    }

    private val root by lazy {
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(8))
            // hint text shares the candidate row's height (shown only while no candidates)
            addView(
                FrameLayout(context).apply {
                    addView(status, FrameLayout.LayoutParams(-1, -1))
                    addView(HorizontalScrollView(context).apply {
                        isHorizontalScrollBarEnabled = false
                        addView(candidateRow)
                    }, FrameLayout.LayoutParams(-1, -1))
                },
                LinearLayout.LayoutParams(-1, dp(48))
            )
            addView(inkView, LinearLayout.LayoutParams(-1, 0, 1f).apply {
                topMargin = dp(6)
                bottomMargin = dp(8)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                fun add(v: View, w: Float) = addView(
                    v, LinearLayout.LayoutParams(0, dp(48), w).apply { marginStart = dp(6) }
                )
                add(button("清除") { resetInk() }, 1f)
                add(button("空白") { send(KeyAction.CommitAction(" ")) }, 1f)
                add(button("⌫") {
                    send(KeyAction.SymAction(KeySym(FcitxKeyMapping.FcitxKey_BackSpace)))
                }, 1f)
                add(button("換行") {
                    send(KeyAction.SymAction(KeySym(FcitxKeyMapping.FcitxKey_Return)))
                }, 1f)
                add(button("⌨ 回鍵盤", true) { windowManager.attachWindow(KeyboardWindow) }, 1.6f)
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }

    override fun onCreateView(): View = root

    private fun send(action: KeyAction) {
        commonKeyActionListener.listener.onKeyAction(action, KeyActionListener.Source.Keyboard)
    }

    private fun resetInk() {
        handler.removeCallbacks(recognizeRunnable)
        inkView.clearInk()
        lastInk = null
        candidateRow.removeAllViews()
        status.visibility = View.VISIBLE
    }

    private fun showCandidates(list: List<String>) {
        candidateRow.removeAllViews()
        status.visibility = if (list.isEmpty()) View.VISIBLE else View.INVISIBLE
        list.filter { org.fcitx.fcitx5.android.input.candidates.RareFonts.drawable(it) }.forEach { text ->
            candidateRow.addView(
                TextView(context).apply {
                    this.text = text
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                    setTextColor(theme.keyTextColor)
                    background = rounded(theme.keyBackgroundColor, 10)
                    setPadding(dp(16), 0, dp(16), 0)
                    minWidth = dp(52)
                    setOnClickListener {
                        send(KeyAction.CommitAction(text))
                        resetInk()
                    }
                },
                LinearLayout.LayoutParams(-2, -1).apply { marginEnd = dp(6) }
            )
        }
    }

    private fun recognize() {
        val ink = lastInk ?: return
        val r = recognizer
        if (r == null || !modelReady) {
            status.text = "字庫準備中，請稍候…"
            return
        }
        r.recognize(ink)
            .addOnSuccessListener { result ->
                showCandidates(result.candidates.map { it.text }.filter { it.isNotEmpty() })
            }
            .addOnFailureListener {
                status.text = "辨識失敗：${it.message ?: it.javaClass.simpleName}"
            }
    }

    private fun prepareModel() {
        if (recognizer != null) return
        try {
            val id = DigitalInkRecognitionModelIdentifier.fromLanguageTag("zh-Hant")
                ?: DigitalInkRecognitionModelIdentifier.fromLanguageTag("zh-Hant-TW")
            if (id == null) {
                status.text = "這台手機不支援手寫字庫"
                return
            }
            val model = DigitalInkRecognitionModel.builder(id).build()
            recognizer = DigitalInkRecognition.getClient(
                DigitalInkRecognizerOptions.builder(model).build()
            )
            val manager = RemoteModelManager.getInstance()
            manager.isModelDownloaded(model).addOnSuccessListener { downloaded ->
                if (downloaded) {
                    modelReady = true
                    status.text = "用手指在下面寫字，停一下出現候選字"
                } else {
                    status.text = "首次使用，下載手寫字庫中（約 20 MB）…"
                    manager.download(model, DownloadConditions.Builder().build())
                        .addOnSuccessListener {
                            modelReady = true
                            status.text = "字庫下載完成，可以開始寫字了"
                            if (lastInk != null) recognize()
                        }
                        .addOnFailureListener {
                            status.text = "字庫下載失敗，請確認網路後重開手寫：${it.message ?: ""}"
                        }
                }
            }.addOnFailureListener {
                status.text = "無法檢查手寫字庫：${it.message ?: it.javaClass.simpleName}"
            }
        } catch (e: Exception) {
            status.text = "手寫功能無法啟動：${e.message ?: e.javaClass.simpleName}"
        }
    }

    override fun onAttached() {
        resetInk()
        prepareModel()
    }

    override fun onDetached() {
        handler.removeCallbacks(recognizeRunnable)
    }
}
