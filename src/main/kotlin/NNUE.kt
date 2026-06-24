package party.elias

import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

object NNUE {

    const val FEATURE_NUM = 6 * 2 * 32 * 64
    const val ACC_HALF_SIZE = 64
    const val PSQT_BUCKETS = 8
    const val ACC_HALF_WITH_PSQT_SIZE = ACC_HALF_SIZE + PSQT_BUCKETS

    const val OUTPUT_BUCKETS = 8

    val ftBiases: IntArray = IntArray(ACC_HALF_WITH_PSQT_SIZE)
    val ftWeights: Array<IntArray> = Array(FEATURE_NUM) { IntArray(ACC_HALF_WITH_PSQT_SIZE) } // this one is laid out differently, so that the weights for a single feature are contiguous in memory
    val outBiases: IntArray = IntArray(OUTPUT_BUCKETS)
    val outWeights: IntArray = IntArray(ACC_HALF_SIZE * 2 * OUTPUT_BUCKETS)

    const val Q_SCALE_ACTIVATION = 8191
    const val Q_SCALE_OTHER = 2048

    fun load() {

        val bytes = NNUE.javaClass.classLoader.getResourceAsStream("model_halfKA_hm_64_v7.bin")?.readAllBytes()
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

        val bucketIndex = (pieceCount - 1) / 4

        var outOut = outBiases[bucketIndex]

        val outWeightsOffset = bucketIndex * ACC_HALF_SIZE * 2
        for (j in 0..<(ACC_HALF_SIZE * 2)) {
            outOut += accClamped[j] * outWeights[j + outWeightsOffset]
        }

        val psqtValue = (accOur[ACC_HALF_SIZE + bucketIndex] - accTheir[ACC_HALF_SIZE + bucketIndex]) * Q_SCALE_OTHER / 2

        return (outOut + psqtValue) / Q_SCALE_OTHER * 512 / Q_SCALE_ACTIVATION
    }

    fun whiteFeature(piece: Piece, square: Square, kingSquare: Square): Int {
        val hm = ((kingSquare.value and 0b000100) ushr 2) * 0b000111

        var hmKingSquare = kingSquare.value xor hm
        // king square gets packed into 5 bits, as the highest bit in the hm king square's file is always 0,
        // so we shift rank over by one
        hmKingSquare = ((hmKingSquare and 0b111000) ushr 1) or (hmKingSquare and 0b000011)

        return (piece.type().idx() * 2 * 32 * 64
                + (if (piece.color() == Color.WHITE) 0 else 1) * 32 * 64
                + hmKingSquare * 64
                + (square.value xor hm))
    }

    fun blackFeature(piece: Piece, square: Square, kingSquare: Square): Int {
        val hm = ((kingSquare.value and 0b000100) ushr 2) * 0b000111

        var hmKingSquare = kingSquare.mirror.value xor hm
        // king square gets packed into 5 bits, as the highest bit in the hm king square's file is always 0,
        // so we shift rank over by one
        hmKingSquare = ((hmKingSquare and 0b111000) ushr 1) or (hmKingSquare and 0b000011)

        return (piece.type().idx() * 2 * 32 * 64
                + (if (piece.color() == Color.BLACK) 0 else 1) * 32 * 64
                + hmKingSquare * 64
                + (square.mirror.value xor hm))
    }
}