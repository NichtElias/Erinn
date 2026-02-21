package party.elias

data class Move(val src: Square, val dst: Square, val promotion: PieceType) {
    fun toUci(): String {
        val srcUci = src.toUci()
        val dstUci = dst.toUci()

        val promUci = if (promotion == PieceType.NONE) "" else Piece.SYMBOL_MAP[Piece(Color.BLACK, promotion)].toString()

        return "$srcUci$dstUci$promUci"
    }

    companion object {
        fun fromUci(uciMove: String): Move {
            assert(uciMove.length in 4..5)

            val src = Square.parseUci(uciMove.substring(0..1))
            val dst = Square.parseUci(uciMove.substring(2..3))

            val promotion: PieceType = if (uciMove.length > 4) Piece.fromSymbol(uciMove[4]).type() else PieceType.NONE

            return Move(src, dst, promotion)
        }
    }
}
