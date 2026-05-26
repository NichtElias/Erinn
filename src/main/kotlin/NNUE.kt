package party.elias

import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

object NNUE {

    const val FEATURE_NUM = 6 * 2 * 64 * 64
    const val ACC_HALF_SIZE = 48
    const val PSQT_BUCKETS = 8
    const val ACC_HALF_WITH_PSQT_SIZE = ACC_HALF_SIZE + PSQT_BUCKETS

    val ftBiases: IntArray = IntArray(ACC_HALF_WITH_PSQT_SIZE)
    val ftWeights: Array<IntArray> = Array(FEATURE_NUM) { IntArray(ACC_HALF_WITH_PSQT_SIZE) } // this one is laid out differently, so that the weights for a single feature are contiguous in memory
    val outBiases: IntArray = IntArray(1)
    val outWeights: IntArray = IntArray(ACC_HALF_SIZE * 2 * 1)

    const val Q_SCALE_ACTIVATION = 8191
    const val Q_SCALE_OTHER = 2048

    fun load() {

        val bytes = NNUE.javaClass.classLoader.getResourceAsStream("model_halfKA_48_v5.bin")?.readAllBytes()
        val buffer = ByteBuffer.wrap(bytes).asIntBuffer()

        buffer.get(ftBiases)
        for (i in 0..<FEATURE_NUM) {
            buffer.get(ftWeights[i])
        }
        buffer.get(outBiases)
        buffer.get(outWeights)
    }

    fun evaluate(accOur: IntArray, accTheir: IntArray, pieceCount: Int): Score {
        val accClamped = IntArray(ACC_HALF_SIZE * 2)

        for (i in 0..<ACC_HALF_SIZE) {
            accClamped[i] = min(max(accOur[i], 0), Q_SCALE_ACTIVATION)
        }

        for (i in 0..<ACC_HALF_SIZE) {
            accClamped[i + ACC_HALF_SIZE] = min(max(accTheir[i], 0), Q_SCALE_ACTIVATION)
        }

        var outOut = outBiases[0]

        for (j in 0..<(ACC_HALF_SIZE * 2)) {
            outOut += accClamped[j] * outWeights[j]
        }

        val psqtBucketIndex = (pieceCount - 1) / 4

        val psqtValue = (accOur[ACC_HALF_SIZE + psqtBucketIndex] - accTheir[ACC_HALF_SIZE + psqtBucketIndex]) * Q_SCALE_OTHER / 2

        return (outOut + psqtValue) / Q_SCALE_OTHER * 512 / Q_SCALE_ACTIVATION
    }
}