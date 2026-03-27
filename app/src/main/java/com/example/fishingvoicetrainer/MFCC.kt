package com.example.fishingvoicetrainer
/*
import kotlin.math.*

class MFCCSequence(
    private val sampleRate: Int = 16000,
    private val numCoefficients: Int = 13,
    private val numFilters: Int = 40,
    private val fftSize: Int = 512,
    private val frameLengthMs: Int = 25,
    private val frameShiftMs: Int = 10
) {

    private val frameLengthSamples = (sampleRate * frameLengthMs) / 1000
    private val frameShiftSamples = (sampleRate * frameShiftMs) / 1000

    private val hamming = FloatArray(fftSize) { i ->
        (0.54 - 0.46 * cos(2.0 * Math.PI * i / (fftSize - 1))).toFloat()
    }

    private val melFilterBank = createMelFilterBank()

    fun extractSequence(samples: ShortArray): Array<FloatArray> {
        if (samples.isEmpty()) return emptyArray()

        // 1) short -> float [-1, 1]
        val floatSamples = FloatArray(samples.size) { samples[it] / 32768f }

        // 2) framing
        val frames = mutableListOf<FloatArray>()
        var start = 0
        while (start + frameLengthSamples <= floatSamples.size) {
            val frame = FloatArray(fftSize)
            // copy frameLengthSamples, rest zero
            for (i in 0 until frameLengthSamples) {
                frame[i] = floatSamples[start + i]
            }
            // Hamming
            for (i in frame.indices) {
                frame[i] *= hamming[i]
            }
            frames.add(frame)
            start += frameShiftSamples
        }

        if (frames.isEmpty()) return emptyArray()

        // 3) MFCC per frame
        val seq = Array(frames.size) { FloatArray(numCoefficients) }
        for (f in frames.indices) {
            val spectrum = fft(frames[f])
            val mag = FloatArray(fftSize / 2)
            for (i in mag.indices) {
                val re = spectrum[2 * i]
                val im = spectrum[2 * i + 1]
                mag[i] = sqrt(re * re + im * im)
            }

            val melEnergies = FloatArray(numFilters)
            for (m in 0 until numFilters) {
                var sum = 0f
                val filter = melFilterBank[m]
                for (i in filter.indices) {
                    sum += filter[i] * mag[i]
                }
                melEnergies[m] = ln(max(sum, 1e-10f))
            }

            val mfccFull = dct(melEnergies)
            seq[f] = mfccFull.copyOf(numCoefficients)
        }

        return seq
    }

    private fun fft(input: FloatArray): FloatArray {
        val n = input.size
        val output = FloatArray(n * 2)
        for (i in 0 until n) {
            output[2 * i] = input[i]
            output[2 * i + 1] = 0f
        }

        var i = 0
        var j = 0
        while (i < n) {
            if (j > i) {
                val ri = output[2 * i]
                val ii = output[2 * i + 1]
                output[2 * i] = output[2 * j]
                output[2 * i + 1] = output[2 * j + 1]
                output[2 * j] = ri
                output[2 * j + 1] = ii
            }
            var m = n shr 1
            while (m >= 1 && j >= m) {
                j -= m
                m = m shr 1
            }
            j += m
            i++
        }

        var mmax = 1
        while (n > mmax) {
            val step = mmax shl 1
            val theta = (-2.0 * Math.PI / step)
            val wpr = -2.0 * sin(0.5 * theta).pow(2.0)
            val wpi = sin(theta)
            var wr = 1.0
            var wi = 0.0

            var m = 0
            while (m < mmax) {
                var k = m
                while (k < n) {
                    val j2 = k + mmax
                    val tr = wr * output[2 * j2] - wi * output[2 * j2 + 1]
                    val ti = wr * output[2 * j2 + 1] + wi * output[2 * j2]

                    output[2 * j2] = (output[2 * k] - tr).toFloat()
                    output[2 * j2 + 1] = (output[2 * k + 1] - ti).toFloat()

                    output[2 * k] = (output[2 * k] + tr).toFloat()
                    output[2 * k + 1] = (output[2 * k + 1] + ti).toFloat()

                    k += step
                }

                val wtemp = wr
                wr = wr + wr * wpr - wi * wpi
                wi = wi + wi * wpr + wtemp * wpi
                m++
            }
            mmax = step
        }

        return output
    }

    private fun createMelFilterBank(): Array<FloatArray> {
        val filters = Array(numFilters) { FloatArray(fftSize / 2) }

        fun hzToMel(hz: Double) = 2595.0 * ln(1 + hz / 700.0)
        fun melToHz(mel: Double) = 700.0 * (exp(mel / 2595.0) - 1)

        val lowMel = hzToMel(0.0)
        val highMel = hzToMel(sampleRate / 2.0)

        val melPoints = DoubleArray(numFilters + 2)
        for (i in melPoints.indices) {
            melPoints[i] = lowMel + (highMel - lowMel) * i / (numFilters + 1)
        }

        val hzPoints = melPoints.map { melToHz(it) }
        val bin = hzPoints.map { floor((fftSize + 1) * it / sampleRate).toInt() }

        for (f in 0 until numFilters) {
            val start = bin[f]
            val center = bin[f + 1]
            val end = bin[f + 2]

            for (i in start until center) {
                if (i in 0 until fftSize / 2) {
                    filters[f][i] = ((i - start).toFloat() / (center - start))
                }
            }
            for (i in center until end) {
                if (i in 0 until fftSize / 2) {
                    filters[f][i] = ((end - i).toFloat() / (end - center))
                }
            }
        }

        return filters
    }

    private fun dct(input: FloatArray): FloatArray {
        val n = input.size
        val output = FloatArray(n)
        for (k in 0 until n) {
            var sum = 0.0
            for (i in 0 until n) {
                sum += input[i] * cos(Math.PI * k * (2 * i + 1) / (2 * n))
            }
            output[k] = sum.toFloat()
        }
        return output
    }
}
*/