package party.elias

class AccumulatorStack {
    val stack: Array<AccPair> = Array(Engine.MAX_SEARCH_PLY) { AccPair() }

    fun init(board: Board) {
        stack[0].fullRefreshFromBoard(board)
    }

    // returns true if it's a king move and full refresh is required
    fun preDoMove(plyFromRoot: Int, move: Move, board: Board): Boolean {
        val movingPiece = board.pieces[move.src.value]
        val whiteKingSquare = board.kingSquares[Color.WHITE.idx()]
        val blackKingSquare = board.kingSquares[Color.BLACK.idx()]

        val accPair = stack[plyFromRoot + 1]

        accPair.resetDiffs()
        accPair.setDirty()

        accPair.subFeature(movingPiece, move.src, whiteKingSquare, blackKingSquare)

        if (move.promotion != PieceType.NONE) {
            accPair.addFeature(
                Piece(board.turn, move.promotion),
                move.dst,
                whiteKingSquare, blackKingSquare
            )
        } else {
            accPair.addFeature(movingPiece, move.dst, whiteKingSquare, blackKingSquare)
        }

        if (move.capture != Piece.NONE) {
            var capturedSquare = move.dst

            if (move.isEp)
                capturedSquare = capturedSquare.enPassantActualCapture()

            accPair.subFeature(move.capture, capturedSquare, whiteKingSquare, blackKingSquare)
        }

        if (movingPiece.type() == PieceType.KING) {

            if (move.castle != -1) {
                val theRook = Piece(board.turn, PieceType.ROOK)

                accPair.addFeature(
                    theRook,
                    Square.CASTLING_ROOK_TARGET_SQUARES[move.castle],
                    whiteKingSquare, blackKingSquare
                )
                accPair.subFeature(
                    theRook,
                    Square.CASTLING_ROOK_SQUARES[move.castle],
                    whiteKingSquare, blackKingSquare
                )
            }

            // we did a king move, that means the accumulator of the color that played the move needs a full refresh
            return true
        }

        return false
    }

    fun postDoMove(plyFromRoot: Int, board: Board, doFullRefresh: Boolean) {
        val accPair = stack[plyFromRoot + 1]

        // because this is called after doMove, board.turn is already the other player, do we need to do .opponent()
        val colorToRefresh = board.turn.opponent()

        if (doFullRefresh) {
            val frd = Accumulator.FullRefreshData.fromBoard(board, colorToRefresh)
            accPair.byColor[colorToRefresh.idx()].fullRefreshData = frd
        } else {
            accPair.byColor[colorToRefresh.idx()].fullRefreshData = null
        }
    }

    fun preDoNullMove(plyFromRoot: Int) {
        // nothing changes from a null move, so we just leave the diffs empty and mark the accumulators dirty so when
        // they get updated it just copies the parent's contents

        val accPair = stack[plyFromRoot + 1]

        accPair.white.fullRefreshData = null
        accPair.black.fullRefreshData = null

        accPair.resetDiffs()
        accPair.setDirty()
    }

    fun updateAccAt(plyFromRoot: Int) {

        for (color in arrayOf(Color.BLACK, Color.WHITE)) {
            // find the first accumulator up the stack, that is clean or needs to be fully refreshed anyway
            var lastCleanAccumulator = 0
            for (i in plyFromRoot downTo 0) {
                if (!stack[i].byColor[color.idx()].dirty || stack[i].byColor[color.idx()].fullRefreshData != null) {
                    lastCleanAccumulator = i
                    break
                }
            }

            // do a full refresh of the accumulator we found if necessary
            val refreshCandidate = stack[lastCleanAccumulator].byColor[color.idx()]
            if (refreshCandidate.fullRefreshData != null) {
                refreshCandidate.fullRefresh()
            }

            // go down the stack applying the diffs of all the accumulators along the way
            for (i in (lastCleanAccumulator + 1)..plyFromRoot) {
                stack[i].byColor[color.idx()].applyDiff(stack[i - 1].byColor[color.idx()])
            }
        }
    }

    fun get(plyFromRoot: Int, color: Color): Accumulator {
        return stack[plyFromRoot].byColor[color.idx()]
    }

    class AccPair {
        val white: Accumulator.White = Accumulator.White()
        val black: Accumulator.Black = Accumulator.Black()

        val byColor: Array<Accumulator> = arrayOf(black, white)

        fun addFeature(piece: Piece, square: Square, whiteKingSquare: Square, blackKingSquare: Square) {
            white.diff.addFeature(white.feature(piece, square, whiteKingSquare))
            black.diff.addFeature(black.feature(piece, square, blackKingSquare))
        }

        fun subFeature(piece: Piece, square: Square, whiteKingSquare: Square, blackKingSquare: Square) {
            white.diff.subFeature(white.feature(piece, square, whiteKingSquare))
            black.diff.subFeature(black.feature(piece, square, blackKingSquare))
        }

        fun fullRefreshFromBoard(board: Board) {
            white.fullRefreshData = Accumulator.FullRefreshData.fromBoard(board, Color.WHITE)
            black.fullRefreshData = Accumulator.FullRefreshData.fromBoard(board, Color.BLACK)
            white.fullRefresh()
            black.fullRefresh()
        }

        fun setDirty() {
            white.dirty = true
            black.dirty = true
        }

        fun resetDiffs() {
            white.diff.reset()
            black.diff.reset()
        }
    }
}