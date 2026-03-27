package com.example.fishingvoicetrainer

object DTWSequence {

    // seqA, seqB: Array<frameIndex, FloatArray(mfccCoeffs)>
    fun distance(seqA: Array<FloatArray>, seqB: Array<FloatArray>): Float {
        if (seqA.isEmpty() || seqB.isEmpty()) return Float.MAX_VALUE

        val n = seqA.size
        val m = seqB.size

        val dp = Array(n) { FloatArray(m) { Float.POSITIVE_INFINITY } }

        fun frameDist(a: FloatArray, b: FloatArray): Float {
            var sum = 0f
            val len = minOf(a.size, b.size)
            for (i in 0 until len) {
                val d = a[i] - b[i]
                sum += d * d
            }
            return sum
        }

        dp[0][0] = frameDist(seqA[0], seqB[0])

        for (i in 1 until n) {
            dp[i][0] = dp[i - 1][0] + frameDist(seqA[i], seqB[0])
        }
        for (j in 1 until m) {
            dp[0][j] = dp[0][j - 1] + frameDist(seqA[0], seqB[j])
        }

        for (i in 1 until n) {
            for (j in 1 until m) {
                val cost = frameDist(seqA[i], seqB[j])
                val prev = minOf(dp[i - 1][j], minOf(dp[i][j - 1], dp[i - 1][j - 1]))
                dp[i][j] = cost + prev
            }
        }

        return dp[n - 1][m - 1]
    }
}
