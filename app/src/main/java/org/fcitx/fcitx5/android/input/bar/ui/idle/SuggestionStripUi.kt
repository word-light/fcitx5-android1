/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SogaKey: suggests ending punctuation / emoji after a sentence.
 */
package org.fcitx.fcitx5.android.input.bar.ui.idle

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import org.fcitx.fcitx5.android.data.theme.Theme

class SuggestionStripUi(
    context: Context,
    private val theme: Theme
) : HorizontalScrollView(context) {

    var onPick: ((String) -> Unit)? = null

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    init {
        isHorizontalScrollBarEnabled = false
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    fun show(items: List<String>) {
        row.removeAllViews()
        scrollTo(0, 0)
        items.forEach { text ->
            row.addView(
                TextView(context).apply {
                    this.text = text
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    setTextColor(theme.keyTextColor)
                    background = GradientDrawable().apply {
                        setColor(theme.keyBackgroundColor)
                        cornerRadius = dp(10).toFloat()
                    }
                    minWidth = dp(44)
                    setPadding(dp(10), 0, dp(10), 0)
                    setOnClickListener { onPick?.invoke(text) }
                },
                LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(34)).apply {
                    marginStart = dp(6)
                    marginEnd = dp(2)
                }
            )
        }
    }
}
