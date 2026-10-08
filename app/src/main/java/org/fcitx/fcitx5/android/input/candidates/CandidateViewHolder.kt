/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024-2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates

import android.graphics.Paint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.fcitx.fcitx5.android.core.CandidateWord

class CandidateViewHolder(val ui: CandidateItemUi) : RecyclerView.ViewHolder(ui.root) {
    var idx = -1
        private set

    var candidate: CandidateWord = CandidateWord.Empty
        private set

    fun update(newIndex: Int, newCandidate: CandidateWord) {
        idx = newIndex
        if (candidate != newCandidate) {
            candidate = newCandidate
            ui.updateCandidate(newCandidate)
        }
        // SogaKey: hide rare characters the phone's fonts cannot draw (shown as empty boxes)
        val t = newCandidate.text
        val drawable = t.isEmpty() || t.all { it.code < 0x2E80 } || glyphPaint.hasGlyph(t)
        itemView.visibility = if (drawable) View.VISIBLE else View.GONE
    }

    fun clear() {
        update(-1, CandidateWord.Empty)
    }

    companion object {
        private val glyphPaint = Paint()
    }
}
