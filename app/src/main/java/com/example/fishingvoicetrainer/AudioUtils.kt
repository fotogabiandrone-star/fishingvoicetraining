package com.example.fishingvoicetrainer

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.abs

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

    Thread {
        val buffer = ByteArray(bufferSize)
        FileOutputStream(outputFile).use { fos ->
            while (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
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
//  TRIMMING AUTOMAT AL LINISTII
// ===============================

fun trimSilence(
    pcmFile: File,
    threshold: Int = 200,      // amplitudine minimă (≈ -40 dB)
    windowSize: Int = 160      // 160 sample-uri ≈ 10 ms la 16 kHz
): File {

    val pcmData = pcmFile.readBytes()
    if (pcmData.isEmpty()) return pcmFile

    // Convertim PCM 16-bit LE în short[]
    val samples = ShortArray(pcmData.size / 2)
    var idx = 0
    for (i in samples.indices) {
        samples[i] = ((pcmData[idx + 1].toInt() shl 8) or (pcmData[idx].toInt() and 0xFF)).toShort()
        idx += 2
    }

    // Găsim începutul
    var start = 0
    while (start < samples.size) {
        var maxAmp = 0
        for (i in start until minOf(start + windowSize, samples.size)) {
            maxAmp = maxOf(maxAmp, abs(samples[i].toInt()))
        }
        if (maxAmp > threshold) break
        start += windowSize
    }

    // Găsim finalul
    var end = samples.size - 1
    while (end > 0) {
        var maxAmp = 0
        val winStart = maxOf(0, end - windowSize)
        for (i in winStart..end) {
            maxAmp = maxOf(maxAmp, abs(samples[i].toInt()))
        }
        if (maxAmp > threshold) break
        end -= windowSize
    }

    // Dacă nu găsim nimic util, returnăm originalul
    if (start >= end) return pcmFile

    // Extragem doar partea utilă
    val trimmedSamples = samples.copyOfRange(start, end)

    // Scriem într-un nou fișier
    val trimmedFile = File(pcmFile.parent, pcmFile.nameWithoutExtension + "_trimmed.pcm")
    FileOutputStream(trimmedFile).use { fos ->
        for (s in trimmedSamples) {
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
