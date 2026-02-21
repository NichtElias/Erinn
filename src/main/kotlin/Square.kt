package party.elias

@JvmInline
value class Square(val value: Int) {
    constructor(rank: Int, file: Int) : this(rank * 8 + file)

    fun bb(): Bitboard {
        return 1L shl value
    }

    fun toUci(): String {
        val file = 'a' + (value and 7)
        val rank = '1' + (value ushr 3)
        return "$file$rank"
    }

    companion object {
        fun parseUci(uciSquare: String): Square {
            assert(uciSquare.length == 2)

            return Square((uciSquare[1] - '1'), (uciSquare[0] - 'a'))
        }
    }
}
