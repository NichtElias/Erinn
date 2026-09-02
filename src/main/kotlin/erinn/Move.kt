package party.elias.erinn

@JvmInline
value class Move(val v: Int) {
    val src: Square get() = Square(v and 0b111111)
    val dst: Square get() = Square((v ushr 6) and 0b111111)
    val capture: Piece get() = Piece((v ushr 12) and 0b1111)
    val promotion: PieceType get() = PieceType((v ushr 16) and 0b111)
    val isEp: Boolean get() = ((v ushr 19) and 0b111) == 0b100
    val castle: Int get() = if (v and (0b100 shl 19) == 0) ((v ushr 19) and 0b111) else -1

    // bit layout (22 bits):
    // XXXXXXXXXX EEE PPP CCCC DDDDDD SSSSSS
    // S = source square
    // D = destination square
    // C = captured piece, Piece.NONE if no capture
    // P = piece type promoted to, PieceType.NONE if no promotion
    // E = castling / en passant, 0-3 are valid castling indices, highest bit is set when not castling, 4 is en passant
    // X = unused

    constructor(src: Square, dst: Square, capture: Piece, promotion: PieceType = PieceType.NONE, isEp: Boolean = false, castle: Int = -1) : this(
        (src.v and 0b111111)
                or ((dst.v and 0b111111) shl 6)
                or ((capture.v and 0b1111) shl 12)
                or ((promotion.v and 0b111) shl 16)
                or (if (isEp) 0b100 shl 19 else ((castle and 0b111) shl 19))
    )

    inline fun forPromotionVariants(f: (Move) -> Unit) {
        for (pp in PieceType.PROMOTABLE_TO) {
            f(Move((v and (0b111 shl 16).inv()) or ((pp.v and 0b111) shl 16)))
        }
    }

    fun toUci(): String {
        val srcUci = src.toUci()
        val dstUci = dst.toUci()

        val promUci = if (promotion == PieceType.NONE) "" else Piece.SYMBOL_MAP[Piece(Color.BLACK, promotion)].toString()

        return "$srcUci$dstUci$promUci"
    }

    fun isNull(): Boolean {
        return v == 0
    }

    companion object {
        val NULL_MOVE = Move(0)

        fun fromUci(uciMove: String, position: Board): Move {
            val src = Square.parseUci(uciMove.substring(0..1))
            val dst = Square.parseUci(uciMove.substring(2..3))
            val promotion: PieceType = if (uciMove.length > 4) Piece.fromSymbol(uciMove[4]).type else PieceType.NONE

            val movingPiece = position.pieces[src.v]

            val isEp = dst == position.epSquare && movingPiece.type == PieceType.PAWN
            val capture = position.pieces[(if (isEp) dst.enPassantActualCapture() else dst).v]

            var castle = -1

            for (i in 0..3) {
                if (src == Square.KING_STARTS[i / 2]
                    && position.pieces[src.v].type == PieceType.KING
                    && dst == Square.CASTLING_TARGET_SQUARES[i]
                ) {
                    castle = i
                }
            }

            return Move(src, dst, capture, promotion, isEp, castle)
        }
    }
}

@JvmInline
value class MoveArray(val array: IntArray) {
    constructor(size: Int) : this(IntArray(size) { 0 })

    operator fun get(index: Int): Move = Move(array[index])
    operator fun set(index: Int, move: Move) {
        array[index] = move.v
    }

    inline fun forEach(action: (Move) -> Unit) {
        for (i in array.indices) {
            action(this[i])
        }
    }
}