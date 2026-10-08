/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SogaKey: a large-key Cangjie layout showing the radicals.
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

/** Shows the radical big and the Latin letter small, sends the letter. */
class CangjieKey(radical: String, letter: String, percentWidth: Float) : KeyDef(
    Appearance.AltText(
        displayText = radical,
        altText = letter.uppercase(),
        textSize = 24f,
        percentWidth = percentWidth,
        border = Border.On
    ),
    setOf(Behavior.Press(KeyAction.FcitxKeyAction(letter))),
    arrayOf(Popup.Preview(radical))
)

@SuppressLint("ViewConstructor")
class CangjieKeyboard(
    context: Context,
    theme: Theme
) : BaseKeyboard(context, theme, Layout) {

    companion object {
        const val Name = "Cangjie"

        fun isCangjie(imeName: String) =
            imeName.startsWith("cangjie") || imeName.startsWith("quick")

        val Layout: List<List<KeyDef>> = listOf(
            listOf(CangjieKey("手", "q", 0.1f), CangjieKey("田", "w", 0.1f), CangjieKey("水", "e", 0.1f), CangjieKey("口", "r", 0.1f), CangjieKey("廿", "t", 0.1f), CangjieKey("卜", "y", 0.1f), CangjieKey("山", "u", 0.1f), CangjieKey("戈", "i", 0.1f), CangjieKey("人", "o", 0.1f), CangjieKey("心", "p", 0.1f)),
            listOf(CangjieKey("日", "a", 1f / 9f), CangjieKey("尸", "s", 1f / 9f), CangjieKey("木", "d", 1f / 9f), CangjieKey("火", "f", 1f / 9f), CangjieKey("土", "g", 1f / 9f), CangjieKey("竹", "h", 1f / 9f), CangjieKey("十", "j", 1f / 9f), CangjieKey("大", "k", 1f / 9f), CangjieKey("中", "l", 1f / 9f)),
            listOf(CangjieKey("重", "z", 0.12f), CangjieKey("難", "x", 0.12f), CangjieKey("金", "c", 0.12f), CangjieKey("女", "v", 0.12f), CangjieKey("月", "b", 0.12f), CangjieKey("弓", "n", 0.12f), CangjieKey("一", "m", 0.12f), BackspaceKey(percentWidth = 0.16f)),
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
