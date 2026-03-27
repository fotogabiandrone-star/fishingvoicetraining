package com.example.fishingvoicetrainer

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import kotlin.math.abs

object ListeningEngine {

    private const val TAG = "ListeningEngine"

    private var recorder: AudioRecord? = null
    private var isListening = false

    private lateinit var models: List<CommandModel>

    @Volatile private var liveSensitivity = 2000
    @Volatile private var liveThreshold = 300f
    @Volatile private var liveMinMargin = 20f

    fun updateSensitivity(v: Int) {
        liveSensitivity = v
        Log.d("LE_UPDATE", "Sensitivity updated → $v")
    }

    fun updateThreshold(v: Float) {
        liveThreshold = v
        Log.d("LE_UPDATE", "Threshold updated → $v")
    }

    fun updateMinMargin(v: Float) {
        liveMinMargin = v
        Log.d("LE_UPDATE", "MinMargin updated → $v")
    }

    data class CommandModel(
        val label: String,
        val mfccSeq: Array<FloatArray>
    )

    private fun hasMicPermission(context: Context): Boolean {
        val perm = android.Manifest.permission.RECORD_AUDIO
        val granted = android.content.pm.PackageManager.PERMISSION_GRANTED
        return androidx.core.content.ContextCompat.checkSelfPermission(context, perm) == granted
    }

    // ---------------------------------------------------------
    // AUTOTRIM – taie liniștea și normalizează
    // ---------------------------------------------------------
    fun trimSilenceLive(
        samples: ShortArray,
        minThreshold: Int = 1800,
        maxThreshold: Int = 3800,
        windowSize: Int = 80,      // 5 ms la 16 kHz
        minVoiceMs: Int = 200,     // minim 200 ms voce
        preRollMs: Int = 60,
        postRollMs: Int = 180
    ): ShortArray {

        if (samples.isEmpty()) return samples

        val preRollSamples = (preRollMs * 16).coerceAtLeast(0)
        val postRollSamples = (postRollMs * 16).coerceAtLeast(0)
        val minVoiceSamples = (minVoiceMs * 16).coerceAtLeast(0)

        // ============================
        // 1. Estimare zgomot (primele 0.3 sec)
        // ============================
        val noiseWindowSamples = (0.3f * 16000).toInt().coerceAtMost(samples.size)
        var noiseMax = 0
        for (i in 0 until noiseWindowSamples) {
            noiseMax = maxOf(noiseMax, abs(samples[i].toInt()))
        }

        val adaptiveThreshold = noiseMax.coerceIn(minThreshold, maxThreshold)

        // ============================
        // 2. Căutăm începutul vocii
        // ============================
        var start = 0
        while (start < samples.size) {
            var maxAmp = 0
            val endWin = minOf(start + windowSize, samples.size)
            for (i in start until endWin) {
                maxAmp = maxOf(maxAmp, abs(samples[i].toInt()))
            }
            if (maxAmp > adaptiveThreshold) break
            start += windowSize
        }

        start = (start - preRollSamples).coerceAtLeast(0)

        // ============================
        // 3. Căutăm finalul vocii
        // ============================
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

        // ============================
        // 4. Validare lungime minimă
        // ============================
        if (start >= end) return samples
        if (end - start < minVoiceSamples) return samples

        // ============================
        // 5. Extragem secvența
        // ============================
        val trimmed = samples.copyOfRange(start, end + 1)

        // ============================
        // 6. Normalizare la -1 dB
        // ============================
        var peak = 0
        for (s in trimmed) peak = maxOf(peak, abs(s.toInt()))
        if (peak == 0) return trimmed

        val targetAmp = (32767.0 * Math.pow(10.0, -1.0 / 20.0)).toInt()
        val factor = targetAmp.toDouble() / peak.toDouble()

        return ShortArray(trimmed.size) { i ->
            val scaled = (trimmed[i] * factor).toInt()
            scaled.coerceIn(-32767, 32767).toShort()
        }
    }


    // ---------------------------------------------------------
    @SuppressLint("MissingPermission")
    fun startListening(
        context: Context,
        sensitivity: Int,
        matchThreshold: Float,
        onDetected: (String) -> Unit
    ) {
        if (isListening) return
        if (!hasMicPermission(context)) return

        isListening = true
        Log.d(TAG, "=== START LISTENING ===")

        val datasetRoot = File(context.getExternalFilesDir(null), "dataset")
        models = loadModels(datasetRoot)
        Log.d(TAG, "Loaded ${models.size} command models")

        if (models.isEmpty()) {
            isListening = false
            return
        }

        val prefs = context.getSharedPreferences("settings", 0)
        liveMinMargin = prefs.getInt("minMargin", 20).toFloat()
        liveSensitivity = sensitivity
        liveThreshold = matchThreshold

        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        try {
            recorder?.startRecording()
            Log.d(TAG, "Recorder started, state=${recorder?.recordingState}")
        } catch (_: Exception) {
            Log.e(TAG, "Recorder failed to start")
            isListening = false
            recorder?.release()
            recorder = null
            return
        }

        val mfccEngine = MFCCSequence()

        // ---------------------------------------------------------
        // THREAD CU AUTOTRIM
        // ---------------------------------------------------------
        Thread {
            Log.d(TAG, "Thread started, state=${recorder?.recordingState}")

            val buffer = ShortArray(bufferSize)
            val packet = ArrayList<Short>()
            var inVoice = false

            while (isListening && recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {

                val read = recorder!!.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                val maxAmp = buffer.take(read).maxOf { abs(it.toInt()) }

                if (maxAmp > 1000) {
                    //Log.d(TAG, "maxAmp=$maxAmp (threshold=$liveSensitivity)")
                }

                // START VOICE
                if (!inVoice && maxAmp > liveSensitivity) {
                    inVoice = true
                    packet.clear()
                    Log.d(TAG, "🔥 Voice START")
                }

                // COLLECT
                if (inVoice) {
                    for (i in 0 until read) packet.add(buffer[i])
                }

                // END VOICE
                if (inVoice && maxAmp < liveSensitivity / 2) {
                    inVoice = false
                    Log.d(TAG, "🛑 Voice END, packet=${packet.size}")

                    val pcm = packet.toShortArray()
                    if (pcm.isEmpty()) continue

                    // AUTOTRIM
                    val trimmed = trimSilenceLive(
                        pcm,
                        minThreshold = 600,
                        maxThreshold = 2000,
                        windowSize = 80,
                        minVoiceMs = 300,
                        preRollMs = 100,
                        postRollMs = 200
                    )


                    Log.d(TAG, "AutoTrim: in=${pcm.size} out=${trimmed.size}")

                    val inputSeq = mfccEngine.extractSequence(trimmed)
                    Log.d(TAG, "MFCC frames: ${inputSeq.size}")

                    val result = matchCommand(
                        inputSeq,
                        models,
                        liveThreshold,
                        liveMinMargin
                    )

                    if (result != null) {
                        Log.d(TAG, "🎯 MATCHED: $result")
                        onDetected(result)
                    } else {
                        Log.d(TAG, "❌ NO MATCH")
                    }
                }
            }

            Log.d(TAG, "Thread ended")
        }.start()
    }

    // ---------------------------------------------------------
    fun stopListening() {
        isListening = false
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release()
        recorder = null
    }

    // ---------------------------------------------------------
    private fun loadModels(root: File): List<CommandModel> {
        val list = mutableListOf<CommandModel>()
        val mfccEngine = MFCCSequence()

        root.listFiles()?.filter { it.isDirectory }?.forEach { folder ->
            val commandName = folder.name

            val wavFiles = folder.listFiles()
                ?.filter { it.name.endsWith("_trimmed.wav") }
                ?: emptyList()

            if (wavFiles.isEmpty()) return@forEach

            wavFiles.forEach { wav ->
                val pcm = WavReader.readWavToShortArray(wav)
                val seq = mfccEngine.extractSequence(pcm)
                list.add(CommandModel(commandName, seq))
            }
        }

        return list
    }

    // ---------------------------------------------------------
    private fun matchCommand(
        inputSeq: Array<FloatArray>,
        models: List<CommandModel>,
        threshold: Float,
        minMargin: Float
    ): String? {

        val distances = mutableMapOf<String, MutableList<Float>>()

        var indexMap = mutableMapOf<String, Int>()

        for (model in models) {
            val idx = indexMap.getOrDefault(model.label, 0) + 1
            indexMap[model.label] = idx

            val dist = DTWSequence.distance(inputSeq, model.mfccSeq)
            Log.d(TAG, "DIST ${model.label}[$idx] = $dist")

            distances.getOrPut(model.label) { mutableListOf() }.add(dist)
        }


        if (distances.isEmpty()) return null

        val averages = distances.mapValues { it.value.average().toFloat() }
        val best = averages.minByOrNull { it.value } ?: return null

        val bestCommand = best.key
        val bestScore = best.value

        if (bestScore > threshold) return null

        val sorted = averages.values.sorted()
        if (sorted.size > 1) {
            val margin = sorted[1] - sorted[0]
            if (margin < minMargin) return null
        }

        return bestCommand
    }
}

object WavReader {

    fun readWavToShortArray(file: File): ShortArray {
        val bytes = file.readBytes()

        if (bytes.size <= 44) return ShortArray(0)

        val pcmBytes = bytes.copyOfRange(44, bytes.size)
        val samples = ShortArray(pcmBytes.size / 2)

        var idx = 0
        for (i in samples.indices) {
            val lo = pcmBytes[idx].toInt() and 0xFF
            val hi = pcmBytes[idx + 1].toInt()
            samples[i] = ((hi shl 8) or lo).toShort()
            idx += 2
        }

        return samples
    }
}
