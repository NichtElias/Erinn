package party.elias

import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object NNUE {

    const val FEATURE_NUM = 6 * 64 * 2
    const val ACC_HALF_SIZE = 8
    const val ACC_HALF_WITH_PSQT_SIZE = ACC_HALF_SIZE + 0

    val ftBiases: IntArray = IntArray(ACC_HALF_WITH_PSQT_SIZE)
    val ftWeights: Array<IntArray> = Array(FEATURE_NUM) { IntArray(ACC_HALF_WITH_PSQT_SIZE) } // this one is laid out differently, so that the weights for a single feature are contiguous in memory
    val outBiases: IntArray = IntArray(1)
    val outWeights: IntArray = IntArray(ACC_HALF_SIZE * 2 * 1)

    const val Q_SCALE_FT = 8191
    const val Q_SCALE_OUT = 2048

    fun load() {
        val bytes = Files.readAllBytes(Paths.get("model.bin"))
        val buffer = ByteBuffer.wrap(bytes).asIntBuffer()

        buffer.get(ftBiases)
        for (i in 0..<FEATURE_NUM) {
            buffer.get(ftWeights[i])
        }
        buffer.get(outBiases)
        buffer.get(outWeights)
    }

    fun evaluate(accOur: IntArray, accTheir: IntArray): Score {
        val accClamped = IntArray(ACC_HALF_SIZE * 2)

        for (i in 0..<ACC_HALF_SIZE) {
            accClamped[i] = min(max(accOur[i], 0), Q_SCALE_FT)
        }

        for (i in 0..<ACC_HALF_SIZE) {
            accClamped[i + ACC_HALF_SIZE] = min(max(accTheir[i], 0), Q_SCALE_FT)
        }

        var outOut = outBiases[0]

        for (j in 0..<(ACC_HALF_SIZE * 2)) {
            outOut += accClamped[j] * outWeights[j]
        }

        return outOut / Q_SCALE_OUT * 512 / Q_SCALE_FT
    }
}