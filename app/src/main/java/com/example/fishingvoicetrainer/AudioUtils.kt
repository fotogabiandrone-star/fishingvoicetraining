package com.example.fishingvoicetrainer

import android.content.Context
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.abs

private const val TAG = "Recorder"

// ======================================
//  GENERARE NUME INCREMENTAL (001, 002…)
// ======================================
private fun nextIndexFor(dir: File, prefix: String): String {
    val existing = dir.listFiles { f -> f.extension == "pcm" } ?: return "001"

    val maxIndex = existing
        .mapNotNull { file ->
            val num = file.nameWithoutExtension.removePrefix(prefix + "_")
            num.toIntOrNull()
        }
        .maxOrNull() ?: 0

    return String.format("%03d", maxIndex + 1)
}

// ===============================
//  ÎNREGISTRARE PCM
// ===============================

@SuppressLint("MissingPermission")
fun startRecording(outputFile: File): AudioRecord {
    val sampleRate = 16000
    val channelConfig = AudioFormat.CHANNEL_IN_MONO
    val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        channelConfig,
        audioFormat
    )

    val recorder = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        sampleRate,
        channelConfig,
        audioFormat,
        bufferSize
    )

    recorder.startRecording()
    Log.d(TAG, "Recorder started, state=${recorder.recordingState}")

    Thread {
        val buffer = ByteArray(bufferSize)

        Log.d(TAG, "Thread started, recorderState=${recorder.recordingState}")

        FileOutputStream(outputFile).use { fos ->
            while (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {

                Log.d(TAG, "Loop check: state=${recorder.recordingState}")

                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) fos.write(buffer, 0, read)
            }
        }
    }.start()

    return recorder
}

fun stopRecording(recorder: AudioRecord?) {
    try {
        recorder?.stop()
        recorder?.release()
    } catch (e: SecurityException) {
        e.printStackTrace()
    }
}

// ===============================
//  NORMALIZARE AUDIO (peak normalize la -1 dB)
// ===============================
fun normalizePcm(samples: ShortArray, targetDb: Int = -1): ShortArray {

    var peak = 0
    for (s in samples) {
        val v = abs(s.toInt())
        if (v > peak) peak = v
    }

    if (peak == 0) return samples

    val targetAmp = (32767.0 * Math.pow(10.0, targetDb / 20.0)).toInt()
    val factor = targetAmp.toDouble() / peak.toDouble()

    val out = ShortArray(samples.size)
    for (i in samples.indices) {
        val scaled = (samples[i] * factor).toInt()
        out[i] = scaled.coerceIn(-32767, 32767).toShort()
    }

    return out
}

// ===============================
//  TRIMMING AUTOMAT AL LINISTII
// ===============================

fun trimSilence(
    context: Context,
    pcmFile: File
): File {

    val prefs = context.getSharedPreferences("settings", 0)

    val minThreshold = prefs.getInt("minThreshold", 1800)
    val maxThreshold = prefs.getInt("maxThreshold", 3800)
    val windowSize = prefs.getInt("windowSize", 80)
    val minVoiceMs = prefs.getInt("minVoiceMs", 200)

    val preRollMs = prefs.getInt("preRollMs", 60)
    val postRollMs = prefs.getInt("postRollMs", 180)

    val preRollSamples = (preRollMs * 16).coerceAtLeast(0)
    val postRollSamples = (postRollMs * 16).coerceAtLeast(0)

    val pcmData = pcmFile.readBytes()
    if (pcmData.isEmpty()) return pcmFile

    val samples = ShortArray(pcmData.size / 2)
    var idx = 0
    for (i in samples.indices) {
        samples[i] = ((pcmData[idx + 1].toInt() shl 8) or (pcmData[idx].toInt() and 0xFF)).toShort()
        idx += 2
    }

    val noiseWindowSamples = (0.3f * 16000).toInt().coerceAtMost(samples.size)
    var noiseMax = 0
    for (i in 0 until noiseWindowSamples) {
        noiseMax = maxOf(noiseMax, abs(samples[i].toInt()))
    }

    val adaptiveThreshold = noiseMax.coerceIn(minThreshold, maxThreshold)

    var start = 0
    while (start < samples.size) {
        var maxAmp = 0
        for (i in start until minOf(start + windowSize, samples.size)) {
            maxAmp = maxOf(maxAmp, abs(samples[i].toInt()))
        }
        if (maxAmp > adaptiveThreshold) break
        start += windowSize
    }

    start = (start - preRollSamples).coerceAtLeast(0)

    var end = samples.size - 1
    while (end > 0) {
        var maxAmp = 0
        val winStart = maxOf(0, end - windowSize)
        for (i in winStart..end) {
            maxAmp = maxOf(maxAmp, abs(samples[i].toInt()))
        }
        if (maxAmp > adaptiveThreshold) break
        end -= windowSize
    }

    end = (end + postRollSamples).coerceAtMost(samples.size - 1)

    if (start >= end) return pcmFile

    val minVoiceSamples = (minVoiceMs * 16).coerceAtMost(samples.size)
    if (end - start < minVoiceSamples) return pcmFile

    val trimmedSamples = samples.copyOfRange(start, end + 1)
    val normalizedSamples = normalizePcm(trimmedSamples)

    val trimmedFile = File(pcmFile.parent, pcmFile.nameWithoutExtension + "_trimmed.pcm")
    FileOutputStream(trimmedFile).use { fos ->
        for (s in normalizedSamples) {
            fos.write(s.toInt() and 0xFF)
            fos.write((s.toInt() shr 8) and 0xFF)
        }
    }

    return trimmedFile
}

// ===============================
//  CONVERSIE PCM → WAV
// ===============================

fun writeWavHeader(
    wavFile: RandomAccessFile,
    pcmDataSize: Int,
    sampleRate: Int = 16000,
    channels: Int = 1,
    bitsPerSample: Int = 16
) {
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = (channels * bitsPerSample / 8).toShort()

    wavFile.seek(0)
    wavFile.writeBytes("RIFF")
    wavFile.writeInt(Integer.reverseBytes(36 + pcmDataSize))
    wavFile.writeBytes("WAVE")
    wavFile.writeBytes("fmt ")
    wavFile.writeInt(Integer.reverseBytes(16))
    wavFile.writeShort(java.lang.Short.reverseBytes(1).toInt())
    wavFile.writeShort(java.lang.Short.reverseBytes(channels.toShort()).toInt())
    wavFile.writeInt(Integer.reverseBytes(sampleRate))
    wavFile.writeInt(Integer.reverseBytes(byteRate))
    wavFile.writeShort(java.lang.Short.reverseBytes(blockAlign).toInt())
    wavFile.writeShort(java.lang.Short.reverseBytes(bitsPerSample.toShort()).toInt())
    wavFile.writeBytes("data")
    wavFile.writeInt(Integer.reverseBytes(pcmDataSize))
}

fun convertPcmToWav(pcmFile: File): File {
    val wavFile = File(pcmFile.parent, pcmFile.nameWithoutExtension + ".wav")

    val pcmData = pcmFile.readBytes()
    val wav = RandomAccessFile(wavFile, "rw")

    writeWavHeader(wav, pcmData.size)
    wav.write(pcmData)
    wav.close()

    return wavFile
}
