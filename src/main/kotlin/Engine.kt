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
        if (nodesSearched++ and 4095 == 0L) {
            if (stop) {
                return Result.ABORT
            }
        }

        if (position.isDrawByRepetition()) return Result.draw(plyFromRoot)

        // probe transposition table
        val ttEntry = tt.get(position.zobristHash)
        if (ttEntry != null && plyFromRoot != 0) {
            if (ttEntry.draft >= remainingDepth) {
                val adjustedScore = ttEntry.getAdjustedScore(position.turn, plyFromRoot)
                when (ttEntry.bound) {
                    TranspositionTable.BoundType.EXACT ->
                        return Result(ttEntry.bestMove!!, adjustedScore, ttEntry.draft + plyFromRoot)

                    TranspositionTable.BoundType.LOWER -> if (adjustedScore >= beta)
                        return Result(Move.NULL_MOVE, adjustedScore, ttEntry.draft + plyFromRoot)

                    TranspositionTable.BoundType.UPPER -> if (adjustedScore < alpha)
                        return Result(Move.NULL_MOVE, adjustedScore, ttEntry.draft + plyFromRoot)
                }
            }
        }

        if (remainingDepth == 0) return Result(Move.NULL_MOVE, qSearch(alpha, beta), plyFromRoot)

        var alpha = alpha

        var bestScore: Score = -MATE_SCORE
        var bestMove: Move = Move.NULL_MOVE
        var bestHorizonDepth = 0
        var moveCount = 0

        var alphaRaised = false

        position.forMoves(hashMove = ttEntry?.bestMove) { move ->
            val stateInfo = position.doMove(move)
            val result = search(plyFromRoot + 1, remainingDepth - 1, limits, -beta, -alpha)
            val score = -result.score
            position.undoMove(move, stateInfo)

            if (result.aborted) return Result.ABORT

            if (score > bestScore) {
                bestScore = score
                bestMove = move
                bestHorizonDepth = result.actualHorizonDepth
                if (score > alpha) {
                    alpha = score
                    alphaRaised = true
                }
            }
            if (score >= beta) {
                tt.store(position.zobristHash, bestHorizonDepth - plyFromRoot, position.turn,
                    plyFromRoot, bestScore, TranspositionTable.BoundType.LOWER, move)
                return Result(bestMove, bestScore, bestHorizonDepth)
            }

            moveCount++
        }

        if (moveCount == 0) {
            if (position.isColorInCheck(position.turn))
                return Result.checkmated(plyFromRoot) // we got checkmated

            return Result.draw(plyFromRoot) // stalemate
        }

        if (alphaRaised) { // PV node
            tt.store(position.zobristHash, bestHorizonDepth - plyFromRoot, position.turn,
                plyFromRoot, bestScore, TranspositionTable.BoundType.EXACT, bestMove)
        } else { // all node
            tt.store(position.zobristHash, bestHorizonDepth - plyFromRoot, position.turn,
                plyFromRoot, bestScore, TranspositionTable.BoundType.UPPER)
        }

        return Result(bestMove, bestScore, bestHorizonDepth)
    }

    fun iterDeep(limits: Limits): Result {
        var deepestResult = Result(Move.NULL_MOVE, -MATE_SCORE, 0)

        searchStartTime = TimeSource.Monotonic.markNow()
        nodesSearched = 0
        stop = false

        for (d in 1..limits.depth) {
            val result = search(0, d, limits)

            if (result.aborted) return deepestResult

            deepestResult = result

            val elapsed = TimeSource.Monotonic.markNow() - searchStartTime
            sendUciInfo(d, elapsed, nodesSearched, result.score)

            if (elapsed > limits.softTime) {
                return deepestResult
            }
        }

        return deepestResult
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

    data class Result(val move: Move, val score: Score, val actualHorizonDepth: Int, val aborted: Boolean = false) {
        companion object {
            val ABORT = Result(Move.NULL_MOVE, -MATE_SCORE, 0, aborted = true)

            fun checkmated(depth: Int): Result {
                return Result(Move.NULL_MOVE, -MATE_SCORE + depth, depth)
            }

            fun draw(depth: Int): Result {
                return Result(Move.NULL_MOVE, 0, depth)
            }
        }
    }
}