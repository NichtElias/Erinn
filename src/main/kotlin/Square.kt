package party.elias

@JvmInline
value class Square(val value: Int) {
    constructor(rank: Int, file: Int) : this(rank * 8 + file)
    val rank: Int get() = value ushr 3
    val file: Int get() = value and 7

    fun bb(): Bitboard {
        return 1L shl value
    }

    fun toUci(): String {
        val file = 'a' + file
        val rank = '1' + rank
        return "$file$rank"
    }

    fun enPassantActualCapture(): Square {
        return if (value < 32) Square(value + 8) else Square(value - 8)
    }

    override fun toString(): String {
        return toUci()
    }

    companion object {
        fun parseUci(uciSquare: String): Square {
            assert(uciSquare.length == 2)

            return Square((uciSquare[1] - '1'), (uciSquare[0] - 'a'))
        }

        val A1 = Square(0)
        val C1 = Square(2)
        val D1 = Square(3)
        val E1 = Square(4)
        val F1 = Square(5)
        val G1 = Square(6)
        val H1 = Square(7)
        val A8 = Square(56)
        val C8 = Square(58)
        val D8 = Square(59)
        val E8 = Square(60)
        val F8 = Square(61)
        val G8 = Square(62)
        val H8 = Square(63)

        // ALL castling related arrays should follow this order (QKqk)
        val CASTLING_TARGET_SQUARES: Array<Square> = arrayOf(
            C1, G1, C8, G8
        )

        val CASTLING_ROOK_SQUARES: Array<Square> = arrayOf(
            A1, H1, A8, H8
        )

        val CASTLING_ROOK_TARGET_SQUARES: Array<Square> = arrayOf(
            D1, F1, D8, F8
        )

        val KING_STARTS: Array<Square> = arrayOf(
            E1, E8
        )
    }
}
