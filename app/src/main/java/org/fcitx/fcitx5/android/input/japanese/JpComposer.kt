/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SogaKey: kana buffer + Sumire conversion for the Japanese flick keyboard.
 * While [enabled], flick keys fill [reading]; the composing text and candidate bar are driven
 * from here (fcitx / Anthy stays idle).
 */
package org.fcitx.fcitx5.android.input.japanese

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.keyboard.KanaCycle
import androidx.lifecycle.lifecycleScope
import com.kazumaproject.markdownhelperkeyboard.learning.session.ConversionLearningSession
import com.kazumaproject.markdownhelperkeyboard.learning.session.LearningFragment
import org.fcitx.fcitx5.android.core.CandidateWord
import timber.log.Timber

object JpComposer {

    /** true while the flick keyboard is the visible layout */
    @Volatile
    var enabled = false

    var service: FcitxInputMethodService? = null

    private var reading = ""
    private var cands: List<Candidate> = emptyList()

    /** -1 = showing the plain reading, otherwise the highlighted candidate (space cycles it) */
    private var sel = -1
    private var job: Job? = null
    private val session = ConversionLearningSession()

    /** page of candidates for the expanded candidate window */
    fun candidateWords(start: Int, count: Int): Array<CandidateWord> =
        cands.drop(start).take(count).map { CandidateWord("", it.string, "") }.toTypedArray()

    private fun recordLearning(idx: Int, c: Candidate, usedReading: String, startReading: String, rest: String) {
        session.beginIfNeeded(startReading)
        session.record(
            LearningFragment(
                reading = usedReading,
                output = c.commitText,
                candidateScore = c.score,
                candidateIndex = idx,
                leftId = c.leftId,
                rightId = c.rightId,
                explicitlySelected = true,
            )
        )
        if (rest.isEmpty()) {
            val entries = session.finish(learnFirstCandidate = true)
            val s = service
            if (entries.isNotEmpty() && s != null) {
                s.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        SogaJpEngine.learn(entries)
                    } catch (e: Throwable) {
                        Timber.e(e, "saving learning failed")
                    }
                }
            }
        }
    }

    val active: Boolean get() = enabled && reading.isNotEmpty()

    private fun render() {
        val s = service ?: return
        val shown = if (sel in cands.indices) {
            val c = cands[sel]
            c.commitText + reading.drop(c.length.toInt())
        } else reading
        s.jpCompose(shown, reading, cands.map { it.string })
    }

    private fun convert() {
        job?.cancel()
        val s = service ?: return
        val r = reading
        if (r.isEmpty()) {
            cands = emptyList()
            sel = -1
            render()
            return
        }
        job = s.lifecycleScope.launch {
            val list = try {
                withContext(Dispatchers.Default) { SogaJpEngine.convert(s, r) }
            } catch (e: Throwable) {
                Timber.e(e, "Sumire conversion failed")
                emptyList()
            }
            if (r == reading) {
                cands = list
                sel = -1
                render()
            }
        }
    }

    /** a kana (or punctuation) cell was flicked */
    fun input(text: String) {
        if (text.isEmpty()) return
        reading += text
        sel = -1
        render()
        convert()
    }

    /** ゛゜小: cycle the last kana */
    fun modifyLast() {
        val last = reading.lastOrNull()?.toString() ?: return
        val next = KanaCycle.next(last) ?: return
        reading = reading.dropLast(1) + next.first
        sel = -1
        render()
        convert()
    }

    /** @return true when the key was consumed */
    fun backspace(): Boolean {
        if (!active) return false
        if (sel >= 0) {
            sel = -1
            render()
        } else {
            reading = reading.dropLast(1)
            render()
            convert()
        }
        return true
    }

    /** space: highlight the next candidate */
    fun space(): Boolean {
        if (!active) return false
        if (cands.isEmpty()) return true
        sel = if (sel < 0) 0 else (sel + 1) % cands.size
        render()
        return true
    }

    /** return: commit what is shown */
    fun enter(): Boolean {
        if (!active) return false
        flush()
        return true
    }

    /** candidate bar tap */
    fun select(idx: Int) {
        val c = cands.getOrNull(idx) ?: return
        val s = service ?: return
        val used = c.length.toInt().coerceIn(1, reading.length)
        val rest = reading.drop(used)
        recordLearning(idx, c, reading.take(used), reading, rest)
        s.commitText(c.commitText)
        reading = rest
        sel = -1
        if (rest.isEmpty()) {
            cands = emptyList()
            render()
        } else {
            render()
            convert()
        }
    }

    /** commit the current selection (or the plain reading) and clear */
    fun flush() {
        val s = service ?: return
        if (reading.isEmpty()) return
        val text = if (sel in cands.indices) {
            val c = cands[sel]
            c.commitText + reading.drop(c.length.toInt())
        } else reading
        job?.cancel()
        if (sel in cands.indices) {
            val c = cands[sel]
            val used = c.length.toInt().coerceIn(1, reading.length)
            recordLearning(sel, c, reading.take(used), reading, reading.drop(used))
        }
        session.cancel()
        reading = ""
        cands = emptyList()
        sel = -1
        s.commitText(text)
        s.jpCompose("", "", emptyList())
    }

    /** the app moved the cursor / focus changed: drop everything without committing */
    fun reset() {
        session.cancel()
        job?.cancel()
        reading = ""
        cands = emptyList()
        sel = -1
        service?.jpCompose("", "", emptyList(), updateText = false)
    }
}
