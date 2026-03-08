package party.elias

data class Move(
    val src: Square,
    val dst: Square,
    val capture: Piece,
    val promotion: PieceType = PieceType.NONE,
    val isEp: Boolean = false,
    val castle: Int = -1
) {
    fun toUci(): String {
        val srcUci = src.toUci()
        val dstUci = dst.toUci()

        val promUci = if (promotion == PieceType.NONE) "" else Piece.SYMBOL_MAP[Piece(Color.BLACK, promotion)].toString()

        return "$srcUci$dstUci$promUci"
    }

    inline fun forPromotionVariants(f: (Move) -> Unit) {
        for (pp in PieceType.PROMOTABLE_TO) {
            f(copy(promotion = pp))
        }
    }

    companion object {
        val NULL_MOVE = Move(Square(-1), Square(-1), Piece.NONE)

        fun fromUci(uciMove: String, position: Board): Move {
            val src = Square.parseUci(uciMove.substring(0..1))
            val dst = Square.parseUci(uciMove.substring(2..3))
            val promotion: PieceType = if (uciMove.length > 4) Piece.fromSymbol(uciMove[4]).type() else PieceType.NONE

            val movingPiece = position.pieces[src.value]

            val isEp = dst == position.epSquare && movingPiece.type() == PieceType.PAWN
            val capture = position.pieces[(if (isEp) dst.enPassantActualCapture() else dst).value]

            var castle = -1

            for (i in 0..3) {
                if (src == Square.KING_STARTS[i / 2]
                    && position.pieces[src.value].type() == PieceType.KING
                    && dst == Square.CASTLING_TARGET_SQUARES[i]
                ) {
                    castle = i
                }
            }

            return Move(src, dst, capture, promotion, isEp, castle)
        }
    }
}
