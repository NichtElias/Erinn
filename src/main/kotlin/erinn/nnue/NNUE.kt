package party.elias.erinn.nnue

import party.elias.erinn.PieceType
import party.elias.erinn.Score
import party.elias.erinn.Square
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

object NNUE {

    const val FEATURE_NUM = 6 * 2 * 8 * 64
    const val ACC_HALF_SIZE = 128

    const val OUTPUT_BUCKETS = 8

    val ftBiases: IntArray = IntArray(ACC_HALF_SIZE)
    val ftWeights: Array<IntArray> = Array(FEATURE_NUM) { IntArray(ACC_HALF_SIZE) } // this one is laid out differently, so that the weights for a single feature are contiguous in memory
    val outBiases: IntArray = IntArray(OUTPUT_BUCKETS)
    val outWeights: IntArray = IntArray(ACC_HALF_SIZE * 2 * OUTPUT_BUCKETS)

    const val Q_SCALE_ACTIVATION = 8191
    const val Q_SCALE_OTHER = 2048

    val KING_BUCKETS = intArrayOf(
        0, 1, 2, 3, 3, 2, 1, 0,
        0, 4, 5, 6, 6, 5, 4, 0,
        4, 4, 5, 6, 6, 5, 4, 4,
        4, 4, 5, 6, 6, 5, 4, 4,
        7, 7, 7, 7, 7, 7, 7, 7,
        7, 7, 7, 7, 7, 7, 7, 7,
        7, 7, 7, 7, 7, 7, 7, 7,
        7, 7, 7, 7, 7, 7, 7, 7
    )

    fun load() {

        val bytes = NNUE.javaClass.classLoader.getResourceAsStream("model_768x8_hm_128_screlu_v12.bin")?.readAllBytes()
        val buffer = ByteBuffer.wrap(bytes).asIntBuffer()

        buffer.get(ftBiases)
        for (i in 0..<FEATURE_NUM) {
            buffer.get(ftWeights[i])
        }
        buffer.get(outBiases)
        buffer.get(outWeights)
    }

    val accClamped = IntArray(ACC_HALF_SIZE * 2)

    fun evaluate(accOur: IntArray, accTheir: IntArray, pieceCount: Int): Score {

        for (i in 0..<ACC_HALF_SIZE) {
            accClamped[i] = min(max(accOur[i], 0), Q_SCALE_ACTIVATION)
        }

        for (i in 0..<ACC_HALF_SIZE) {
            accClamped[i + ACC_HALF_SIZE] = min(max(accTheir[i], 0), Q_SCALE_ACTIVATION)
        }

        for (i in 0..<(ACC_HALF_SIZE * 2)) {
            accClamped[i] = accClamped[i] * accClamped[i] / Q_SCALE_ACTIVATION
        }

        val bucketIndex = (pieceCount - 1) / 4

        var outOut = outBiases[bucketIndex]

        val outWeightsOffset = bucketIndex * ACC_HALF_SIZE * 2
        for (j in 0..<(ACC_HALF_SIZE * 2)) {
            outOut += accClamped[j] * outWeights[j + outWeightsOffset]
        }

        return outOut / Q_SCALE_OTHER * 512 / Q_SCALE_ACTIVATION
    }

    /**
     * Calculates the feature index.
     * @param square the square the piece is on, needs to be mirrored for black's perspective
     * @param kingSquare the square the own king is on, needs to be mirrored for black's perspective
     * @param relativePieceColor is 0 for own pieces and 1 for opponent's pieces
     */
    fun feature(pieceType: PieceType, square: Square, kingSquare: Square, relativePieceColor: Int): Int {
        val hm = ((kingSquare.v and 0b000100) ushr 2) * 0b000111

        return (pieceType.idx * 2 * 8 * 64
                + relativePieceColor * 8 * 64
                + KING_BUCKETS[kingSquare.v] * 64
                + (square.v xor hm))
    }
}