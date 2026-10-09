/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SogaKey: a large-key romaji layout for Japanese input (Anthy / Mozc), with kana hints.
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Border
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Variant
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import splitties.views.imageResource

/** Latin letter big (what you type in romaji), hiragana hint small. */
class JpKey(letter: String, kana: String, percentWidth: Float) : KeyDef(
    Appearance.AltText(
        displayText = letter.uppercase(),
        altText = kana,
        textSize = 24f,
        percentWidth = percentWidth,
        border = Border.On
    ),
    setOf(Behavior.Press(KeyAction.FcitxKeyAction(letter))),
    arrayOf(Popup.Preview(letter))
)

/** Punctuation key: shows the Japanese mark, sends the ASCII key the engine converts. */
class JpPunctKey(shown: String, sent: String, percentWidth: Float) : KeyDef(
    Appearance.Text(
        displayText = shown,
        textSize = 22f,
        percentWidth = percentWidth,
        variant = Variant.Alternative,
        border = Border.On
    ),
    setOf(Behavior.Press(KeyAction.FcitxKeyAction(sent))),
    arrayOf(Popup.Preview(shown))
)

@SuppressLint("ViewConstructor")
class JapaneseKeyboard(
    context: Context,
    theme: Theme
) : BaseKeyboard(context, theme, Layout) {

    companion object {
        const val Name = "Japanese"

        fun isJapanese(imeName: String) = imeName == "anthy" || imeName.startsWith("mozc")

        val Layout: List<List<KeyDef>> = listOf(
            listOf(JpKey("q", "わ", 0.1f), JpKey("w", "を", 0.1f), JpKey("e", "え", 0.1f), JpKey("r", "ら", 0.1f), JpKey("t", "た", 0.1f), JpKey("y", "や", 0.1f), JpKey("u", "う", 0.1f), JpKey("i", "い", 0.1f), JpKey("o", "お", 0.1f), JpKey("p", "ぱ", 0.1f)),
            listOf(JpKey("a", "あ", 1f / 9f), JpKey("s", "さ", 1f / 9f), JpKey("d", "だ", 1f / 9f), JpKey("f", "ふ", 1f / 9f), JpKey("g", "が", 1f / 9f), JpKey("h", "は", 1f / 9f), JpKey("j", "じ", 1f / 9f), JpKey("k", "か", 1f / 9f), JpKey("l", "ろ", 1f / 9f)),
            listOf(JpKey("z", "ざ", 0.1f), JpKey("x", "ぁ", 0.1f), JpKey("c", "ち", 0.1f), JpKey("v", "ゔ", 0.1f), JpKey("b", "ば", 0.1f), JpKey("n", "な", 0.1f), JpKey("m", "ま", 0.1f), JpPunctKey("ー", "-", 0.1f), BackspaceKey(percentWidth = 0.16f)),
            listOf(
                LayoutSwitchKey("符號", "", percentWidth = 0.13f),
                ImagePickerSwitchKey(
                    R.drawable.ic_baseline_tag_faces_24,
                    PickerWindow.Key.Emoji,
                    percentWidth = 0.11f,
                    variant = Variant.Alternative
                ),
                LanguageKey(),
                JpPunctKey("、", ",", 0.1f),
                SpaceKey(),
                JpPunctKey("。", ".", 0.1f),
                ReturnKey(percentWidth = 0.15f)
            )
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
