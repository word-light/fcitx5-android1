/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SogaKey: records 16 kHz mono PCM from the microphone and packs it as WAV.
 */
package org.fcitx.fcitx5.android.input.ai

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class WavRecorder {

    companion object {
        const val SAMPLE_RATE = 16000
        const val MAX_SECONDS = 120
        private const val MAX_BYTES = SAMPLE_RATE * 2 * MAX_SECONDS

        fun toWav(data: ByteArray): ByteArray {
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
                put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
                putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
                put("data".toByteArray()); putInt(data.size)
            }
            return header.array() + data
        }
    }

    @Volatile
    private var running = false
    private var thread: Thread? = null
    private val pcm = ByteArrayOutputStream()

    /** Latest loudness 0..1, for the level indicator. */
    @Volatile
    var level: Float = 0f
        private set

    @Volatile
    var reachedLimit = false
        private set

    val seconds: Int get() = pcm.size() / (SAMPLE_RATE * 2)

    @SuppressLint("MissingPermission") // checked by caller
    fun start() {
        if (running) return
        pcm.reset()
        reachedLimit = false
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(minBuf, SAMPLE_RATE / 5 * 2)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("麥克風無法啟動，可能被其他 App 使用中")
        }
        running = true
        record.startRecording()
        thread = Thread {
            val buf = ByteArray(bufSize)
            try {
                while (running) {
                    val n = record.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    synchronized(pcm) { pcm.write(buf, 0, n) }
                    var peak = 0
                    var i = 0
                    while (i + 1 < n) {
                        val s = (buf[i].toInt() and 0xff) or (buf[i + 1].toInt() shl 8)
                        peak = maxOf(peak, abs(s.toShort().toInt()))
                        i += 2
                    }
                    level = (peak / 32768f).coerceIn(0f, 1f)
                    if (pcm.size() >= MAX_BYTES) {
                        reachedLimit = true
                        running = false
                    }
                }
            } finally {
                runCatching { record.stop() }
                record.release()
            }
        }.apply { name = "SogaKeyRecorder"; start() }
    }

    /** Stops recording and returns a complete WAV file. */
    fun stop(): ByteArray {
        running = false
        thread?.join(1500)
        thread = null
        val data = synchronized(pcm) { pcm.toByteArray() }
        return toWav(data)
    }

    fun cancel() {
        running = false
        thread?.join(1500)
        thread = null
        pcm.reset()
    }

    val isRunning: Boolean get() = running

}
