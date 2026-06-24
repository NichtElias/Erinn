package party.elias

import party.elias.uci.sendUciInfo
import java.io.File
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

typealias Score = Int

class Engine {

    var position: Board = Board.startPos()
        set(newPos) {
            field = newPos
            moveGens = Array(MAX_SEARCH_PLY) { MoveGen(newPos, this) }
        }

    var moveGens: Array<MoveGen> = Array(MAX_SEARCH_PLY) { MoveGen(position, this) }

    var tt: TranspositionTable = TranspositionTable(256 * (1 shl 20) / TranspositionTable.ENTRY_SIZE)

    @Volatile
    var stop: Boolean = false

    var searchStartTime: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow()

    var nodesSearched: Long = 0

    val killers: Array<Array<Move>> = Array(MAX_GAME_PLY) { Array(2) { Move.NULL_MOVE } }

    val historyCuts: FloatArray = FloatArray(2 * 64 * 64) // "normal" history heuristic scores
    val historyTotal: FloatArray = FloatArray(2 * 64 * 64) // used for relative history heuristic

    val pvTable: CompactMoveArray = CompactMoveArray(MAX_SEARCH_PLY * MAX_SEARCH_PLY)
    val pvLength: IntArray = IntArray(MAX_SEARCH_PLY)

    val accStack: AccumulatorStack = AccumulatorStack()

    var printInfo: Boolean = true

    var debugMode: Boolean = false

    var totalTTHits: Long = 0
    var totalSearchedNodes: Long = 0
    var ttBestMoveCount: Long = 0
    var captureBestMoveCount: Long = 0 // capture but not tt move
    var killerBestMoveCount: Long = 0
    var otherBestMoveCount: Long = 0

    fun evaluate(plyFromRoot: Int): Score {
        val pieceCount = position.occupiedBB.countOneBits()
        val accPair = accStack.stack[plyFromRoot]

        accStack.updateAccAt(plyFromRoot)

        return if (position.turn == Color.WHITE)
            NNUE.evaluate(accPair.white.contents, accPair.black.contents, pieceCount)
        else
            NNUE.evaluate(accPair.black.contents, accPair.white.contents, pieceCount)
    }

    fun doMoveWithAccUpdate(plyFromRoot: Int, move: Move): Board.StateInfo {
        val doFullRefresh = accStack.preDoMove(plyFromRoot, move, position)
        val stateInfo = position.doMove(move)
        accStack.postDoMove(plyFromRoot, position, doFullRefresh)

        return stateInfo
    }

    fun doNullMoveWithAccUpdate(plyFromRoot: Int): Board.StateInfo {
        accStack.preDoNullMove(plyFromRoot)
        return position.doNullMove()
    }

    fun qSearch(plyFromRoot: Int, alpha: Score, beta: Score): Score {
        var alpha = alpha
        val staticEval = evaluate(plyFromRoot)
        var bestScore = staticEval

        if (bestScore >= beta) return bestScore
        if (bestScore > alpha) alpha = bestScore

        val inCheck = position.isColorInCheck(position.turn)
        val moveGen = moveGens[plyFromRoot]
        moveGen.begin(genQuiets = false, inCheck = inCheck)

        while (true) {
            val move = moveGen.nextMove() ?: break

            // delta pruning
            if (!inCheck && staticEval + PieceType.VALUES[move.capture.type().idx()] + 210 <= alpha) {
                continue
            }

            val stateInfo = doMoveWithAccUpdate(plyFromRoot, move)
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

        if (position.isDrawByRepetition() || position.halfMoves >= 100 || position.isDrawByInsufficientMaterial()) return Result.draw()

        // probe transposition table
        val ttEntry = tt.get(position.zobristHash)
        if (ttEntry != null && plyFromRoot != 0) {
            if (ttEntry.draft >= remainingDepth) {
                val adjustedScore = ttEntry.getAdjustedScore(position.turn, plyFromRoot)
                when (ttEntry.bound) {
                    TranspositionTable.BOUND_EXACT ->
                        return Result(ttEntry.bestMove.toMove(), adjustedScore)

                    TranspositionTable.BOUND_LOWER -> if (adjustedScore >= beta && !isPV)
                        return Result(Move.NULL_MOVE, adjustedScore)

                    TranspositionTable.BOUND_UPPER -> if (adjustedScore < alpha && !isPV)
                        return Result(Move.NULL_MOVE, adjustedScore)
                }
            }
        }

        val inCheck = position.isColorInCheck(position.turn)

        if (inCheck && (plyFromRoot < 24 || remainingDepth == 0)) remainingDepth++ // check extension

        if (remainingDepth == 0) return Result(Move.NULL_MOVE, qSearch(plyFromRoot, alpha, beta))

        // reverse futility pruning
        var staticEval: Score = 0
        var hasStaticEval = false
        if (!isPV && !inCheck && remainingDepth < 6) {
            staticEval = evaluate(plyFromRoot)
            hasStaticEval = true
            if (staticEval >= beta + 150 * remainingDepth) {
                return Result(Move.NULL_MOVE, staticEval)
            }
        }

        // null move pruning
        if (!inCheck
            && !isPV
            && remainingDepth > 2
            && position.nonKpPieceCount(position.turn) > 0
        ) {
            var reduction = 2
            if (remainingDepth > 6) reduction = 3

            val stateInfo = doNullMoveWithAccUpdate(plyFromRoot)

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
                && (if (hasStaticEval) staticEval else evaluate(plyFromRoot)) + FUTILITY_MARGINS[remainingDepth] <= alpha)

        var alpha = alpha

        var bestScore: Score = -MATE_SCORE
        var bestMove: Move = Move.NULL_MOVE
        var moveCount = 0
        var prunedMoves = 0

        var firstMoveWasBestMove = true
        var firstIsKiller = false

        var alphaRaised = false

        val moveGen = moveGens[plyFromRoot]
        moveGen.begin(inCheck = inCheck, hashMove = ttEntry?.bestMove?.toMove(), killerMoves = killers[plyFromRoot], doSEE = remainingDepth > 2)

        while (true) {
            val move = moveGen.nextMove() ?: break
            moveCount++

            if (debugMode && moveCount == 1 && (move == killers[plyFromRoot][0] || move == killers[plyFromRoot][1])) {
                firstIsKiller = true
            }

            val putsInCheck = position.putsOpponentInCheck(move)

            if (futilityPruning
                && move.capture == Piece.NONE
                && move.promotion == PieceType.NONE
                && !putsInCheck
            ) {
                prunedMoves++
                continue
            }

            val stateInfo = doMoveWithAccUpdate(plyFromRoot, move)

            var reduction = 0

            if (!isPV && remainingDepth >= 3 && moveCount > 4
                && !inCheck && !putsInCheck // we weren't in check and this move isn't putting the opponent in check
                && !isKiller(move, plyFromRoot)
                && move.capture == Piece.NONE
                && move.promotion == PieceType.NONE
            ) {
                reduction = 1 + ((remainingDepth - 3) / 2)
            }

            var result: Result
            var score: Score

            pvLength[plyFromRoot + 1] = 0
            result = pvs(moveCount, plyFromRoot, remainingDepth, reduction, limits, beta, alpha, isPV)
            score = -result.score

            if (reduction > 0 && score > alpha) {
                reduction = 0
                result = pvs(moveCount, plyFromRoot, remainingDepth, reduction, limits, beta, alpha, isPV)
                score = -result.score
            }

            position.undoMove(move, stateInfo)

            if (result.aborted) return Result.ABORT

            if (plyFromRoot == 0 && moveCount == 1 && score <= alpha) {
                return Result(move, score)
            }

            if (score > bestScore) {
                if (debugMode && moveCount > 1) {
                    firstMoveWasBestMove = false
                }

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

                collectSearchStats(ttEntry, firstMoveWasBestMove, bestMove, firstIsKiller)

                tt.store(position.zobristHash, remainingDepth, position.turn,
                    plyFromRoot, bestScore, TranspositionTable.BOUND_LOWER, move)
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

        collectSearchStats(ttEntry, firstMoveWasBestMove, bestMove, firstIsKiller)

        if (alphaRaised) { // PV node
            tt.store(position.zobristHash, remainingDepth, position.turn,
                plyFromRoot, bestScore, TranspositionTable.BOUND_EXACT, bestMove)
        } else { // all node
            tt.store(position.zobristHash, remainingDepth, position.turn,
                plyFromRoot, bestScore, TranspositionTable.BOUND_UPPER, bestMove)
        }

        return Result(bestMove, bestScore)
    }

    private fun pvs(
        moveCount: Int,
        plyFromRoot: Int,
        remainingDepth: Int,
        reduction: Int,
        limits: Limits,
        beta: Score,
        alpha: Score,
        isPV: Boolean,
    ): Result {
        var result: Result
        if (moveCount == 1) {
            result = search(plyFromRoot + 1, remainingDepth - reduction - 1, limits, -beta, -alpha, isPV)
        } else {
            result = search(plyFromRoot + 1, remainingDepth - reduction - 1, limits, -alpha - 1, -alpha, false)
            if (-result.score > alpha && -result.score < beta && !result.aborted) {
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

        accStack.init(position)

        resetSearchStats()

        for (d in 1..limits.depth) {
            var delta = 15
            var windowAlpha = deepestResult.score - delta
            var windowBeta = deepestResult.score + delta

            var result: Result

            while (true) {
                result = search(0, d, limits, windowAlpha, windowBeta)

                if (result.aborted) return deepestResult

                if (result.score <= windowAlpha) {
                    // fail-low
                    windowAlpha -= delta
                    delta += delta
                    continue
                }
                if (result.score >= windowBeta) {
                    // fail-high
                    windowBeta += delta
                    delta += delta
                    continue
                }

                break
            }

            deepestResult = result

            val elapsed = TimeSource.Monotonic.markNow() - searchStartTime
            if (printInfo)
                sendUciInfo(d, elapsed, nodesSearched, result.score, getPv(), tt.fullPerMill())

            if (elapsed > limits.softTime || (abs(result.score) >= MIN_MATE_SCORE && MATE_SCORE - abs(result.score) < d)) {
                return deepestResult
            }
        }

        if (debugMode) {
            print("info string first move hits: tt ${ttBestMoveCount.toFloat() / totalSearchedNodes * 100}%/${totalTTHits.toFloat() / totalSearchedNodes * 100}%, ")
            print("capture ${captureBestMoveCount.toFloat() / totalSearchedNodes * 100}%, ")
            print("killer ${killerBestMoveCount.toFloat() / totalSearchedNodes * 100}%, ")
            println("other ${otherBestMoveCount.toFloat() / totalSearchedNodes * 100}%")
        }

        return deepestResult
    }

    private fun collectSearchStats(
        ttEntry: TranspositionTable.Entry?,
        firstMoveWasBestMove: Boolean,
        bestMove: Move,
        firstIsKiller: Boolean
    ) {
        if (debugMode) {
            totalSearchedNodes++
            if (ttEntry != null) totalTTHits++
            if (firstMoveWasBestMove) {
                if (ttEntry != null && ttEntry.bestMove == bestMove.toCompact()) {
                    ttBestMoveCount++
                } else if (bestMove.capture != Piece.NONE) {
                    captureBestMoveCount++
                } else if (firstIsKiller) {
                    killerBestMoveCount++
                } else {
                    otherBestMoveCount++
                }
            }
        }
    }

    private fun resetSearchStats() {
        totalSearchedNodes = 0
        ttBestMoveCount = 0
        captureBestMoveCount = 0
        killerBestMoveCount = 0
        otherBestMoveCount = 0
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
        for (killerPair in killers) {
            killerPair[0] = Move.NULL_MOVE
            killerPair[1] = Move.NULL_MOVE
        }
    }

    fun ageHistory() {
        for (i in historyCuts.indices) {
            historyCuts[i] /= 4
            historyTotal[i] /= 4
        }
    }

    fun resetHistory() {
        for (i in historyCuts.indices) {
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

    fun genEvalPosFromSelfPlayGame(searchDepth: Int, file: File): Int {

        val positions: ArrayList<ByteArray> = ArrayList()
        var result: Float

        while (true) {
            // test if game is draw
            if (position.isDrawByRepetition() || position.halfMoves >= 100 || position.isDrawByInsufficientMaterial()) {
                result = 0.5f
                break
            }

            val searchResult = iterDeep(Limits(searchDepth))

            val inCheck = position.isColorInCheck(position.turn)

            // search only returns null move when no moves are possible, so stalemate or checkmate
            if (searchResult.move == Move.NULL_MOVE) {
                result = if (inCheck) (if (position.turn == Color.WHITE) 0f else 1f) else 0.5f
                break
            }

            // check for quietness
            if (!inCheck && searchResult.move.capture == Piece.NONE) {
                val binPos =
                    position.toBinaryPosition(
                        scoreToWdl(if (position.turn == Color.WHITE) searchResult.score else -searchResult.score),
                        0f
                    )

                positions.add(binPos)
            }

            // make move
            position.doMove(searchResult.move)
        }

        for (position in positions) {
            val resultWdl16 = java.lang.Float.floatToFloat16(result)
            position[2] = ((resultWdl16.toInt() and 0xFFFF) ushr 8).toByte()
            position[3] = (resultWdl16.toInt() and 0xFF).toByte()

            file.appendBytes(position)
        }

        return positions.size
    }

    fun genEvalPosFromSelfPlayGames(seed: Int, searchDepth: Int, games: Int, file: File) {
        val rng = Random(seed)

        var positionsGenerated = 0

        var lastInfoPrint = TimeSource.Monotonic.markNow()

        val prevPrintInfoState = printInfo
        printInfo = false

        for (i in 0..<games) {
            tt.clear()
            position = Board.startPos()
            // amount of random moves played is 4 to 10 but heavily skewed to the higher end
            makeRandomMoves(rng, rng.nextInt(rng.nextInt(4, 11), 11), 10)

            positionsGenerated += genEvalPosFromSelfPlayGame(searchDepth, file)

            val currentTimeStamp = TimeSource.Monotonic.markNow()
            if (currentTimeStamp - lastInfoPrint > 5.seconds) {
                lastInfoPrint = currentTimeStamp

                println("generated $positionsGenerated positions")
            }
        }
        println("generated $positionsGenerated positions")

        printInfo = prevPrintInfoState
    }

    fun getPv(): ArrayList<Move> {
        val pv = ArrayList<Move>()

        for (i in 0..<pvLength[0]) {
            pv.add(pvTable[0 * 48 + i].toMove())
        }

        return pv
    }

    fun setHashTableSize(sizeInMiB: Int) {
        tt = TranspositionTable(sizeInMiB * (1 shl 20) / TranspositionTable.ENTRY_SIZE)
    }

    fun makeRandomMoves(rng: Random, minMoves: Int, maxMoves: Int) {
        for (m in 0..<rng.nextInt(minMoves, maxMoves + 1)) {
            val allMoves = ArrayList<Move>()

            // moveGens[0] used for all moves, as they're generated and made sequentially
            moveGens[0].begin(inCheck = position.isColorInCheck(position.turn))

            while (true) {
                val move = moveGens[0].nextMove() ?: break
                allMoves.add(move)
            }

            if (allMoves.isEmpty()) break

            position.doMove(allMoves[rng.nextInt(allMoves.size)])
        }
    }

    companion object {
        const val MATE_SCORE: Score = 32000
        const val MAX_SEARCH_PLY: Int = 64
        const val MIN_MATE_SCORE: Score = MATE_SCORE - MAX_SEARCH_PLY
        const val MAX_GAME_PLY: Int = 1024 // 512 would probably be enough for most cases, but I've seen some very long bot games

        val FUTILITY_MARGINS = intArrayOf(0, 200, 300, 500)

        fun scoreToWdl(score: Score): Float {
            if (abs(score) >= MIN_MATE_SCORE) {
                return if (score > 0) 1F else 0F
            }

            return 1F / (1F + exp(-score  / 400F))
        }
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