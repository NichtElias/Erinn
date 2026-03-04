package party.elias.tunepst

import party.elias.Bitboards
import party.elias.Board
import party.elias.Color
import party.elias.Eval
import party.elias.PieceType
import party.elias.Score
import java.io.File
import kotlin.math.exp
import kotlin.math.pow

object Tuner {

    fun loadBatches(maxBatchCount: Int, batchSize: Int): ArrayList<Array<Sample>> {

        val reader = File("positions.csv").bufferedReader()

        val batches = ArrayList<Array<Sample>>()

        reader.useLines { lines ->
            val lineIt = lines.iterator()

            for (i in 0..<maxBatchCount) {
                try {
                    val currentBatch = Array(batchSize) {
                        val lineParts = lineIt.next().split(",")

                        Sample(Board.fromFen(lineParts[0]), lineParts[1].toFloat())
                    }

                    batches.add(currentBatch)
                } catch (e: NoSuchElementException) {

                }
            }
        }

        return batches
    }

    fun gradient(samples: Array<Sample>, parameters: IntArray): Pair<FloatArray, Float> {
        val finalFloatD = FloatArray(parameters.size)
        var loss = 0F

        for (sample in samples) {
            val matBal = Eval.evaluate(sample.position, parameters)
            val winProb = winProbability(matBal)
            loss += sampleError(winProb, sample.result) / samples.size

            val errD = sampleErrorDerivative(winProb, sample.result)
            val winProbD = winProbabilityDerivative(matBal)
            val evalD = evaluationDerivative(sample.position, parameters)

            for (p in 0..<finalFloatD.size) {
                finalFloatD[p] += evalD[p] * winProbD * errD / samples.size
            }
        }

        return Pair(finalFloatD, loss)
    }

    fun loss(samples: Array<Sample>, parameters: IntArray): Float {
        var totalLoss = 0F
        for (sample in samples) {
            totalLoss += sampleError(winProbability(Eval.evaluate(sample.position, parameters)), sample.result) / samples.size
        }
        return totalLoss
    }

    fun evaluationDerivative(position: Board, parameters: IntArray): FloatArray {
        val derivatives = FloatArray(parameters.size)

        val mgPhase = Eval.midgamePhase(position)
        val egPhase = 24 - mgPhase

        val wKingSq = position.kingSquares[Color.WHITE.idx()].value
        val bKingSq = position.kingSquares[Color.BLACK.idx()].value
        val whiteKingQuad = Bitboards.QUADRANTS[((wKingSq and 0b100) ushr 2) or ((wKingSq and 0b100000) ushr 4)]
        val blackKingQuad = Bitboards.QUADRANTS[((bKingSq and 0b100) ushr 2) or ((bKingSq and 0b100000) ushr 4)]

        val whiteKingQuadPieces = whiteKingQuad and position.colorsBB[Color.WHITE.idx()]
        val whiteNonKingQuadPieces = whiteKingQuad.inv() and position.colorsBB[Color.WHITE.idx()]
        val blackKingQuadPieces = blackKingQuad and position.colorsBB[Color.BLACK.idx()]
        val blackNonKingQuadPieces = blackKingQuad.inv() and position.colorsBB[Color.BLACK.idx()]

        for (p in PieceType.PAWN.idx()..PieceType.KING.idx()) {
            Bitboards.forAllSquares(position.piecesBB[p] and whiteKingQuadPieces) { square ->
                derivatives[Eval.PST_INDEX + square.value * 2 * 2 * 6 + p] = mgPhase.toFloat() / 24
                derivatives[Eval.PST_INDEX + square.value * 2 * 2 * 6 + 6 + p] = egPhase.toFloat() / 24
            }

            Bitboards.forAllSquares(position.piecesBB[p] and whiteNonKingQuadPieces) { square ->
                derivatives[Eval.PST_INDEX + square.value * 2 * 2 * 6 + 12 + p] = mgPhase.toFloat() / 24
                derivatives[Eval.PST_INDEX + square.value * 2 * 2 * 6 + 12 + 6 + p] = egPhase.toFloat() / 24
            }

            Bitboards.forAllSquares(position.piecesBB[p] and blackKingQuadPieces) { square ->
                derivatives[Eval.PST_INDEX + square.mirror.value * 2 * 2 * 6 + p] = -mgPhase.toFloat() / 24
                derivatives[Eval.PST_INDEX + square.mirror.value * 2 * 2 * 6 + 6 + p] = -egPhase.toFloat() / 24
            }

            Bitboards.forAllSquares(position.piecesBB[p] and blackNonKingQuadPieces) { square ->
                derivatives[Eval.PST_INDEX + square.mirror.value * 2 * 2 * 6 + 12 + p] = -mgPhase.toFloat() / 24
                derivatives[Eval.PST_INDEX + square.mirror.value * 2 * 2 * 6 + 12 + 6 + p] = -egPhase.toFloat() / 24
            }
        }

        val whitePawns = position.piecesBB[PieceType.PAWN.idx()] and position.colorsBB[Color.WHITE.idx()]
        val blackPawns = position.piecesBB[PieceType.PAWN.idx()] and position.colorsBB[Color.BLACK.idx()]
        Bitboards.forAllSquares(whitePawns) { square ->
            if (Bitboards.FRONT_SPANS_WHITE[square.value] and blackPawns == 0L) {
                derivatives[Eval.PASSED_INDEX] += egPhase.toFloat() / 24
            }
        }

        Bitboards.forAllSquares(blackPawns) { square ->
            if (Bitboards.FRONT_SPANS_BLACK[square.value] and whitePawns == 0L) {
                derivatives[Eval.PASSED_INDEX] -= egPhase.toFloat() / 24
            }
        }

        val whiteBishops = position.piecesBB[PieceType.BISHOP.idx()] and position.colorsBB[Color.WHITE.idx()]
        if (whiteBishops and Bitboards.LIGHT_SQUARES != 0L && whiteBishops and Bitboards.DARK_SQUARES != 0L) {
            derivatives[Eval.BISHOP_PAIR_INDEX] += egPhase.toFloat() / 24
        }

        val blackBishops = position.piecesBB[PieceType.BISHOP.idx()] and position.colorsBB[Color.BLACK.idx()]
        if (blackBishops and Bitboards.LIGHT_SQUARES != 0L && blackBishops and Bitboards.DARK_SQUARES != 0L) {
            derivatives[Eval.BISHOP_PAIR_INDEX] -= egPhase.toFloat() / 24
        }

        return derivatives
    }

    fun winProbability(score: Score): Float =
        1F / (1F + exp(-K * score))

    fun sampleError(actual: Float, expected: Float): Float =
        (expected - actual).pow(2)

    fun winProbabilityDerivative(score: Score) : Float =
        winProbability(score) * (1F - winProbability(score))

    fun sampleErrorDerivative(actual: Float, expected: Float): Float =
        2 * (expected - actual)

    const val K = 1F / 174F

    data class Sample(val position: Board, val result: Float)
}