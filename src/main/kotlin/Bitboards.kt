package party.elias

typealias Bitboard = Long
typealias BitboardArray = LongArray

object Bitboards {
    const val A1: Bitboard = 1L
    const val H1: Bitboard = 1L shl 7
    const val A8: Bitboard = 1L shl 56
    const val H8: Bitboard = 1L shl 63

    const val RANK_1: Bitboard = 0xFFL
    const val RANK_2: Bitboard = 0xFFL shl 8
    const val RANK_3: Bitboard = 0xFFL shl 16
    const val RANK_4: Bitboard = 0xFFL shl 24
    const val RANK_5: Bitboard = 0xFFL shl 32
    const val RANK_6: Bitboard = 0xFFL shl 40
    const val RANK_7: Bitboard = 0xFFL shl 48
    const val RANK_8: Bitboard = 0xFFL shl 56

    val KNIGHT_ATTACKS: LongArray = LongArray(64) { idx ->
        relativeMoves(Square(idx), arrayOf(
            Pair(1, 2), Pair(2, 1), Pair(-1, 2), Pair(-2, 1),
            Pair(1, -2), Pair(2, -1), Pair(-1, -2), Pair(-2, -1)
        ))
    }

    val KING_ATTACKS: LongArray = LongArray(64) { idx ->
        relativeMoves(Square(idx), arrayOf(
            Pair(1, 1), Pair(1, 0), Pair(1, -1), Pair(0, 1),
            Pair(0, -1), Pair(-1, 1), Pair(-1, 0), Pair(-1, -1)
        ))
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

    val WHITE_PAWN_ATTACKS: LongArray = LongArray(64) { idx -> pawnAttacks(Square(idx), Color.WHITE)}
    val BLACK_PAWN_ATTACKS: LongArray = LongArray(64) { idx -> pawnAttacks(Square(idx), Color.BLACK)}

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

    val WHITE_PAWN_MOVES: LongArray = LongArray(64) { idx -> pawnMoves(Square(idx), Color.WHITE) }
    val BLACK_PAWN_MOVES: LongArray = LongArray(64) { idx -> pawnMoves(Square(idx), Color.BLACK) }

    val INDEXED_PAWN_MOVES: Array<LongArray> = arrayOf(BLACK_PAWN_MOVES, WHITE_PAWN_MOVES)

    private fun pawnMoves(sq: Square, color: Color): Long {
        var bb = 0L

        val movingDirection = if (color == Color.BLACK) -1 else 1

        if (sq.rank != color.opponent().backRank()) {
            bb = bb or Square(sq.rank + movingDirection, sq.file).bb()

            if (sq.rank == color.pawnStartingRank()) {
                bb = bb or Square(sq.rank + 2 * movingDirection, sq.file).bb()
            }
        }

        return bb
    }

    val ROOK_RELATIVE_MOVEMENTS: Array<Pair<Int, Int>> = arrayOf(Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1))
    val BISHOP_RELATIVE_MOVEMENTS: Array<Pair<Int, Int>> = arrayOf(Pair(1, 1), Pair(-1, 1), Pair(1, -1), Pair(-1, -1))

    fun slidingMoves(sq: Square, blockers: Long, relativeMovements: Array<Pair<Int, Int>>): Long {
        var bb = 0L

        for ((ro, fo) in relativeMovements) {
            for (i in 1..7) {
                val rank = sq.rank + ro * i
                val file = sq.file + fo * i

                if (rank in 0..7 && file in 0..7) {
                    val dst = Square(rank, file)
                    bb = bb or dst.bb()
                    if (dst.bb() and blockers != 0L) {
                        break
                    }
                } else {
                    break
                }
            }
        }

        return bb
    }

    fun forAllSquares(bb: Bitboard, f: (square: Square) -> Unit) {
        var bb = bb
        while (bb != 0L) {
            val shift = bb.countTrailingZeroBits()
            f(Square(shift))
            bb = bb xor (1L shl shift)
        }
    }
}
