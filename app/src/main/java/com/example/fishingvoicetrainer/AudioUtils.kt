package com.example.fishingvoicetrainer
import android.content.Context
import java.io.File
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.FileOutputStream
import java.io.RandomAccessFile

fun getOutputFile(context: Context, command: String): File {
    // folderul comenzii, ex: /Android/data/.../start_l1/
    val dir = File(context.getExternalFilesDir(null), command)
    if (!dir.exists()) dir.mkdirs()

    // nume unic pentru fiecare înregistrare
    val fileName = "sample_${System.currentTimeMillis()}.pcm"

    return File(dir, fileName)
}

fun startRecording(outputFile: File): AudioRecord {
    val sampleRate = 16000
    val channelConfig = AudioFormat.CHANNEL_IN_MONO
    val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        channelConfig,
        audioFormat
    )

    val recorder = try {
        AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
    } catch (e: SecurityException) {
        e.printStackTrace()
        throw e
    }

    recorder.startRecording()

    Thread {
        val buffer = ByteArray(bufferSize)
        FileOutputStream(outputFile).use { fos ->
            while (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    fos.write(buffer, 0, read)
                }
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
        // dacă nu avem permisiune, nu crăpăm
        e.printStackTrace()
    }
}

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
    wavFile.writeInt(Integer.reverseBytes(16)) // Subchunk1Size
    wavFile.writeShort(java.lang.Short.reverseBytes(1).toInt()) // AudioFormat = PCM
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

    writeWavHeader(
        wavFile = wav,
        pcmDataSize = pcmData.size
    )

    wav.write(pcmData)
    wav.close()

    return wavFile
}
