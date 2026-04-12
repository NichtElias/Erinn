package party.elias

import kotlin.math.sign

class MoveGen(val position: Board, val engine: Engine) {
    var stage: Stage = Stage.HASH

    val moves: CompactMoveArray = CompactMoveArray(128)
    val moveScores: FloatArray = FloatArray(128) // used for move sorting, not actual centipawn scores
    var bufferedMoves: Int = 0
    var moveIndex: Int = 0
    var finished: Boolean = false

    var hashMove: Move? = null
    var killerMoves: Array<Move>? = null
    var genQuiets: Boolean = true

    fun begin(genQuiets: Boolean = true, hashMove: Move? = null, killerMoves: Array<Move>? = null) {
        stage = Stage.HASH
        bufferedMoves = 0
        moveIndex = 0
        finished = false
        this.hashMove = hashMove
        this.killerMoves = killerMoves
        this.genQuiets = genQuiets
    }

    fun nextMove(): Move? {
        while (true) {
            if (finished) return null

            // if there's still at least one buffered move, return it
            if (moveIndex < bufferedMoves) {

                if (stage.sorted) {
                    // find best remaining move
                    var maxScoreIndex: Int = moveIndex
                    for (i in (moveIndex + 1)..<bufferedMoves) {
                        if (moveScores[i] > moveScores[maxScoreIndex]) {
                            maxScoreIndex = i
                        }
                    }

                    // swap best move with current move
                    val swapScore = moveScores[maxScoreIndex]
                    val swapMove = moves[maxScoreIndex]
                    moveScores[maxScoreIndex] = moveScores[moveIndex]
                    moves[maxScoreIndex] = moves[moveIndex]
                    moveScores[moveIndex] = swapScore
                    moves[moveIndex] = swapMove
                }

                val move = moves[moveIndex++].toMove()
                if (moveIndex == bufferedMoves) {
                    // if it was the last one, go to next stage
                    nextStage()
                }
                return move
            }

            // there was no buffered move to be returned, so we need to refill the buffer now
            bufferedMoves = 0
            moveIndex = 0
            when (stage) {
                Stage.HASH -> {
                    // take a shortcut for the hash move, it's only ever one move, so we can just return it
                    if (hashMove != null && position.isLegalMove(hashMove!!)) {
                        nextStage()
                        return hashMove
                    }
                }

                // non-capture promotions
                Stage.NC_PROM -> if (genQuiets) {
                    Bitboards.forAllSquares(
                        position.piecesBB[PieceType.PAWN.idx()] and position.colorsBB[position.turn.idx()]
                                and Bitboards.RANKS[position.turn.opponent().pawnStartingRank()]
                    ) { src ->
                        val front = Square(src.value + PAWN_DIRECTIONS[position.turn.idx()])
                        if (position.pieces[front.value] == Piece.NONE) {
                            val singlePushMove = Move(src, front, Piece.NONE)
                            if (!position.isInCheckAfter(singlePushMove))
                                singlePushMove.forPromotionVariants { m ->
                                    moves[bufferedMoves++] = m.toCompact()
                                }
                        }
                    }
                }

                Stage.CAPTURES -> {
                    var attacks = 0L
                    Bitboards.forAllSquares(position.colorsBB[position.turn.idx()]) { square ->
                        val piece = position.pieces[square.value]
                        attacks = attacks or position.attacksOf(square, piece.type(), piece.color())
                    }
                    attacks = attacks and position.colorsBB[position.turn.opponent().idx()]

                    // mvv-lva capture move generation
                    for (victimType in MoveGen.MVV_PIECES) {

                        // squeeze in en passant at the front of all the other x takes pawn captures
                        if (victimType == PieceType.PAWN.idx()) {
                            if (position.epSquare != Square(-1)) {
                                Bitboards.forAllSquares(INDEXED_PAWN_ATTACKS[position.turn.opponent().idx()][position.epSquare.value] and
                                        position.piecesBB[PieceType.PAWN.idx()] and position.colorsBB[position.turn.idx()]
                                ) { src ->
                                    val epMove = Move(src, position.epSquare, Piece(position.turn.opponent(), PieceType.PAWN), isEp = true)
                                    if (!position.isInCheckAfter(epMove))
                                        moves[bufferedMoves++] = epMove.toCompact()
                                }
                            }
                        }

                        Bitboards.forAllSquares(position.piecesBB[victimType] and attacks) { victimSquare ->
                            val aggressorBB = position.attackersTargeting(victimSquare, position.turn)
                            for (aggressorType in LVA_PIECES) {
                                Bitboards.forAllSquares(position.piecesBB[aggressorType] and aggressorBB) { aggressorSquare ->
                                    val captureMove = Move(aggressorSquare, victimSquare, position.pieces[victimSquare.value])

                                    if (!position.isInCheckAfter(captureMove)) {
                                        if (aggressorType == PieceType.PAWN.idx() && victimSquare.rank == position.turn.opponent().backRank()) {
                                            captureMove.forPromotionVariants { m ->
                                                moves[bufferedMoves++] = m.toCompact()
                                            }
                                        } else {
                                            moves[bufferedMoves++] = captureMove.toCompact()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Stage.KILLER -> if (genQuiets && killerMoves != null) {
                    for (killer in killerMoves) {
                        if (killer != Move.NULL_MOVE && position.isLegalMove(killer)) {
                            moves[bufferedMoves++] = killer.toCompact()
                        }
                    }
                }

                Stage.CASTLE -> if (genQuiets) {
                    for (i in 0..3) {
                        if (position.castlingRights and Square.CASTLING_ROOK_SQUARES[i].bb() != 0L
                            && Bitboards.CASTLING_EMPTY[i] and position.occupiedBB == 0L
                            && !position.areSquaresAttackedBy(Bitboards.CASTLING_UNATTACKED[i], position.turn.opponent())
                        ) { // cannot be illegal because we already check if king will move into check
                            val castlingMove = Move(Square.KING_STARTS[i / 2], Square.CASTLING_TARGET_SQUARES[i], Piece.NONE, castle = i)
                            moves[bufferedMoves++] = castlingMove.toCompact()
                        }
                    }
                }

                Stage.QUIET -> if (genQuiets) {
                    // generate missing non-captures
                    Bitboards.forAllSquares(position.colorsBB[position.turn.idx()]) { src ->
                        val piece = position.pieces[src.value]
                        if (piece.type() != PieceType.PAWN) {
                            Bitboards.forAllSquares(
                                position.attacksOf(src, piece.type(), piece.color()) and position.occupiedBB.inv()
                            ) { target ->
                                val move = Move(src, target, Piece.NONE)
                                if (!position.isInCheckAfter(move)) {
                                    val idx = position.turn.idx() * 64 * 64 + move.src.value * 64 + move.dst.value

                                    moveScores[bufferedMoves] = if (engine.historyTotal[idx] == 0F) 0F else engine.historyCuts[idx] / engine.historyTotal[idx]
                                    moves[bufferedMoves++] = move.toCompact()
                                }
                            }
                        }
                    }

                    // generate pawn non-capture non-promotion moves
                    Bitboards.forAllSquares(position.piecesBB[PieceType.PAWN.idx()] and position.colorsBB[position.turn.idx()]
                            and Bitboards.PAWN_NON_PROMOTION_AREAS[position.turn.idx()]) { src ->
                        val front = Square(src.value + MoveGen.PAWN_DIRECTIONS[position.turn.idx()])
                        if (position.pieces[front.value] == Piece.NONE) {
                            val singlePushMove = Move(src, front, Piece.NONE)

                            // generate double push
                            val doublePushSquare = Square(src.value + 2 * MoveGen.PAWN_DIRECTIONS[position.turn.idx()])
                            if (src.rank == position.turn.pawnStartingRank() && position.pieces[doublePushSquare.value] == Piece.NONE) {
                                val doublePushMove = Move(src, doublePushSquare, Piece.NONE)
                                if (!position.isInCheckAfter(doublePushMove)) {
                                    val idx = position.turn.idx() * 64 * 64 + doublePushMove.src.value * 64 + doublePushMove.dst.value

                                    moveScores[bufferedMoves] = if (engine.historyTotal[idx] == 0F) 0F else engine.historyCuts[idx] / engine.historyTotal[idx]
                                    moves[bufferedMoves++] = doublePushMove.toCompact()
                                }
                            }

                            // generate single push
                            if (!position.isInCheckAfter(singlePushMove)) {
                                val idx = position.turn.idx() * 64 * 64 + singlePushMove.src.value * 64 + singlePushMove.dst.value

                                moveScores[bufferedMoves] = if (engine.historyTotal[idx] == 0F) 0F else engine.historyCuts[idx] / engine.historyTotal[idx]
                                moves[bufferedMoves++] = singlePushMove.toCompact()
                            }
                        }
                    }
                }
            }

            // check if stage was empty
            if (bufferedMoves == 0) {
                // if so, try next stage
                nextStage()
            }
        }
    }

    fun nextStage() {
        if (stage.hasNext()) {
            stage = stage.next()
            return
        }
        finished = true
    }

    companion object {
        val KNIGHT_ATTACKS: LongArray = LongArray(64) { idx ->
            relativeMoves(
                Square(idx), arrayOf(
                    Pair(1, 2), Pair(2, 1), Pair(-1, 2), Pair(-2, 1),
                    Pair(1, -2), Pair(2, -1), Pair(-1, -2), Pair(-2, -1)
                )
            )
        }

        val KING_ATTACKS: LongArray = LongArray(64) { idx ->
            relativeMoves(
                Square(idx), arrayOf(
                    Pair(1, 1), Pair(1, 0), Pair(1, -1), Pair(0, 1),
                    Pair(0, -1), Pair(-1, 1), Pair(-1, 0), Pair(-1, -1)
                )
            )
        }

        private fun relativeMoves(sq: Square, relativeMovements: Array<Pair<Int, Int>>): Long {
            var bb = 0L

            for ((ro, fo) in relativeMovements) {
                val rank = sq.rank + ro
                val file = sq.file + fo
                if (rank in 0..7 && file in 0..7) {
                    bb = bb or Square(rank, file).bb()
                }
            }

            return bb
        }

        val WHITE_PAWN_ATTACKS: LongArray = LongArray(64) { idx -> pawnAttacks(Square(idx), Color.WHITE) }
        val BLACK_PAWN_ATTACKS: LongArray = LongArray(64) { idx -> pawnAttacks(Square(idx), Color.BLACK) }

        val INDEXED_PAWN_ATTACKS: Array<LongArray> = arrayOf(BLACK_PAWN_ATTACKS, WHITE_PAWN_ATTACKS)

        private fun pawnAttacks(sq: Square, color: Color): Long {
            var bb = 0L

            val movingDirection = if (color == Color.BLACK) -1 else 1

            if (sq.rank != color.opponent().backRank()) {
                if (sq.file < 7) {
                    bb = bb or Square(sq.rank + movingDirection, sq.file + 1).bb()
                }
                if (sq.file > 0) {
                    bb = bb or Square(sq.rank + movingDirection, sq.file - 1).bb()
                }
            }

            return bb
        }

        val ROOK_RELATIVE_MOVEMENTS: Array<Pair<Int, Int>> = arrayOf(Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1))
        val BISHOP_RELATIVE_MOVEMENTS: Array<Pair<Int, Int>> =
            arrayOf(Pair(1, 1), Pair(-1, 1), Pair(1, -1), Pair(-1, -1))

        val ROOK_ATTACK_MASKS: LongArray =
            LongArray(64) { idx -> slidingMoves(Square(idx), 0L, ROOK_RELATIVE_MOVEMENTS) }
        val BISHOP_ATTACK_MASKS: LongArray =
            LongArray(64) { idx -> slidingMoves(Square(idx), 0L, BISHOP_RELATIVE_MOVEMENTS) }

        val PAWN_DIRECTIONS: IntArray = intArrayOf(-8, 8) // these are square offsets, indexed by color

        // most valuable victim order; does not include king as king can never be in a position where it would be captured (because that would be checkmate)
        val MVV_PIECES: IntArray = intArrayOf(4, 3, 1, 2, 0) // these are piece type values
        val MVV_ORDER: IntArray = intArrayOf(5, 3, 4, 2, 1, 0) // these are values used for sorting

        // least valuable aggressor order; this one includes king
        val LVA_PIECES: IntArray = intArrayOf(0, 2, 1, 3, 4, 5)
        val LVA_ORDER: IntArray = intArrayOf(0, 2, 1, 3, 4, 5)

        fun slidingMoves(sq: Square, blockers: Long, relativeMovements: Array<Pair<Int, Int>>): Long {
            var bb = 0L

            for ((ro, fo) in relativeMovements) {
                for (i in 1..7) {
                    val rank = sq.rank + ro * i
                    val file = sq.file + fo * i

                    if (!(rank in 0..7 && file in 0..7))
                        break

                    val dst = Square(rank, file)
                    bb = bb or dst.bb()

                    if (dst.bb() and blockers != 0L)
                        break
                }
            }

            return bb
        }
    }

    enum class Stage(val sorted: Boolean = false) {
        HASH,
        NC_PROM,
        CAPTURES,
        KILLER,
        CASTLE,
        QUIET(true);

        fun hasNext(): Boolean {
            return entries.size > (ordinal + 1)
        }

        fun next(): Stage {
            return entries[ordinal + 1]
        }
    }
}