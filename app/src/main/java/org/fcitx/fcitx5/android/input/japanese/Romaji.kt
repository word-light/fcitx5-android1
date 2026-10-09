/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SogaKey: small romaji -> hiragana table used by the ABC (romaji) Japanese layout.
 */
package org.fcitx.fcitx5.android.input.japanese

object Romaji {
    private val table: Map<String, String> = buildMap {
        fun row(cons: String, vararg kana: String) {
            val v = listOf("a", "i", "u", "e", "o")
            for (i in 0 until 5) if (kana[i].isNotEmpty()) put(cons + v[i], kana[i])
        }
        row("", "あ", "い", "う", "え", "お")
        row("k", "か", "き", "く", "け", "こ"); row("g", "が", "ぎ", "ぐ", "げ", "ご")
        row("s", "さ", "し", "す", "せ", "そ"); row("z", "ざ", "じ", "ず", "ぜ", "ぞ")
        row("t", "た", "ち", "つ", "て", "と"); row("d", "だ", "ぢ", "づ", "で", "ど")
        row("n", "な", "に", "ぬ", "ね", "の"); row("h", "は", "ひ", "ふ", "へ", "ほ")
        row("b", "ば", "び", "ぶ", "べ", "ぼ"); row("p", "ぱ", "ぴ", "ぷ", "ぺ", "ぽ")
        row("m", "ま", "み", "む", "め", "も"); row("y", "や", "", "ゆ", "", "よ")
        row("r", "ら", "り", "る", "れ", "ろ"); row("w", "わ", "うぃ", "う", "うぇ", "を")
        row("v", "ゔぁ", "ゔぃ", "ゔ", "ゔぇ", "ゔぉ"); row("f", "ふぁ", "ふぃ", "ふ", "ふぇ", "ふぉ")
        row("j", "じゃ", "じ", "じゅ", "じぇ", "じょ"); row("c", "か", "し", "く", "せ", "こ")
        row("l", "ぁ", "ぃ", "ぅ", "ぇ", "ぉ"); row("x", "ぁ", "ぃ", "ぅ", "ぇ", "ぉ")
        row("q", "くぁ", "くぃ", "く", "くぇ", "くぉ")
        for ((c, k) in listOf(
            "ky" to "き", "gy" to "ぎ", "sh" to "し", "sy" to "し", "zy" to "じ", "ch" to "ち", "ty" to "ち",
            "cy" to "ち", "dy" to "ぢ", "ny" to "に", "hy" to "ひ", "by" to "び", "py" to "ぴ", "my" to "み",
            "ry" to "り", "ts" to "つ", "th" to "て", "dh" to "で", "wh" to "う"
        )) {
            put(c + "a", if (c == "ts") "つぁ" else if (c == "th") "てゃ" else if (c == "dh") "でゃ" else if (c == "wh") "うぁ" else k + "ゃ")
            put(c + "i", if (c == "sh" || c == "ch") k else if (c == "ts") "つぃ" else if (c == "th") "てぃ" else if (c == "dh") "でぃ" else if (c == "wh") "うぃ" else k + "ぃ")
            put(c + "u", if (c == "ts") "つ" else if (c == "th") "てゅ" else if (c == "dh") "でゅ" else if (c == "wh") "う" else k + "ゅ")
            put(c + "e", if (c == "ts") "つぇ" else if (c == "wh") "うぇ" else if (c == "th") "てぇ" else if (c == "dh") "でぇ" else k + "ぇ")
            put(c + "o", if (c == "ts") "つぉ" else if (c == "wh") "うぉ" else if (c == "th") "てょ" else if (c == "dh") "でょ" else k + "ょ")
        }
        for ((c, k) in listOf("xy" to "", "ly" to "")) {
            put(c + "a", "ゃ"); put(c + "i", "ぃ"); put(c + "u", "ゅ"); put(c + "e", "ぇ"); put(c + "o", "ょ")
        }
        put("xtu", "っ"); put("xtsu", "っ"); put("ltu", "っ"); put("ltsu", "っ")
        put("xwa", "ゎ"); put("lwa", "ゎ"); put("nn", "ん"); put("n'", "ん")
        put("-", "ー"); put(",", "、"); put(".", "。"); put("?", "？"); put("!", "！"); put("/", "・")
        put("[", "「"); put("]", "」")
    }

    private val prefixes: Set<String> = buildSet { for (k in table.keys) for (i in 1 until k.length) add(k.substring(0, i)) }
    private const val vowels = "aiueo"

    /** Converts as much of [pending] as possible. @return converted kana to the left and the unfinished rest. */
    fun feed(pending: String): Pair<String, String> {
        val out = StringBuilder()
        var p = pending
        while (p.isNotEmpty()) {
            val k = table[p]
            if (k != null && p !in prefixes) { out.append(k); p = ""; continue }
            if (p in prefixes) break
            if (k != null) { out.append(k); p = ""; continue }
            // no match: try shorter prefix handling
            if (p.length >= 2 && p[0] == p[1] && p[0] !in vowels && p[0] != 'n') {
                out.append("っ"); p = p.substring(1); continue
            }
            if (p[0] == 'n' && p.length >= 2 && p[1] !in vowels && p[1] != 'y' && p[1] != 'n' && p[1] != '\'') {
                out.append("ん"); p = p.substring(1); continue
            }
            // drop the first char as a literal and retry with the rest
            out.append(p[0]); p = p.substring(1)
        }
        return out.toString() to p
    }
}
