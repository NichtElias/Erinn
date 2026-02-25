package party.elias

import party.elias.uci.sendUciInfo
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

typealias Score = Int

class Engine {

    var position: Board = Board.startPos()

    val tt: TranspositionTable = TranspositionTable()

    @Volatile
    var stop: Boolean = false

    var searchStartTime: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow()

    var nodesSearched: Long = 0

    fun evaluate(): Score {
        var whiteMaterial = 0
        whiteMaterial += (position.piecesBB[PieceType.PAWN.value] and position.colorsBB[Color.WHITE.idx()]).countOneBits() * 100
        whiteMaterial += (position.piecesBB[PieceType.BISHOP.value] and position.colorsBB[Color.WHITE.idx()]).countOneBits() * 300
        whiteMaterial += (position.piecesBB[PieceType.KNIGHT.value] and position.colorsBB[Color.WHITE.idx()]).countOneBits() * 300
        whiteMaterial += (position.piecesBB[PieceType.ROOK.value] and position.colorsBB[Color.WHITE.idx()]).countOneBits() * 500
        whiteMaterial += (position.piecesBB[PieceType.QUEEN.value] and position.colorsBB[Color.WHITE.idx()]).countOneBits() * 1000

        var blackMaterial = 0
        blackMaterial += (position.piecesBB[PieceType.PAWN.value] and position.colorsBB[Color.BLACK.idx()]).countOneBits() * 100
        blackMaterial += (position.piecesBB[PieceType.BISHOP.value] and position.colorsBB[Color.BLACK.idx()]).countOneBits() * 300
        blackMaterial += (position.piecesBB[PieceType.KNIGHT.value] and position.colorsBB[Color.BLACK.idx()]).countOneBits() * 300
        blackMaterial += (position.piecesBB[PieceType.ROOK.value] and position.colorsBB[Color.BLACK.idx()]).countOneBits() * 500
        blackMaterial += (position.piecesBB[PieceType.QUEEN.value] and position.colorsBB[Color.BLACK.idx()]).countOneBits() * 1000

        return (whiteMaterial - blackMaterial) * position.turn.scoreFactor()
    }

    fun qSearch(alpha: Score, beta: Score): Score {
        var alpha = alpha
        var bestScore = evaluate()

        if (bestScore >= beta) return bestScore
        if (bestScore > alpha) alpha = bestScore

        val captures = position.genMoves(capturesOnly = true)

        for (move in captures.sortedWith { moveA, moveB ->
            val vDiff = moveB.capture.type().idx() - moveA.capture.type().idx() // works because they're ordered by importance

            if (vDiff != 0) return@sortedWith vDiff

            return@sortedWith position.pieces[moveA.src.value].type().idx() - position.pieces[moveB.src.value].type().idx()
        }) {
            val stateInfo = position.doMove(move)
            val score = -qSearch(-beta, -alpha)
            position.undoMove(move, stateInfo)

            if (score >= beta)
                return score
            if (score > alpha)
                alpha = score
            if (score > bestScore)
                bestScore = score
        }

        return bestScore
    }

    fun search(remainingDepth: Int, limits: Limits, alpha: Score = -MATE_SCORE, beta: Score = MATE_SCORE): Result {
        if (nodesSearched++ and 4095 == 0L) {
            if (stop) {
                return Result.ABORT
            }
        }

        if (remainingDepth == 0) return Result(Move.NULL_MOVE, qSearch(alpha, beta))

        var alpha = alpha

        val moves = position.genMoves()
        var bestScore: Score = -MATE_SCORE
        var bestMove: Move = Move.NULL_MOVE

        if (moves.isEmpty()) {
            if (position.isColorInCheck(position.turn))
                return Result.CHECKMATED // we got checkmated

            return Result.STALEMATE // stalemate
        }

        for (move in moves) {
            val stateInfo = position.doMove(move)
            val result = search(remainingDepth - 1, limits, -beta, -alpha)
            val score = -result.score
            position.undoMove(move, stateInfo)

            if (result.aborted) return Result.ABORT

            if (score > bestScore) {
                bestScore = score
                bestMove = move
                if (score > alpha) {
                    alpha = score
                }
            }
            if (score >= beta) {
                return Result(bestMove, bestScore)
            }
        }

        return Result(bestMove, bestScore)
    }

    fun iterDeep(limits: Limits): Result {
        var deepestResult = Result(Move.NULL_MOVE, -MATE_SCORE)

        searchStartTime = TimeSource.Monotonic.markNow()
        nodesSearched = 0

        for (d in 1..limits.depth) {
            val result = search(d, limits)

            if (result.aborted) return deepestResult

            deepestResult = result

            val elapsed = TimeSource.Monotonic.markNow() - searchStartTime
            sendUciInfo(d, elapsed, nodesSearched)

            if (elapsed > limits.softTime) {
                return deepestResult
            }
        }

        return deepestResult
    }

    fun perft(depth: Int): Long {
        val moves = position.genMoves()

        if (depth == 1) return moves.size.toLong()

        var nodes = 0L
        for (move in moves) {
            val stateInfo = position.doMove(move)
            nodes += perft(depth - 1)
            position.undoMove(move, stateInfo)
        }
        return nodes
    }

    fun perftDivide(depth: Int): Map<Move, Long> {
        val moves = position.genMoves()

        val results = HashMap<Move, Long>()

        for (move in moves) {
            if (depth == 1) {
                results[move] = 1
            } else {
                val stateInfo = position.doMove(move)
                results[move] = perft(depth - 1)
                position.undoMove(move, stateInfo)
            }
        }

        return results
    }

    companion object {
        const val MATE_SCORE: Score = 32000
    }

    data class Result(val move: Move, val score: Score, val aborted: Boolean = false) {
        companion object {
            val CHECKMATED = Result(Move.NULL_MOVE, -MATE_SCORE)
            val STALEMATE = Result(Move.NULL_MOVE, 0)
            val ABORT = Result(Move.NULL_MOVE, -MATE_SCORE, aborted = true)
        }
    }
}