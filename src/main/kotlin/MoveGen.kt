package party.elias

class MoveGen(val position: Board, val engine: Engine) {
    var stage: Stage = Stage.HASH

    val quietMoves: ScoredMoveContainer = ScoredMoveContainer(128)
    val goodCaptures: ScoredMoveContainer = ScoredMoveContainer(32)
    val badCaptures: ScoredMoveContainer = ScoredMoveContainer(16)
    var currentMoveContainer: ScoredMoveContainer = quietMoves
    var finished: Boolean = false

    var hashMove: Move? = null
    var killerMoves: Array<Move>? = null
    var genQuiets: Boolean = true
    var inCheck: Boolean = false
    var doSEE: Boolean = false

    fun begin(genQuiets: Boolean = true, inCheck: Boolean = false, hashMove: Move? = null, killerMoves: Array<Move>? = null, doSEE: Boolean = false) {
        stage = Stage.HASH

        quietMoves.reset()
        goodCaptures.reset()
        badCaptures.reset()
        currentMoveContainer = quietMoves
        finished = false

        this.hashMove = hashMove
        this.killerMoves = killerMoves
        this.genQuiets = genQuiets
        this.inCheck = inCheck
        this.doSEE = doSEE

        if (inCheck) stage = Stage.EVASION_HASH
    }

    fun nextMove(): Move? {
        while (true) {
            if (finished) return null

            // if there's still at least one buffered move, return it
            if (currentMoveContainer.hasNext()) {

                val move = currentMoveContainer.selectMove(
                    stage.sorted && (!(stage == Stage.GOOD_CAPTURES || stage == Stage.BAD_CAPTURES) || doSEE)
                ).toMove()

                if (!currentMoveContainer.hasNext()) {
                    // if it was the last one, go to next stage
                    nextStage()
                }
                return move
            }

            // there was no buffered move to be returned, so we need to refill the buffer now
            currentMoveContainer.reset()
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
                    currentMoveContainer = quietMoves
                    Bitboards.forAllSquares(
                        position.piecesBB[PieceType.PAWN.idx()] and position.colorsBB[position.turn.idx()]
                                and Bitboards.RANKS[position.turn.opponent().pawnStartingRank()]
                    ) { src ->
                        val front = Square(src.value + PAWN_DIRECTIONS[position.turn.idx()])
                        if (position.pieces[front.value] == Piece.NONE) {
                            val singlePushMove = Move(src, front, Piece.NONE)
                            if (!position.putsCurrentPlayerInCheck(singlePushMove))
                                singlePushMove.forPromotionVariants { m ->
                                    quietMoves.add(m.toCompact())
                                }
                        }
                    }
                }

                Stage.CAPTURES_INIT -> {
                    var attacks = 0L
                    Bitboards.forAllSquares(position.colorsBB[position.turn.idx()]) { square ->
                        val piece = position.pieces[square.value]
                        attacks = attacks or position.attacksOf(square, piece.type(), piece.color())
                    }
                    attacks = attacks and position.colorsBB[position.turn.opponent().idx()]

                    // mvv-lva capture move generation
                    for (victimType in MVV_PIECES) {

                        // squeeze in en passant at the front of all the other x takes pawn captures
                        if (victimType == PieceType.PAWN.idx()) {
                            if (position.epSquare != Square(-1)) {
                                Bitboards.forAllSquares(INDEXED_PAWN_ATTACKS[position.turn.opponent().idx()][position.epSquare.value] and
                                        position.piecesBB[PieceType.PAWN.idx()] and position.colorsBB[position.turn.idx()]
                                ) { src ->
                                    val epMove = Move(src, position.epSquare, Piece(position.turn.opponent(), PieceType.PAWN), isEp = true)

                                    if (!position.putsCurrentPlayerInCheck(epMove)) {
                                        val seeScore = if (doSEE) position.see(epMove).toFloat() else GOOD_CAPTURE_THRESHOLD
                                        (if (seeScore >= GOOD_CAPTURE_THRESHOLD) goodCaptures else badCaptures).addWithScore(
                                            epMove.toCompact(),
                                            seeScore
                                        )
                                    }
                                }
                            }
                        }

                        Bitboards.forAllSquares(position.piecesBB[victimType] and attacks) { victimSquare ->
                            val aggressorBB = position.attackersTargeting(victimSquare, position.turn)
                            for (aggressorType in LVA_PIECES) {
                                Bitboards.forAllSquares(position.piecesBB[aggressorType] and aggressorBB) { aggressorSquare ->
                                    val captureMove = Move(aggressorSquare, victimSquare, position.pieces[victimSquare.value])

                                    if (!position.putsCurrentPlayerInCheck(captureMove)) {
                                        if (aggressorType == PieceType.PAWN.idx() && victimSquare.rank == position.turn.opponent().backRank()) {
                                            captureMove.forPromotionVariants { m ->

                                                val seeScore = if (doSEE) position.see(m).toFloat() else GOOD_CAPTURE_THRESHOLD
                                                (if (seeScore >= GOOD_CAPTURE_THRESHOLD) goodCaptures else badCaptures).addWithScore(
                                                    m.toCompact(),
                                                    seeScore
                                                )
                                            }
                                        } else {
                                            val seeScore = if (doSEE) position.see(captureMove).toFloat() else GOOD_CAPTURE_THRESHOLD
                                            (if (seeScore >= GOOD_CAPTURE_THRESHOLD) goodCaptures else badCaptures).addWithScore(
                                                captureMove.toCompact(),
                                                seeScore
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Stage.GOOD_CAPTURES -> {
                    currentMoveContainer = goodCaptures
                }

                Stage.KILLER -> if (genQuiets && killerMoves != null) {
                    currentMoveContainer = quietMoves
                    for (killer in killerMoves) {
                        if (killer != Move.NULL_MOVE && position.isLegalMove(killer)) {

                            quietMoves.add(killer.toCompact())
                        }
                    }
                }

                Stage.QUIET -> if (genQuiets) {
                    currentMoveContainer = quietMoves

                    // generate castling moves
                    for (i in 0..3) {
                        if (position.castlingRights and Square.CASTLING_ROOK_SQUARES[i].bb() != 0L
                            && Bitboards.CASTLING_EMPTY[i] and position.occupiedBB == 0L
                            && !position.areSquaresAttackedBy(Bitboards.CASTLING_UNATTACKED[i], position.turn.opponent())
                        ) { // cannot be illegal because we already check if king will move into check
                            val castlingMove = Move(Square.KING_STARTS[i / 2], Square.CASTLING_TARGET_SQUARES[i], Piece.NONE, castle = i)

                            quietMoves.add(castlingMove.toCompact())
                        }
                    }

                    // generate missing non-captures
                    Bitboards.forAllSquares(position.colorsBB[position.turn.idx()]) { src ->
                        val piece = position.pieces[src.value]
                        if (piece.type() != PieceType.PAWN) {
                            Bitboards.forAllSquares(
                                position.attacksOf(src, piece.type(), piece.color()) and position.occupiedBB.inv()
                            ) { target ->
                                val move = Move(src, target, Piece.NONE)
                                if (!position.putsCurrentPlayerInCheck(move)) {
                                    val idx = position.turn.idx() * 64 * 64 + move.src.value * 64 + move.dst.value

                                    quietMoves.addWithScore(
                                        move.toCompact(),
                                        if (engine.historyTotal[idx] == 0F) 0F else engine.historyCuts[idx] / engine.historyTotal[idx]
                                    )
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
                                if (!position.putsCurrentPlayerInCheck(doublePushMove)) {
                                    val idx = position.turn.idx() * 64 * 64 + doublePushMove.src.value * 64 + doublePushMove.dst.value

                                    quietMoves.addWithScore(
                                        doublePushMove.toCompact(),
                                        if (engine.historyTotal[idx] == 0F) 0F else engine.historyCuts[idx] / engine.historyTotal[idx]
                                    )
                                }
                            }

                            // generate single push
                            if (!position.putsCurrentPlayerInCheck(singlePushMove)) {
                                val idx = position.turn.idx() * 64 * 64 + singlePushMove.src.value * 64 + singlePushMove.dst.value

                                quietMoves.addWithScore(
                                    singlePushMove.toCompact(),
                                    if (engine.historyTotal[idx] == 0F) 0F else engine.historyCuts[idx] / engine.historyTotal[idx]
                                )
                            }
                        }
                    }
                }

                Stage.BAD_CAPTURES -> {
                    currentMoveContainer = badCaptures
                }

                Stage.EVASION_HASH -> {
                    // take a shortcut for the hash move, it's only ever one move, so we can just return it
                    if (hashMove != null && position.isLegalMove(hashMove!!)) {
                        nextStage()
                        return hashMove
                    }
                }

                Stage.EVASION -> {
                    // not all evasion moves are quiet, but we don't depend on the moves in quietMoves actually being quiet anyway
                    currentMoveContainer = quietMoves
                    genEvasionMoves()
                }
            }

            // check if stage was empty
            if (currentMoveContainer.isEmpty()) {
                // if so, try next stage
                nextStage()
            }
        }
    }

    private fun genEvasionMoves() {
        val kingSquare = position.kingSquares[position.turn.idx()]

        val checkers = position.attackersTargeting(kingSquare, position.turn.opponent())

        if (checkers.countOneBits() == 1) {
            // we're not in double check, so we can capture the checker or interpose a piece
            val checkerSq = Square(checkers.countTrailingZeroBits())

            // capture the checker via en passant
            if (position.epSquare != Square(-1) && position.epSquare.enPassantActualCapture() == checkerSq) {
                val epAttackers: Bitboard =
                    (INDEXED_PAWN_ATTACKS[position.turn.opponent().idx()][position.epSquare.value]
                            and position.piecesBB[PieceType.PAWN.idx()] and position.colorsBB[position.turn.idx()])

                Bitboards.forAllSquares(epAttackers) { src ->
                    // can only use non-pinned pieces
                    if (src.bb() and position.currentKingProtectors == 0L) {
                        currentMoveContainer.add(
                            Move(src, position.epSquare, position.pieces[checkerSq.value], isEp = true).toCompact()
                        )
                    }
                }
            }

            // capture the checker the normal way
            val potentialCapturers = position.attackersTargeting(checkerSq, position.turn)
            Bitboards.forAllSquares(potentialCapturers) { src ->
                // can only use non-pinned pieces and don't use the king, he'll have his chance when generating the king moves
                if (src.bb() and position.currentKingProtectors == 0L && src != kingSquare) {

                    val captureMove = Move(src, checkerSq, position.pieces[checkerSq.value])

                    // if we're moving a pawn to the last rank, we promote
                    if (position.turn.opponent().pawnStartingRank() == src.rank // our pawn moving from opponent's starting rank, means we reach the last rank
                        && position.pieces[src.value].type() == PieceType.PAWN) {
                        captureMove.forPromotionVariants { m ->
                            currentMoveContainer.add(m.toCompact())
                        }
                    } else {
                        currentMoveContainer.add(captureMove.toCompact())
                    }
                }
            }

            // interpose a piece
            if (position.pieces[checkerSq.value].type().isSliding()) {
                Bitboards.forAllSquares(Bitboards.between(kingSquare, checkerSq)) { dst ->
                    Bitboards.forAllSquares(position.moversTargeting(dst, position.turn)) { src ->
                        // can only use non-pinned pieces and not the king
                        if (src.bb() and position.currentKingProtectors == 0L && src != kingSquare) {

                            val interposeMove = Move(src, dst, Piece.NONE)

                            // if we're moving a pawn to the last rank, we promote
                            if (position.turn.opponent().pawnStartingRank() == src.rank // our pawn moving from opponent's starting rank, means we reach the last rank
                                && position.pieces[src.value].type() == PieceType.PAWN) {
                                interposeMove.forPromotionVariants { m ->
                                    currentMoveContainer.add(m.toCompact())
                                }
                            } else {
                                currentMoveContainer.add(interposeMove.toCompact())
                            }
                        }
                    }
                }
            }
        }

        // generate king evasion moves
        val kingEvasionSquares = (KING_ATTACKS[kingSquare.value]
                and position.colorsBB[position.turn.idx()].inv())

        Bitboards.forAllSquares(kingEvasionSquares) { dst ->
            // don't walk into check
            if (!position.areSquaresAttackedBy(
                    dst.bb(),
                    position.turn.opponent(),
                    position.occupiedBB and kingSquare.bb().inv()
                )
            )
                currentMoveContainer.add(Move(kingSquare, dst, position.pieces[dst.value]).toCompact())
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
        const val GOOD_CAPTURE_THRESHOLD = 0F

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
        CAPTURES_INIT,
        GOOD_CAPTURES(true),
        KILLER,
        NC_PROM,
        BAD_CAPTURES,
        QUIET(true),

        EVASION_HASH,
        EVASION;

        fun hasNext(): Boolean {
            return this != QUIET && this != EVASION
        }

        fun next(): Stage {
            return entries[ordinal + 1]
        }
    }

    class ScoredMoveContainer(
        val moves: CompactMoveArray,
        val scores: FloatArray,
        var size: Int,
        var index: Int
    ) {
        constructor(capacity: Int) : this(CompactMoveArray(capacity), FloatArray(capacity), 0, 0)

        fun reset() {
            size = 0
            index = 0
        }

        fun hasNext(): Boolean {
            return index < size
        }

        fun isEmpty(): Boolean {
            return size == 0
        }

        fun add(move: CompactMove) {
            moves[size++] = move
        }

        fun addWithScore(move: CompactMove, score: Float) {
            scores[size] = score
            moves[size++] = move
        }

        fun selectMove(sort: Boolean): CompactMove {
            if (sort) {
                // find best remaining move
                var maxScoreIndex: Int = index
                for (i in (index + 1)..<size) {
                    if (scores[i] > scores[maxScoreIndex]) {
                        maxScoreIndex = i
                    }
                }

                // swap best move with current move
                val swapScore = scores[maxScoreIndex]
                val swapMove = moves[maxScoreIndex]
                scores[maxScoreIndex] = scores[index]
                moves[maxScoreIndex] = moves[index]
                scores[index] = swapScore
                moves[index] = swapMove
            }

            return moves[index++]
        }
    }
}