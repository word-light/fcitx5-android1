/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024-2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates

import android.graphics.Paint
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
        // expanded list: characters the phone cannot draw are made invisible (never GONE:
        // FlexboxLayoutManager crashes with hidden children)
        itemView.alpha = if (RareFonts.drawable(newCandidate.text)) 1f else 0f
    }

    fun clear() {
        update(-1, CandidateWord.Empty)
    }
}

/** Is [text] drawable with the phone's own fonts? (what other apps can show, too) */
object RareFonts {
    private val paint = Paint()
    private val cache = object : LinkedHashMap<Int, Boolean>(512, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<Int, Boolean>) = size > 4000
    }

    fun drawable(t: String): Boolean {
        if (t.isEmpty()) return true
        // Paint.hasGlyph(String) is only true for a single glyph, so test each Han character
        return t.codePoints().allMatch { cp ->
            val han = cp in 0x3400..0x9FFF || cp in 0xF900..0xFAFF || cp in 0x20000..0x3FFFF
            !han || cache.getOrPut(cp) { paint.hasGlyph(String(Character.toChars(cp))) }
        }
    }
}
