package party.elias.erinn

@JvmInline
value class CompactMove(val v: Int) {
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

    fun toMove(): Move {
        return Move(src, dst, capture, promotion, isEp, castle)
    }

    companion object {
        fun fromMove(move: Move): CompactMove {
            var v = 0

            v = v or (move.src.v and 0b111111)
            v = v or ((move.dst.v and 0b111111) shl 6)
            v = v or ((move.capture.v and 0b1111) shl 12)
            v = v or ((move.promotion.v and 0b111) shl 16)
            v = v or (if (move.isEp) 0b100 shl 19 else ((move.castle and 0b111) shl 19))

            return CompactMove(v)
        }
    }
}

@JvmInline
value class CompactMoveArray(val array: IntArray) {
    constructor(size: Int) : this(IntArray(size) { 0 })

    operator fun get(index: Int): CompactMove = CompactMove(array[index])
    operator fun set(index: Int, compactMove: CompactMove) {
        array[index] = compactMove.v
    }
}