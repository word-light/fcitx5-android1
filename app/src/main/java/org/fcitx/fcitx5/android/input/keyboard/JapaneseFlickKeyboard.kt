/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SogaKey: Japanese 12-key flick (フリック) keyboard. Every flick is turned into the
 * matching romaji keys, so it works with the Anthy engine.
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Border
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Variant
import splitties.views.imageResource

/** One flick target: what is shown and which keys to type. */
class FlickCell(val shown: String, val keys: String)

/** Kana cycles for the ゛゜小 key: the kana and the romaji that produces it. */
object KanaCycle {
    /** last kana typed by a flick, valid only until any other key is pressed */
    var lastKana: String? = null

    private fun c(vararg items: String) = items.map { it.split(":").let { p -> p[0] to p[1] } }

    private val cycles: List<List<Pair<String, String>>> = listOf(
        c("あ:a", "ぁ:xa"), c("い:i", "ぃ:xi"), c("う:u", "ぅ:xu", "ゔ:vu"), c("え:e", "ぇ:xe"), c("お:o", "ぉ:xo"),
        c("か:ka", "が:ga"), c("き:ki", "ぎ:gi"), c("く:ku", "ぐ:gu"), c("け:ke", "げ:ge"), c("こ:ko", "ご:go"),
        c("さ:sa", "ざ:za"), c("し:shi", "じ:ji"), c("す:su", "ず:zu"), c("せ:se", "ぜ:ze"), c("そ:so", "ぞ:zo"),
        c("た:ta", "だ:da"), c("ち:chi", "ぢ:di"), c("つ:tsu", "っ:xtu", "づ:du"), c("て:te", "で:de"), c("と:to", "ど:do"),
        c("は:ha", "ば:ba", "ぱ:pa"), c("ひ:hi", "び:bi", "ぴ:pi"), c("ふ:fu", "ぶ:bu", "ぷ:pu"),
        c("へ:he", "べ:be", "ぺ:pe"), c("ほ:ho", "ぼ:bo", "ぽ:po"),
        c("や:ya", "ゃ:xya"), c("ゆ:yu", "ゅ:xyu"), c("よ:yo", "ょ:xyo"), c("わ:wa", "ゎ:xwa")
    )

    /** next form of [kana] in its cycle, or null when it has none */
    fun next(kana: String): Pair<String, String>? {
        val cycle = cycles.firstOrNull { cy -> cy.any { it.first == kana } } ?: return null
        val i = cycle.indexOfFirst { it.first == kana }
        return cycle[(i + 1) % cycle.size]
    }
}

/**
 * A flick key. Tap = [center]; flick left / up / right / down = the other cells.
 * With [modifier] set it is the ゛゜小 key, which transforms the previously typed kana.
 */
class FlickKeyDef(
    val center: FlickCell,
    val left: FlickCell? = null,
    val up: FlickCell? = null,
    val right: FlickCell? = null,
    val down: FlickCell? = null,
    val modifier: Boolean = false,
    label: String = center.shown,
    percentWidth: Float = 0.22f
) : KeyDef(
    Appearance.Text(
        displayText = label,
        textSize = 24f,
        percentWidth = percentWidth,
        border = Border.On
    ),
    emptySet(),
    emptyArray()
)

private fun cell(shown: String, keys: String) = FlickCell(shown, keys)

private fun gyou(vararg p: String): FlickKeyDef {
    // p = kana1,romaji1, kana2,romaji2 ... (5 pairs: a i u e o)
    val cs = (0 until 5).map { cell(p[it * 2], p[it * 2 + 1]) }
    return FlickKeyDef(cs[0], cs[1], cs[2], cs[3], cs[4])
}

class ArrowKey(label: String, sym: Int, percentWidth: Float) : KeyDef(
    Appearance.Text(
        displayText = label,
        textSize = 20f,
        percentWidth = percentWidth,
        variant = Variant.Alternative,
        border = Border.On
    ),
    setOf(Behavior.Press(KeyAction.SymAction(KeySym(sym)))),
    emptyArray()
)

@SuppressLint("ViewConstructor")
class JapaneseFlickKeyboard(
    context: Context,
    theme: Theme
) : BaseKeyboard(context, theme, Layout) {

    companion object {
        const val Name = "JapaneseFlick"

        private val A = gyou("あ", "a", "い", "i", "う", "u", "え", "e", "お", "o")
        private val KA = gyou("か", "ka", "き", "ki", "く", "ku", "け", "ke", "こ", "ko")
        private val SA = gyou("さ", "sa", "し", "shi", "す", "su", "せ", "se", "そ", "so")
        private val TA = gyou("た", "ta", "ち", "chi", "つ", "tsu", "て", "te", "と", "to")
        private val NA = gyou("な", "na", "に", "ni", "ぬ", "nu", "ね", "ne", "の", "no")
        private val HA = gyou("は", "ha", "ひ", "hi", "ふ", "fu", "へ", "he", "ほ", "ho")
        private val MA = gyou("ま", "ma", "み", "mi", "む", "mu", "め", "me", "も", "mo")
        private val YA = FlickKeyDef(
            cell("や", "ya"), cell("「", "["), cell("ゆ", "yu"), cell("」", "]"), cell("よ", "yo")
        )
        private val RA = gyou("ら", "ra", "り", "ri", "る", "ru", "れ", "re", "ろ", "ro")
        private val WA = FlickKeyDef(
            cell("わ", "wa"), cell("を", "wo"), cell("ん", "nn"), cell("ー", "-"), null
        )
        private val PUNCT = FlickKeyDef(
            cell("、", ","), cell("。", "."), cell("？", "?"), cell("！", "!"), cell("・", "/"),
            label = "、。？！"
        )
        private val MOD = FlickKeyDef(cell("", ""), modifier = true, label = "゛゜小")

        private const val SIDE = 0.16f
        private const val SIDE_R = 0.18f

        val Layout: List<List<KeyDef>> = listOf(
            listOf(LayoutSwitchKey("符號", "", percentWidth = SIDE), A, KA, SA, BackspaceKey(percentWidth = SIDE_R)),
            listOf(LayoutSwitchKey("ABC", "Japanese", percentWidth = SIDE), TA, NA, HA,
                ArrowKey("→", FcitxKeyMapping.FcitxKey_Right, SIDE_R)),
            listOf(ArrowKey("←", FcitxKeyMapping.FcitxKey_Left, SIDE), MA, YA, RA, SpaceKey()),
            listOf(LanguageKey(), MOD, WA, PUNCT, ReturnKey(percentWidth = SIDE_R))
        )
    }

    private val space: TextKeyView by lazy { findViewById(R.id.button_space) }
    private val `return`: ImageKeyView by lazy { findViewById(R.id.button_return) }

    override fun onReturnDrawableUpdate(returnDrawable: Int) {
        `return`.img.imageResource = returnDrawable
    }

    override fun onInputMethodUpdate(ime: InputMethodEntry) {
        space.mainText.text = ime.displayName
    }
}
