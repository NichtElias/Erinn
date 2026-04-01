package party.elias

import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.max
import kotlin.math.roundToInt

object NNUE {

    const val FEATURE_NUM = 64 * 64 * 5 * 2
    const val ACC_HALF_SIZE = 32
    const val L1_SIZE = 16

    val ftBiases: FloatArray = FloatArray(ACC_HALF_SIZE)
    val ftWeights: FloatArray = FloatArray(FEATURE_NUM * ACC_HALF_SIZE) // this one is laid out differently, so that the weights for a single feature are contiguous in memory
    val l1Biases: FloatArray = FloatArray(L1_SIZE)
    val l1Weights: FloatArray = FloatArray(ACC_HALF_SIZE * 2 * L1_SIZE)
    val l2Biases: FloatArray = FloatArray(1)
    val l2Weights: FloatArray = FloatArray(L1_SIZE * 1)

    fun load() {
        val bytes = Files.readAllBytes(Paths.get("model.bin"))
        val buffer = ByteBuffer.wrap(bytes).asFloatBuffer()

        buffer.get(ftBiases)
        buffer.get(ftWeights)
        buffer.get(l1Biases)
        buffer.get(l1Weights)
        buffer.get(l2Biases)
        buffer.get(l2Weights)
    }

    fun evaluate(accWhite: FloatArray, accBlack: FloatArray, turn: Color): Score {
        val accClamped = FloatArray(ACC_HALF_SIZE * 2)

//        for (i in 0..<ACC_HALF_SIZE) {
//            print("${accWhite[i]}, ")
//        }
//        println()
//        for (i in 0..<ACC_HALF_SIZE) {
//            print("${accBlack[i]}, ")
//        }
//        println()

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
            accClamped[i + whiteOffset] = max(accWhite[i], 0F)
            accClamped[i + blackOffset] = max(accBlack[i], 0F)
        }

//        for (i in 0..<ACC_HALF_SIZE*2) {
//            print("${accClamped[i]}, ")
//        }
//        println()

        val l1Out = l1Biases.clone()

        for (i in 0..<l1Out.size) {
            for (j in 0..<ACC_HALF_SIZE*2) {
                l1Out[i] += accClamped[j] * l1Weights[i * ACC_HALF_SIZE*2 + j]
            }
            l1Out[i] = max(l1Out[i], 0F)
        }

        val l2Out = l2Biases.clone()

        for (i in 0..<l2Out.size) {
            for (j in 0..<L1_SIZE) {
                l2Out[i] += l1Out[j] * l2Weights[i * L1_SIZE + j]
            }
            // l2Out[i] = max(l2Out[i], 0F) last layer doesn't get clamped!
        }

        return (l2Out[0] * 100).roundToInt()
    }
}