package party.elias

@JvmInline
value class CompactMove(val v: Int) {
    val src: Square get() = Square(v and 0b111111)
    val dst: Square get() = Square((v ushr 6) and 0b111111)
    val capture: Piece get() = Piece((v ushr 12) and 0b1111)
    val promotion: PieceType get() = PieceType((v ushr 16) and 0b111)
    val isEp: Boolean get() = ((v ushr 19) and 0b1) == 1
    val castle: Int get() = if (v and (0b100 shl 20) == 0) ((v ushr 20) and 0b111) else -1

    fun toMove(): Move {
        return Move(src, dst, capture, promotion, isEp, castle)
    }

    fun isNull(): Boolean {
        return src.value == 0 && dst.value == 0
    }

    companion object {
        fun fromMove(move: Move): CompactMove {
            var v = 0

            v = v or (move.src.value and 0b111111)
            v = v or ((move.dst.value and 0b111111) shl 6)
            v = v or ((move.capture.value and 0b1111) shl 12)
            v = v or ((move.promotion.value and 0b111) shl 16)
            v = v or (if (move.isEp) 1 shl 19 else 0)
            v = v or ((move.castle and 0b111) shl 20)

            return CompactMove(v)
        }
    }
}