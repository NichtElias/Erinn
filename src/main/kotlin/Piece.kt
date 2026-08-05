package party.elias

import kotlin.jvm.JvmInline

@JvmInline
value class Color(val v: Int) { // WHITE = 0b1000, BLACK = 0b0000
    val opponent: Color get() = Color(v.inv() and 8)
    val idx: Int get() = v ushr 3

    fun backRank(): Int = if (this == BLACK) 7 else 0
    fun pawnStartingRank(): Int = if (this == BLACK) 6 else 1

    override fun toString(): String {
        return when (this) {
            BLACK -> "black"
            WHITE -> "white"
            else -> "invalid color"
        }
    }

    companion object {
        val BLACK = Color(0b0000)
        val WHITE = Color(0b1000)
    }
}

@JvmInline
value class PieceType(val v: Int) { // ...0ttt
    val name: String get() = NAMES[v]
    val idx: Int get() = v

    fun isSliding(): Boolean {
        return (v == BISHOP.idx
                || v == ROOK.idx
                || v == QUEEN.idx)
    }

    companion object {
        val PAWN: PieceType   = PieceType(0b0000)
        val BISHOP: PieceType = PieceType(0b0001)
        val KNIGHT: PieceType = PieceType(0b0010)
        val ROOK: PieceType   = PieceType(0b0011)
        val QUEEN: PieceType  = PieceType(0b0100)
        val KING: PieceType   = PieceType(0b0101)

        val NONE: PieceType   = PieceType(0b0111)

        val NAMES: Array<String> = arrayOf("pawn", "bishop", "knight", "rook", "queen", "king")

        val PROMOTABLE_TO: Array<PieceType> = arrayOf(QUEEN, KNIGHT, ROOK, BISHOP)

        val SLIDING_PIECES: IntArray = intArrayOf(BISHOP.idx, ROOK.idx, QUEEN.idx)

        val VALUES: IntArray = intArrayOf(100, 300, 300, 500, 1000, 400)

        val BISHOP_KNIGHT_SWAP_MAP: IntArray = intArrayOf(
            PAWN.idx, KNIGHT.idx, BISHOP.idx, ROOK.idx, QUEEN.idx, KING.idx
        )

        val BY_VALUE_ASC: IntArray = intArrayOf(
            PAWN.idx, KNIGHT.idx, BISHOP.idx, ROOK.idx, QUEEN.idx, KING.idx
        )
    }
}

@JvmInline
value class Piece(val v: Int) { // ...cttt, t = type, c = color
    constructor(color: Color, pieceType: PieceType) : this(color.v or pieceType.v)

    val type: PieceType get() = PieceType(v and 7)
    val color: Color get() = Color(v and 8)

    override fun toString(): String {
        return "${SYMBOL_MAP[this]}"
    }

    companion object {
        val SYMBOL_MAP = mapOf(
            Piece(0) to 'p',
            Piece(1) to 'b',
            Piece(2) to 'n',
            Piece(3) to 'r',
            Piece(4) to 'q',
            Piece(5) to 'k',
            Piece(8) to 'P',
            Piece(9) to 'B',
            Piece(10) to 'N',
            Piece(11) to 'R',
            Piece(12) to 'Q',
            Piece(13) to 'K',
            Piece(15) to '.'
        )
        val REVERSE_SYMBOL_MAP = SYMBOL_MAP.entries.associate { entry -> Pair(entry.value, entry.key) }

        val NONE: Piece = Piece(0b1111)

        fun fromSymbol(symbol: Char): Piece {
            val p = REVERSE_SYMBOL_MAP[symbol]
            requireNotNull(p) { "invalid piece symbol '$symbol'" }

            return p
        }
    }
}

@JvmInline
value class PieceArray(val array: IntArray) {
    constructor() : this(IntArray(64) { Piece.NONE.v })

    operator fun get(index: Int): Piece = Piece(array[index])
    operator fun set(index: Int, piece: Piece) {
        array[index] = piece.v
    }
}
