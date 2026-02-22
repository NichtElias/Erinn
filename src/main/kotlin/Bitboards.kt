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

    val KNIGHT_ATTACKS: LongArray = LongArray(64, fun(idx: Int): Long {
        var bb = 0L
        val sq = Square(idx)

        for ((ro, fo) in arrayOf(Pair(1, 2), Pair(2, 1), Pair(-1, 2), Pair(-2, 1), Pair(1, -2), Pair(2, -1), Pair(-1, -2), Pair(-2, -1))) {
            val rank = sq.rank + ro
            val file = sq.file + fo
            if (rank in 0..7 && file in 0..7) {
                bb = bb or Square(rank, file).bb()
            }
        }

        return bb
    })

    fun forAllSquares(bb: Bitboard, f: (square: Square) -> Unit) {
        var bb = bb
        while (bb != 0L) {
            val shift = bb.countTrailingZeroBits()
            f(Square(shift))
            bb = bb xor (1L shl shift)
        }
    }
}
