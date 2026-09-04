package com.hadeer.offlinetranslator

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

class WavRecorder(val outputFile: File) {
    private val sampleRate = 16_000
    private val channels = 1
    private val bitsPerSample = 16
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private val recording = AtomicBoolean(false)

    @SuppressLint("MissingPermission")
    fun start() {
        if (recording.get()) return
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSize = maxOf(minBuffer, sampleRate * 2)
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        require(audioRecord.state == AudioRecord.STATE_INITIALIZED) { "تعذر تهيئة الميكروفون" }
        outputFile.parentFile?.mkdirs()
        recorder = audioRecord
        recording.set(true)
        worker = Thread {
            val raf = RandomAccessFile(outputFile, "rw")
            try {
                raf.setLength(0)
                writeHeader(raf, 0)
                val buffer = ByteArray(bufferSize)
                var dataBytes = 0L
                audioRecord.startRecording()
                while (recording.get()) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        raf.write(buffer, 0, read)
                        dataBytes += read
                    }
                }
                try { audioRecord.stop() } catch (_: Exception) {}
                raf.seek(0)
                writeHeader(raf, dataBytes)
            } finally {
                raf.close()
                audioRecord.release()
                recorder = null
            }
        }.apply { name = "OfflineTranslatorRecorder"; start() }
    }

    fun stop() {
        if (!recording.getAndSet(false)) return
        try { worker?.join(2500) } catch (_: InterruptedException) {}
        worker = null
    }

    fun isRecording(): Boolean = recording.get()

    private fun writeHeader(raf: RandomAccessFile, dataSize: Long) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalSize = dataSize + 36
        raf.writeBytes("RIFF")
        writeLEInt(raf, totalSize.toInt())
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        writeLEInt(raf, 16)
        writeLEShort(raf, 1)
        writeLEShort(raf, channels)
        writeLEInt(raf, sampleRate)
        writeLEInt(raf, byteRate)
        writeLEShort(raf, blockAlign)
        writeLEShort(raf, bitsPerSample)
        raf.writeBytes("data")
        writeLEInt(raf, dataSize.toInt())
    }

    private fun writeLEInt(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xff)
        raf.write(value shr 8 and 0xff)
        raf.write(value shr 16 and 0xff)
        raf.write(value shr 24 and 0xff)
    }

    private fun writeLEShort(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xff)
        raf.write(value shr 8 and 0xff)
    }
}
