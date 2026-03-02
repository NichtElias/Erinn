package party.elias

typealias Bitboard = Long
typealias BitboardArray = LongArray

object Bitboards {
    const val A1: Bitboard = 1L
    const val B1: Bitboard = 1L shl 1
    const val C1: Bitboard = 1L shl 2
    const val D1: Bitboard = 1L shl 3
    const val E1: Bitboard = 1L shl 4
    const val F1: Bitboard = 1L shl 5
    const val G1: Bitboard = 1L shl 6
    const val H1: Bitboard = 1L shl 7
    const val A8: Bitboard = 1L shl 56
    const val B8: Bitboard = 1L shl 57
    const val C8: Bitboard = 1L shl 58
    const val D8: Bitboard = 1L shl 59
    const val E8: Bitboard = 1L shl 60
    const val F8: Bitboard = 1L shl 61
    const val G8: Bitboard = 1L shl 62
    const val H8: Bitboard = 1L shl 63

    const val CASTLING_ALL: Bitboard = A1 or H1 or A8 or H8

    const val CASTLING_UNATTACKED_WQ: Bitboard = E1 or D1 or C1
    const val CASTLING_UNATTACKED_WK: Bitboard = E1 or F1 or G1
    const val CASTLING_UNATTACKED_BQ: Bitboard = E8 or D8 or C8
    const val CASTLING_UNATTACKED_BK: Bitboard = E8 or F8 or G8

    val CASTLING_UNATTACKED: BitboardArray = longArrayOf(
        CASTLING_UNATTACKED_WQ,
        CASTLING_UNATTACKED_WK,
        CASTLING_UNATTACKED_BQ,
        CASTLING_UNATTACKED_BK
    )

    const val CASTLING_EMPTY_WQ: Bitboard = B1 or C1 or D1
    const val CASTLING_EMPTY_WK: Bitboard = F1 or G1
    const val CASTLING_EMPTY_BQ: Bitboard = B8 or C8 or D8
    const val CASTLING_EMPTY_BK: Bitboard = F8 or G8

    val CASTLING_EMPTY: BitboardArray = longArrayOf(
        CASTLING_EMPTY_WQ,
        CASTLING_EMPTY_WK,
        CASTLING_EMPTY_BQ,
        CASTLING_EMPTY_BK
    )

    const val RANK_1: Bitboard = 0xFFL
    const val RANK_2: Bitboard = RANK_1 shl 8
    const val RANK_3: Bitboard = RANK_1 shl 16
    const val RANK_4: Bitboard = RANK_1 shl 24
    const val RANK_5: Bitboard = RANK_1 shl 32
    const val RANK_6: Bitboard = RANK_1 shl 40
    const val RANK_7: Bitboard = RANK_1 shl 48
    const val RANK_8: Bitboard = RANK_1 shl 56

    const val FILE_A: Bitboard = 0x0101010101010101L
    const val FILE_B: Bitboard = FILE_A shl 1
    const val FILE_C: Bitboard = FILE_A shl 2
    const val FILE_D: Bitboard = FILE_A shl 3
    const val FILE_E: Bitboard = FILE_A shl 4
    const val FILE_F: Bitboard = FILE_A shl 5
    const val FILE_G: Bitboard = FILE_A shl 6
    const val FILE_H: Bitboard = FILE_A shl 7

    val LEFT_RIGHT: BitboardArray = longArrayOf(
        FILE_A or FILE_B or FILE_C or FILE_D,
        FILE_E or FILE_F or FILE_G or FILE_H
    )

    val QUADRANTS: BitboardArray = longArrayOf(
        LEFT_RIGHT[0] and (RANK_1 or RANK_2 or RANK_3 or RANK_4),
        LEFT_RIGHT[1] and (RANK_1 or RANK_2 or RANK_3 or RANK_4),
        LEFT_RIGHT[0] and (RANK_5 or RANK_6 or RANK_7 or RANK_8),
        LEFT_RIGHT[1] and (RANK_5 or RANK_6 or RANK_7 or RANK_8),
    )

    inline fun forAllSquares(bb: Bitboard, f: (square: Square) -> Unit) {
        var bb = bb
        while (bb != 0L) {
            val shift = bb.countTrailingZeroBits()
            f(Square(shift))
            bb = bb xor (1L shl shift)
        }
    }

    // tests the predicate f for every square on the bitboard, returns true if one of the tests returns true, else false
    inline fun checkSquares(bb: Bitboard, f: (square: Square) -> Boolean): Boolean {
        var bb = bb
        while (bb != 0L) {
            val shift = bb.countTrailingZeroBits()
            if (f(Square(shift))) return true
            bb = bb xor (1L shl shift)
        }
        return false
    }

    fun permutations(bb: Bitboard): BitboardArray {
        require(bb.countOneBits() <= 26) { "A gigabyte of permutations is a bit much." }

        val perm = BitboardArray(1 shl bb.countOneBits())
        perm[0] = 0
        var permCount = 1

        forAllSquares(bb) { square ->
            val preLoopPermCount = permCount
            for (i in 0..<preLoopPermCount) {
                perm[permCount] = perm[permCount - preLoopPermCount] or square.bb()
                permCount++
            }
        }

        return perm
    }
}
