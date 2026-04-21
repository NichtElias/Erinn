package party.elias

import party.elias.uci.sendUciInfo
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.time.TimeSource

typealias Score = Int

class Engine {

    var position: Board = Board.startPos()
        set(newPos) {
            field = newPos
            moveGens = Array(MAX_SEARCH_PLY) { MoveGen(newPos, this) }
        }

    var moveGens: Array<MoveGen> = Array(MAX_SEARCH_PLY) { MoveGen(position, this) }

    val tt: TranspositionTable = TranspositionTable()

    @Volatile
    var stop: Boolean = false

    var searchStartTime: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow()

    var nodesSearched: Long = 0

    val killers: Array<Array<Move>> = Array(MAX_GAME_PLY) { Array(2) { Move.NULL_MOVE } }

    val historyCuts: FloatArray = FloatArray(2 * 64 * 64) // "normal" history heuristic scores
    val historyTotal: FloatArray = FloatArray(2 * 64 * 64) // used for relative history heuristic

    val pvTable: CompactMoveArray = CompactMoveArray(MAX_SEARCH_PLY * MAX_SEARCH_PLY)
    val pvLength: IntArray = IntArray(MAX_SEARCH_PLY)

    fun evaluate(): Score {
        return Eval.evaluate(position, Eval.EVAL_PARAMETERS) * position.turn.scoreFactor()
    }

    fun qSearch(plyFromRoot: Int, alpha: Score, beta: Score): Score {
        var alpha = alpha
        var bestScore = evaluate()

        if (bestScore >= beta) return bestScore
        if (bestScore > alpha) alpha = bestScore

        val moveGen = moveGens[plyFromRoot]
        moveGen.begin(genQuiets = false, inCheck = position.isColorInCheck(position.turn))

        while (true) {
            val move = moveGen.nextMove() ?: break

            val stateInfo = position.doMove(move)
            val score = -qSearch(plyFromRoot + 1, -beta, -alpha)
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

    fun search(plyFromRoot: Int, remainingDepth: Int, limits: Limits, alpha: Score = -MATE_SCORE, beta: Score = MATE_SCORE, isPV: Boolean = true): Result {
        if (plyFromRoot == 0) {
            pvLength.fill(0)
        }

        var remainingDepth = remainingDepth

        if (nodesSearched++ and 255 == 0L) {
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
                        return Result(ttEntry.bestMove.toMove(), adjustedScore)

                    TranspositionTable.BoundType.LOWER -> if (adjustedScore >= beta && !isPV)
                        return Result(Move.NULL_MOVE, adjustedScore)

                    TranspositionTable.BoundType.UPPER -> if (adjustedScore < alpha && !isPV)
                        return Result(Move.NULL_MOVE, adjustedScore)
                }
            }
        }

        val inCheck = position.isColorInCheck(position.turn)

        if (inCheck && (plyFromRoot < 24 || remainingDepth == 0)) remainingDepth++ // check extension

        if (remainingDepth == 0) return Result(Move.NULL_MOVE, qSearch(plyFromRoot, alpha, beta))

        // null move pruning
        if (!inCheck
            && !isPV
            && remainingDepth > 2
            && position.nonKpPieceCount(position.turn) > 0
        ) {
            var reduction = 2
            if (remainingDepth > 6) reduction = 3

            val stateInfo = position.doNullMove()

            val nullMoveResult = search(plyFromRoot + 1, remainingDepth - reduction - 1, limits, -beta, -beta + 1, false)
            val nullMoveScore = -nullMoveResult.score

            position.undoNullMove(stateInfo)

            if (nullMoveResult.aborted) {
                return Result.ABORT
            }

            // if null move would cause beta cutoff, we can assume the best move we can find in this position would also cause a beta cutoff
            if (nullMoveScore >= beta) {
                return Result(Move.NULL_MOVE, nullMoveScore)
            }
        }

        // set futility pruning flag
        val futilityPruning = (plyFromRoot > 0
                && remainingDepth <= 3
                && !isPV
                && !inCheck
                && abs(alpha) < 9000
                && evaluate() + FUTILITY_MARGINS[remainingDepth] <= alpha)

        var alpha = alpha

        var bestScore: Score = -MATE_SCORE
        var bestMove: Move = Move.NULL_MOVE
        var moveCount = 0
        var prunedMoves = 0

        var alphaRaised = false

        val moveGen = moveGens[plyFromRoot]
        moveGen.begin(inCheck = inCheck, hashMove = ttEntry?.bestMove?.toMove(), killerMoves = killers[plyFromRoot], doSEE = remainingDepth > 2)

        while (true) {
            val move = moveGen.nextMove() ?: break
            moveCount++

            val stateInfo = position.doMove(move)

            if (futilityPruning
                && move.capture == Piece.NONE
                && move.promotion == PieceType.NONE
                && !position.isColorInCheck(position.turn)
            ) {
                position.undoMove(move, stateInfo)
                prunedMoves++
                continue
            }

            var reduction = 0

            if (!isPV && remainingDepth >= 3 && moveCount > 4
                && !inCheck && !position.isColorInCheck(position.turn) // we weren't in check and this move isn't putting the opponent in check
                && !isKiller(move, plyFromRoot)
                && move.capture == Piece.NONE
                && move.promotion == PieceType.NONE
            ) {
                reduction = 1 + ((remainingDepth - 3) / 2)
            }

            var result: Result
            var score: Score

            pvLength[plyFromRoot + 1] = 0
            result = pvs(alphaRaised, plyFromRoot, remainingDepth, reduction, limits, beta, alpha, isPV)
            score = -result.score

            if (reduction > 0 && score > alpha) {
                reduction = 0
                result = pvs(alphaRaised, plyFromRoot, remainingDepth, reduction, limits, beta, alpha, isPV)
                score = -result.score
            }

            position.undoMove(move, stateInfo)

            if (result.aborted) return Result.ABORT

            if (score > bestScore) {
                bestScore = score
                bestMove = move
                if (score > alpha) {
                    alpha = score
                    alphaRaised = true

                    if (isPV) {
                        pvTable[plyFromRoot * MAX_SEARCH_PLY + 0] = move.toCompact()
                        System.arraycopy(
                            pvTable.array, (plyFromRoot + 1) * MAX_SEARCH_PLY,
                            pvTable.array, plyFromRoot * MAX_SEARCH_PLY + 1,
                            pvLength[plyFromRoot + 1]
                        )
                        pvLength[plyFromRoot] = pvLength[plyFromRoot + 1] + 1
                    }
                }
            }

            val historyIndex = position.turn.idx() * 64 * 64 + move.src.value * 64 + move.dst.value
            historyTotal[historyIndex] += remainingDepth + 1
            if (score >= beta) {
                if (move.capture == Piece.NONE && move.promotion == PieceType.NONE) {
                    putKiller(move, plyFromRoot)

                    historyCuts[historyIndex] += remainingDepth + 1
                }

                tt.store(position.zobristHash, remainingDepth, position.turn,
                    plyFromRoot, bestScore, TranspositionTable.BoundType.LOWER, move)
                return Result(bestMove, bestScore)
            }
        }

        if (moveCount == 0) {
            if (inCheck)
                return Result.checkmated(plyFromRoot) // we got checkmated

            return Result.draw() // stalemate
        }

        // we need to return something that isn't a checkmate score if we didn't actually search any moves
        if (moveCount - prunedMoves == 0) return Result(Move.NULL_MOVE, alpha)

        if (alphaRaised) { // PV node
            tt.store(position.zobristHash, remainingDepth, position.turn,
                plyFromRoot, bestScore, TranspositionTable.BoundType.EXACT, bestMove)
        } else { // all node
            tt.store(position.zobristHash, remainingDepth, position.turn,
                plyFromRoot, bestScore, TranspositionTable.BoundType.UPPER)
        }

        return Result(bestMove, bestScore)
    }

    private fun pvs(
        alphaRaised: Boolean,
        plyFromRoot: Int,
        remainingDepth: Int,
        reduction: Int,
        limits: Limits,
        beta: Score,
        alpha: Score,
        isPV: Boolean,
    ): Result {
        var result: Result
        if (!alphaRaised) {
            result = search(plyFromRoot + 1, remainingDepth - reduction - 1, limits, -beta, -alpha, isPV)
        } else {
            result = search(plyFromRoot + 1, remainingDepth - reduction - 1, limits, -alpha - 1, -alpha, false)
            if (-result.score > alpha && !result.aborted) {
                result = search(plyFromRoot + 1, remainingDepth - reduction - 1, limits, -beta, -alpha, true)
            }
        }
        return result
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
            sendUciInfo(d, elapsed, nodesSearched, result.score, getPv())

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

    fun isKiller(move: Move, plyFromRoot: Int): Boolean {
        return killers[plyFromRoot][0] == move || killers[plyFromRoot][1] == move
    }

    fun resetKillers() {
        for (ply in 0..<killers.size) {
            killers[ply][0] = Move.NULL_MOVE
            killers[ply][1] = Move.NULL_MOVE
        }
    }

    fun ageHistory() {
        for (i in 0..<historyCuts.size) {
            historyCuts[i] /= 4
            historyTotal[i] /= 4
        }
    }

    fun resetHistory() {
        for (i in 0..<historyCuts.size) {
            historyCuts[i] = 0F
            historyTotal[i] = 0F
        }
    }

    fun perft(plyFromRoot: Int, depth: Int): Long {
        if (depth == 0) return 1L

        var nodes = 0L
        val moveGen = moveGens[plyFromRoot]
        moveGen.begin(inCheck = position.isColorInCheck(position.turn))

        while (true) {
            val move = moveGen.nextMove() ?: break

            val stateInfo = position.doMove(move)
            nodes += perft(plyFromRoot + 1, depth - 1)
            position.undoMove(move, stateInfo)
        }
        return nodes
    }

    fun perftDivide(depth: Int): Map<Move, Long> {
        val results = HashMap<Move, Long>()

        val moveGen = moveGens[0]
        moveGen.begin(inCheck = position.isColorInCheck(position.turn))

        while (true) {
            val move = moveGen.nextMove() ?: break
            if (depth == 1) {
                results[move] = 1
            } else {
                val stateInfo = position.doMove(move)
                results[move] = perft(1, depth - 1)
                position.undoMove(move, stateInfo)
            }
        }

        return results
    }

    fun getPv(): ArrayList<Move> {
        val pv = ArrayList<Move>()

        for (i in 0..<pvLength[0]) {
            pv.add(pvTable[0 * 48 + i].toMove())
        }

        return pv
    }

    companion object {
        const val MATE_SCORE: Score = 32000
        const val MAX_SEARCH_PLY: Int = 64
        const val MIN_MATE_SCORE: Score = MATE_SCORE - MAX_SEARCH_PLY
        const val MAX_GAME_PLY: Int = 1024 // 512 would probably be enough for most cases, but I've seen some very long bot games

        val FUTILITY_MARGINS = intArrayOf(0, 200, 300, 500)
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