package party.elias

object MoveGen {
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

    val ROOK_RELATIVE_MOVEMENTS: Array<Pair<Int, Int>> = arrayOf(Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1))
    val BISHOP_RELATIVE_MOVEMENTS: Array<Pair<Int, Int>> = arrayOf(Pair(1, 1), Pair(-1, 1), Pair(1, -1), Pair(-1, -1))

    val ROOK_ATTACK_MASKS: LongArray = LongArray(64) { idx -> slidingMoves(Square(idx), 0L, ROOK_RELATIVE_MOVEMENTS) }
    val BISHOP_ATTACK_MASKS: LongArray = LongArray(64) { idx -> slidingMoves(Square(idx), 0L, BISHOP_RELATIVE_MOVEMENTS) }

    val PAWN_DIRECTIONS: IntArray = intArrayOf(-8, 8) // these are square offsets, indexed by color

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