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
    val ftWeights: FloatArray = FloatArray(FEATURE_NUM * ACC_HALF_WITH_PSQT_SIZE) // this one is laid out differently, so that the weights for a single feature are contiguous in memory
    val outBiases: FloatArray = FloatArray(1)
    val outWeights: FloatArray = FloatArray(ACC_HALF_SIZE * 2 * 1)

    fun load() {
        val bytes = Files.readAllBytes(Paths.get("model.bin"))
        val buffer = ByteBuffer.wrap(bytes).asFloatBuffer()

        buffer.get(ftBiases)
        buffer.get(ftWeights)
        buffer.get(outBiases)
        buffer.get(outWeights)
    }

    fun evaluate(accWhite: FloatArray, accBlack: FloatArray, turn: Color): Score {
        val accClamped = FloatArray(ACC_HALF_SIZE * 2)

        val whiteOffset: Int
        val blackOffset: Int
        if (turn == Color.WHITE) {
            whiteOffset = 0
            blackOffset = ACC_HALF_SIZE
        } else {
            whiteOffset = ACC_HALF_SIZE
            blackOffset = 0
        }

        for (i in 0..<ACC_HALF_SIZE) {
            accClamped[i + whiteOffset] = min(max(accWhite[i], 0F), 1F)
            accClamped[i + blackOffset] = min(max(accBlack[i], 0F), 1F)
        }

        var outOut = outBiases[0]

        for (j in 0..<(ACC_HALF_SIZE * 2)) {
            outOut += accClamped[j] * outWeights[ACC_HALF_SIZE * 2 + j]
        }
        // clamp would go here last layer doesn't get clamped!

        val psqtVal = (accWhite[ACC_HALF_SIZE] - accBlack[ACC_HALF_SIZE]) * (turn.idx() - 0.5F)

        return ((outOut + psqtVal) * 100).roundToInt()
    }
}