package party.elias

import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object NNUE {

    const val FEATURE_NUM = 64 * 64 * 5 * 2
    const val ACC_HALF_SIZE = 128
    const val ACC_HALF_WITH_PSQT_SIZE = ACC_HALF_SIZE + 1

    val ftBiases: FloatArray = FloatArray(ACC_HALF_WITH_PSQT_SIZE)
    val ftWeights: Array<FloatArray> = Array(FEATURE_NUM) { FloatArray(ACC_HALF_WITH_PSQT_SIZE) } // this one is laid out differently, so that the weights for a single feature are contiguous in memory
    val outBiases: FloatArray = FloatArray(1)
    val outWeights: FloatArray = FloatArray(ACC_HALF_SIZE * 2 * 1)

    fun load() {
        val bytes = Files.readAllBytes(Paths.get("model.bin"))
        val buffer = ByteBuffer.wrap(bytes).asFloatBuffer()

        buffer.get(ftBiases)
        for (i in 0..<FEATURE_NUM) {
            buffer.get(ftWeights[i])
        }
        buffer.get(outBiases)
        buffer.get(outWeights)
    }

    fun evaluate(accOur: FloatArray, accTheir: FloatArray, turn: Color): Score {
        val accClamped = FloatArray(ACC_HALF_SIZE * 2)

        for (i in 0..<ACC_HALF_SIZE) {
            accClamped[i] = min(max(accOur[i], 0F), 1F)
        }

        for (i in 0..<ACC_HALF_SIZE) {
            accClamped[i + ACC_HALF_SIZE] = min(max(accTheir[i], 0F), 1F)
        }

        var outOut = outBiases[0]

        for (j in 0..<(ACC_HALF_SIZE * 2)) {
            outOut += accClamped[j] * outWeights[j]
        }
        // clamp would go here last layer doesn't get clamped!

        val psqtVal = (accOur[ACC_HALF_SIZE] - accTheir[ACC_HALF_SIZE]) * 0.5F

        return ((outOut + psqtVal) * 100).roundToInt()
    }
}