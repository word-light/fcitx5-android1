/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SogaKey: settings for AI voice input (stored separately from Fcitx prefs).
 */
package org.fcitx.fcitx5.android.input.ai

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit

object AiVoicePrefs {
    private const val FILE = "sogakey_ai"
    const val DEFAULT_MODEL = "gemini-flash-latest"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun apiKey(ctx: Context): String = sp(ctx).getString("api_key", "")!!.trim()
    fun setApiKey(ctx: Context, v: String) = sp(ctx).edit { putString("api_key", v.trim()) }

    /** Optional: Groq key for fast Whisper transcription. Empty = use Gemini for everything. */
    fun groqKey(ctx: Context): String = sp(ctx).getString("groq_key", "")!!.trim()
    fun setGroqKey(ctx: Context, v: String) = sp(ctx).edit { putString("groq_key", v.trim()) }

    fun model(ctx: Context): String =
        sp(ctx).getString("model", "")!!.trim().ifEmpty { DEFAULT_MODEL }

    fun setModel(ctx: Context, v: String) = sp(ctx).edit { putString("model", v.trim()) }

    /** When true, the window starts listening as soon as the mic button is pressed. */
    fun autoStart(ctx: Context): Boolean = sp(ctx).getBoolean("auto_start", true)
    fun setAutoStart(ctx: Context, v: Boolean) = sp(ctx).edit { putBoolean("auto_start", v) }

    fun hasMicPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
}
