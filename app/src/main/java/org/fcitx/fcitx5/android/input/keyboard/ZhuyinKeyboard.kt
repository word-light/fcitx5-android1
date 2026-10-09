/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SogaKey: a large-key Zhuyin (Bopomofo) layout for the Chewing engine.
 *
 * Each key shows a Zhuyin symbol but sends the standard (大千) keyboard key that
 * libchewing expects, e.g. ㄅ -> "1", ㄆ -> "q", ㄇ -> "a".
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Border
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Variant
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import splitties.views.imageResource

/** A key that displays [zhuyin] but sends [key] to the Chewing engine. */
class ZhuyinKey(
    zhuyin: String,
    key: String,
    percentWidth: Float,
    textSize: Float = 22f,
    /** typed directly (bypassing Chewing) when the key is swiped; also drawn as a small hint */
    alt: String? = null
) : KeyDef(
    if (alt == null) Appearance.Text(
        displayText = zhuyin,
        textSize = textSize,
        percentWidth = percentWidth,
        border = Border.On
    ) else Appearance.AltText(
        displayText = zhuyin,
        altText = alt,
        textSize = textSize,
        percentWidth = percentWidth,
        border = Border.On
    ),
    if (alt == null) setOf(
        Behavior.Press(KeyAction.FcitxKeyAction(key))
    ) else setOf(
        Behavior.Press(KeyAction.FcitxKeyAction(key)),
        Behavior.Swipe(KeyAction.CommitAction(alt))
    ),
    arrayOf(
        Popup.Preview(zhuyin)
    )
)

/** Commits full-width punctuation directly, bypassing Chewing's key mapping. */
class DirectPunctKey(
    text: String,
    percentWidth: Float,
    popupItems: Array<String>
) : KeyDef(
    Appearance.Text(
        displayText = text,
        textSize = 22f,
        percentWidth = percentWidth,
        variant = Variant.Alternative,
        border = Border.On
    ),
    setOf(
        Behavior.Press(KeyAction.SymAction(KeySym(0x1000000 + text.codePointAt(0))))
    ),
    arrayOf(
        Popup.Preview(text),
        Popup.Keyboard.Explicit(popupItems)
    )
)

@SuppressLint("ViewConstructor")
class ZhuyinKeyboard(
    context: Context,
    theme: Theme
) : BaseKeyboard(context, theme, Layout) {

    companion object {
        const val Name = "Zhuyin"

        /** Unique name of the Chewing input method in fcitx5-chewing. */
        const val ChewingIme = "chewing"

        private const val W11 = 1f / 11f   // first row has 11 keys
        private const val W10 = 0.1f       // rows with 10 keys
        private const val W4 = 0.085f      // last Zhuyin row, leaves room for backspace

        val Layout: List<List<KeyDef>> = listOf(
            listOf(
                ZhuyinKey("ㄅ", "1", W11, alt = "1"), ZhuyinKey("ㄉ", "2", W11, alt = "2"),
                ZhuyinKey("ˇ", "3", W11, 24f, alt = "3"), ZhuyinKey("ˋ", "4", W11, 24f, alt = "4"),
                ZhuyinKey("ㄓ", "5", W11, alt = "5"), ZhuyinKey("ˊ", "6", W11, 24f, alt = "6"),
                ZhuyinKey("˙", "7", W11, 24f, alt = "7"), ZhuyinKey("ㄚ", "8", W11, alt = "8"),
                ZhuyinKey("ㄞ", "9", W11, alt = "9"), ZhuyinKey("ㄢ", "0", W11, alt = "0"),
                ZhuyinKey("ㄦ", "-", W11, alt = "-")
            ),
            listOf(
                ZhuyinKey("ㄆ", "q", W10, alt = "！"), ZhuyinKey("ㄊ", "w", W10, alt = "？"),
                ZhuyinKey("ㄍ", "e", W10, alt = "～"), ZhuyinKey("ㄐ", "r", W10, alt = "…"),
                ZhuyinKey("ㄔ", "t", W10, alt = "；"), ZhuyinKey("ㄗ", "y", W10, alt = "："),
                ZhuyinKey("ㄧ", "u", W10, alt = "「"), ZhuyinKey("ㄛ", "i", W10, alt = "」"),
                ZhuyinKey("ㄟ", "o", W10, alt = "（"), ZhuyinKey("ㄣ", "p", W10, alt = "）")
            ),
            listOf(
                ZhuyinKey("ㄇ", "a", W10, alt = "@"), ZhuyinKey("ㄋ", "s", W10, alt = "#"),
                ZhuyinKey("ㄎ", "d", W10, alt = "$"), ZhuyinKey("ㄑ", "f", W10, alt = "%"),
                ZhuyinKey("ㄕ", "g", W10, alt = "&"), ZhuyinKey("ㄘ", "h", W10, alt = "*"),
                ZhuyinKey("ㄨ", "j", W10, alt = "+"), ZhuyinKey("ㄜ", "k", W10, alt = "="),
                ZhuyinKey("ㄠ", "l", W10, alt = "/"), ZhuyinKey("ㄤ", ";", W10, alt = "\\")
            ),
            listOf(
                ZhuyinKey("ㄈ", "z", W4, alt = "_"), ZhuyinKey("ㄌ", "x", W4, alt = "\'"),
                ZhuyinKey("ㄏ", "c", W4, alt = "\""), ZhuyinKey("ㄒ", "v", W4, alt = "`"),
                ZhuyinKey("ㄖ", "b", W4, alt = "^"), ZhuyinKey("ㄙ", "n", W4, alt = "|"),
                ZhuyinKey("ㄩ", "m", W4, alt = "<"), ZhuyinKey("ㄝ", ",", W4, alt = ">"),
                ZhuyinKey("ㄡ", ".", W4, alt = "["), ZhuyinKey("ㄥ", "/", W4, alt = "]"),
                BackspaceKey(percentWidth = 0.15f)
            ),
            listOf(
                LayoutSwitchKey("符號", "", percentWidth = 0.13f),
                ImagePickerSwitchKey(
                    R.drawable.ic_baseline_tag_faces_24,
                    PickerWindow.Key.Emoji,
                    percentWidth = 0.11f,
                    variant = Variant.Alternative
                ),
                LanguageKey(),
                DirectPunctKey("，", 0.1f, arrayOf("、", "；", "：", "「", "」", "（", "）")),
                SpaceKey(),
                DirectPunctKey("。", 0.1f, arrayOf("？", "！", "…", "～", "『", "』")),
                ReturnKey(percentWidth = 0.15f)
            )
        )
    }

    private val space: TextKeyView by lazy { findViewById(R.id.button_space) }
    private val `return`: ImageKeyView by lazy { findViewById(R.id.button_return) }

    override fun onAction(action: KeyAction, source: KeyActionListener.Source) {
        // Full-width punctuation picked from a popup must not go through Chewing's key mapping
        if (source == KeyActionListener.Source.Popup &&
            action is KeyAction.FcitxKeyAction &&
            action.act.any { it.code > 0x7f }
        ) {
            val cp = action.act.codePointAt(0)
            if (Character.charCount(cp) == action.act.length) {
                // goes through Chewing so a half-typed phrase is committed first
                super.onAction(KeyAction.SymAction(KeySym(0x1000000 + cp)), source)
            } else {
                super.onAction(KeyAction.CommitAction(action.act), source)
            }
            return
        }
        super.onAction(action, source)
    }

    override fun onReturnDrawableUpdate(returnDrawable: Int) {
        `return`.img.imageResource = returnDrawable
    }

    override fun onInputMethodUpdate(ime: InputMethodEntry) {
        space.mainText.text = ime.displayName
    }
}
