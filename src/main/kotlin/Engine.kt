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

    val killers: Array<Array<Move>> = Array(MAX_GAME_PLY) { Array(2) { Move.NULL_MOVE } }

    fun evaluate(): Score {
        return Eval.evaluate(position, Eval.EVAL_PARAMETERS) * position.turn.scoreFactor()
    }

    fun qSearch(alpha: Score, beta: Score): Score {
        var alpha = alpha
        var bestScore = evaluate()

        if (bestScore >= beta) return bestScore
        if (bestScore > alpha) alpha = bestScore

        position.forMoves(capturesOnly = true) { move ->
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

    fun search(plyFromRoot: Int, remainingDepth: Int, limits: Limits, alpha: Score = -MATE_SCORE, beta: Score = MATE_SCORE): Result {
        var remainingDepth = remainingDepth

        if (nodesSearched++ and 4095 == 0L) {
            if (stop) {
                return Result.ABORT
            }
        }

        if (position.isDrawByRepetition()) return Result.draw()

        // probe transposition table
        val ttEntry = tt.get(position.zobristHash)
        if (ttEntry != null && plyFromRoot != 0) {
            if (ttEntry.draft >= remainingDepth) {
                val adjustedScore = ttEntry.getAdjustedScore(position.turn, plyFromRoot)
                when (ttEntry.bound) {
                    TranspositionTable.BoundType.EXACT ->
                        return Result(ttEntry.bestMove!!, adjustedScore)

                    TranspositionTable.BoundType.LOWER -> if (adjustedScore >= beta)
                        return Result(Move.NULL_MOVE, adjustedScore)

                    TranspositionTable.BoundType.UPPER -> if (adjustedScore < alpha)
                        return Result(Move.NULL_MOVE, adjustedScore)
                }
            }
        }

        val inCheck = position.isColorInCheck(position.turn)

        if (inCheck && remainingDepth == 0) remainingDepth++ // check extension

        if (remainingDepth == 0) return Result(Move.NULL_MOVE, qSearch(alpha, beta))

        var alpha = alpha

        var bestScore: Score = -MATE_SCORE
        var bestMove: Move = Move.NULL_MOVE
        var moveCount = 0

        var alphaRaised = false

        position.forMoves(hashMove = ttEntry?.bestMove, killerMoves = killers[plyFromRoot]) { move ->
            val stateInfo = position.doMove(move)
            val result = search(plyFromRoot + 1, remainingDepth - 1, limits, -beta, -alpha)
            val score = -result.score
            position.undoMove(move, stateInfo)

            if (result.aborted) return Result.ABORT

            if (score > bestScore) {
                bestScore = score
                bestMove = move
                if (score > alpha) {
                    alpha = score
                    alphaRaised = true
                }
            }
            if (score >= beta) {
                if (move.capture == Piece.NONE && move.promotion == PieceType.NONE) {
                    putKiller(move, plyFromRoot)
                }

                tt.store(position.zobristHash, remainingDepth, position.turn,
                    plyFromRoot, bestScore, TranspositionTable.BoundType.LOWER, move)
                return Result(bestMove, bestScore)
            }

            moveCount++
        }

        if (moveCount == 0) {
            if (inCheck)
                return Result.checkmated(plyFromRoot) // we got checkmated

            return Result.draw() // stalemate
        }

        if (alphaRaised) { // PV node
            tt.store(position.zobristHash, remainingDepth, position.turn,
                plyFromRoot, bestScore, TranspositionTable.BoundType.EXACT, bestMove)
        } else { // all node
            tt.store(position.zobristHash, remainingDepth, position.turn,
                plyFromRoot, bestScore, TranspositionTable.BoundType.UPPER)
        }

        return Result(bestMove, bestScore)
    }

    fun iterDeep(limits: Limits): Result {
        var deepestResult = Result(Move.NULL_MOVE, -MATE_SCORE)

        searchStartTime = TimeSource.Monotonic.markNow()
        nodesSearched = 0
        stop = false

        for (d in 1..limits.depth) {
            val result = search(0, d, limits)

            if (result.aborted) return deepestResult

            deepestResult = result

            val elapsed = TimeSource.Monotonic.markNow() - searchStartTime
            sendUciInfo(d, elapsed, nodesSearched, result.score, deepestResult.move)

            if (elapsed > limits.softTime) {
                return deepestResult
            }
        }

        return deepestResult
    }

    fun putKiller(move: Move, plyFromRoot: Int) {
        if (move != killers[plyFromRoot][0]) {
            killers[plyFromRoot][1] = killers[plyFromRoot][0]
        }
        killers[plyFromRoot][0] = move
    }

    fun resetKillers() {
        for (ply in 0..<killers.size) {
            killers[ply][0] = Move.NULL_MOVE
            killers[ply][1] = Move.NULL_MOVE
        }
    }

    fun perft(depth: Int): Long {
        if (depth == 0) return 1L

        var nodes = 0L
        position.forMoves { move ->
            val stateInfo = position.doMove(move)
            nodes += perft(depth - 1)
            position.undoMove(move, stateInfo)
        }
        return nodes
    }

    fun perftDivide(depth: Int): Map<Move, Long> {
        val results = HashMap<Move, Long>()

        position.forMoves { move ->
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
        const val MAX_SEARCH_PLY: Int = 128
        const val MIN_MATE_SCORE: Score = MATE_SCORE - MAX_SEARCH_PLY
        const val MAX_GAME_PLY: Int = 1024 // 512 would probably be enough for most cases, but I've seen some very long bot games
    }

    data class Result(val move: Move, val score: Score, val aborted: Boolean = false) {
        companion object {
            val ABORT = Result(Move.NULL_MOVE, -MATE_SCORE,  aborted = true)

            fun checkmated(depth: Int): Result {
                return Result(Move.NULL_MOVE, -MATE_SCORE + depth)
            }

            fun draw(): Result {
                return Result(Move.NULL_MOVE, 0)
            }
        }
    }
}