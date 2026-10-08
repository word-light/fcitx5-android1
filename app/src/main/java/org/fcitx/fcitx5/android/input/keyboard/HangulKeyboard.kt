/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SogaKey: a large-key Korean (Dubeolsik) layout for the Hangul engine.
 *
 * Each key shows a jamo but sends the matching QWERTY key; swiping up on a key
 * that has a double/tense form (ㅂ -> ㅃ) sends the shifted key.
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

/** Shows [jamo], sends [key]; swipe sends [shiftKey] and shows [shiftJamo] as the small hint. */
class JamoKey(
    jamo: String,
    key: String,
    percentWidth: Float,
    shiftJamo: String? = null,
    shiftKey: String? = null
) : KeyDef(
    if (shiftJamo != null) {
        Appearance.AltText(
            displayText = jamo,
            altText = shiftJamo,
            textSize = 23f,
            percentWidth = percentWidth,
            border = Border.On
        )
    } else {
        Appearance.Text(
            displayText = jamo,
            textSize = 23f,
            percentWidth = percentWidth,
            border = Border.On
        )
    },
    if (shiftKey != null) {
        setOf(
            Behavior.Press(KeyAction.FcitxKeyAction(key)),
            Behavior.Swipe(KeyAction.FcitxKeyAction(shiftKey))
        )
    } else {
        setOf(Behavior.Press(KeyAction.FcitxKeyAction(key)))
    },
    if (shiftJamo != null) {
        arrayOf(Popup.AltPreview(jamo, shiftJamo))
    } else {
        arrayOf(Popup.Preview(jamo))
    }
)

/** A plain ASCII punctuation key that goes through the Hangul engine. */
class AsciiPunctKey(text: String, percentWidth: Float) : KeyDef(
    Appearance.Text(
        displayText = text,
        textSize = 22f,
        percentWidth = percentWidth,
        variant = Variant.Alternative,
        border = Border.On
    ),
    setOf(Behavior.Press(KeyAction.FcitxKeyAction(text))),
    arrayOf(Popup.Preview(text))
)

@SuppressLint("ViewConstructor")
class HangulKeyboard(
    context: Context,
    theme: Theme
) : BaseKeyboard(context, theme, Layout) {

    companion object {
        const val Name = "Hangul"

        /** Unique name of the Hangul input method in fcitx5-hangul. */
        const val HangulIme = "hangul"

        private const val W = 0.1f

        val Layout: List<List<KeyDef>> = listOf(
            listOf(
                JamoKey("ㅂ", "q", W, "ㅃ", "Q"), JamoKey("ㅈ", "w", W, "ㅉ", "W"),
                JamoKey("ㄷ", "e", W, "ㄸ", "E"), JamoKey("ㄱ", "r", W, "ㄲ", "R"),
                JamoKey("ㅅ", "t", W, "ㅆ", "T"), JamoKey("ㅛ", "y", W),
                JamoKey("ㅕ", "u", W), JamoKey("ㅑ", "i", W),
                JamoKey("ㅐ", "o", W, "ㅒ", "O"), JamoKey("ㅔ", "p", W, "ㅖ", "P")
            ),
            listOf(
                JamoKey("ㅁ", "a", 1f / 9f), JamoKey("ㄴ", "s", 1f / 9f),
                JamoKey("ㅇ", "d", 1f / 9f), JamoKey("ㄹ", "f", 1f / 9f),
                JamoKey("ㅎ", "g", 1f / 9f), JamoKey("ㅗ", "h", 1f / 9f),
                JamoKey("ㅓ", "j", 1f / 9f), JamoKey("ㅏ", "k", 1f / 9f),
                JamoKey("ㅣ", "l", 1f / 9f)
            ),
            listOf(
                JamoKey("ㅋ", "z", 0.12f), JamoKey("ㅌ", "x", 0.12f),
                JamoKey("ㅊ", "c", 0.12f), JamoKey("ㅍ", "v", 0.12f),
                JamoKey("ㅠ", "b", 0.12f), JamoKey("ㅜ", "n", 0.12f),
                JamoKey("ㅡ", "m", 0.12f),
                BackspaceKey(percentWidth = 0.16f)
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
                AsciiPunctKey(",", 0.1f),
                SpaceKey(),
                AsciiPunctKey(".", 0.1f),
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
