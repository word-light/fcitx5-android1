/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SogaKey: cross-shaped popup shown while flicking a Japanese kana key.
 */
package org.fcitx.fcitx5.android.input.popup

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.TextView
import org.fcitx.fcitx5.android.data.theme.Theme

/**
 * Draws the center cell on top of the key and the other cells left / above / right / below it.
 * [cells] order: center, left, up, right, down.
 */
class FlickPopupUi(
    val ctx: Context,
    private val theme: Theme,
    cells: Array<String?>,
    private val cellW: Int,
    private val cellH: Int,
    private val radius: Float
) {
    val root = FrameLayout(ctx).apply {
        clipChildren = false
        clipToPadding = false
    }

    private val views = arrayOfNulls<TextView>(5)

    // column / row offsets (in cells) relative to the key, per cell index
    private val dx = intArrayOf(0, -1, 0, 1, 0)
    private val dy = intArrayOf(0, 0, -1, 0, 1)

    init {
        for (i in 0 until 5) {
            val text = cells[i] ?: continue
            val tv = TextView(ctx).apply {
                this.text = text
                textSize = 24f
                gravity = Gravity.CENTER
                elevation = ctx.resources.displayMetrics.density * 3f
                outlineProvider = ViewOutlineProvider.BACKGROUND
            }
            root.addView(tv, FrameLayout.LayoutParams(cellW, cellH).apply {
                leftMargin = dx[i] * cellW
                topMargin = dy[i] * cellH
            })
            views[i] = tv
        }
        focus(0)
    }

    fun focus(index: Int) {
        for (i in 0 until 5) {
            val tv = views[i] ?: continue
            val on = i == index
            tv.background = GradientDrawable().apply {
                cornerRadius = radius
                setColor(if (on) theme.accentKeyBackgroundColor else theme.popupBackgroundColor)
            }
            tv.setTextColor(if (on) theme.accentKeyTextColor else theme.popupTextColor)
        }
    }
}
