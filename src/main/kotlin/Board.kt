package party.elias

class Board {
    val piecesBB: BitboardArray = BitboardArray(6)
    val colorsBB: BitboardArray = BitboardArray(2)
    val kingSquares: Array<Square> = Array(2) { _ -> Square(-1) }
    var castlingRights: Bitboard = 0
    var epSquare: Square = Square(-1) // the square that can be attacked after a double pawn push
    var turn: Color = Color.WHITE
    var halfMoves: Int = 0
    var fullMoves: Int = 1
    val pieces: Array<Piece> = Array(64) { _ -> Piece.NONE }

    val occupiedBB: Bitboard get() = colorsBB[0] or colorsBB[1]

    fun place(piece: Piece, square: Square) {
        val pieceIdx = piece.type().idx()
        val colorIdx = piece.color().idx()
        piecesBB[pieceIdx] = piecesBB[pieceIdx] or square.bb()
        colorsBB[colorIdx] = colorsBB[colorIdx] or square.bb()

        if (pieceIdx == PieceType.KING.value)
            kingSquares[colorIdx] = square

        pieces[square.value] = piece
    }

    // remove piece on square, either because it moved elsewhere or it was captured
    fun remove(square: Square) {
        val pieceIdx = pieces[square.value].type().idx()
        val colorIdx = pieces[square.value].color().idx()
        piecesBB[pieceIdx] = piecesBB[pieceIdx] and square.bb().inv()
        colorsBB[colorIdx] = colorsBB[colorIdx] and square.bb().inv()

        pieces[square.value] = Piece.NONE
    }

    fun doMove(move: Move): StateInfo {
        val stateInfo = StateInfo(castlingRights, epSquare, halfMoves) // save some info for undoing the move

        val movingPiece = pieces[move.src.value]
        val movingColor = movingPiece.color()
        val movingType = movingPiece.type()

        assert(movingType != PieceType.NONE)

        // increment half moves
        halfMoves++

        // capture
        if (move.capture != Piece.NONE) {
            var capturedSquare = move.dst

            if (move.isEp) {
                capturedSquare = capturedSquare.enPassantActualCapture()
            }

            // remove captured piece
            remove(capturedSquare)

            halfMoves = 0
        }

        if (movingType == PieceType.PAWN) halfMoves = 0

        // set ep square
        epSquare = if (movingType == PieceType.PAWN
            && (move.src.bb() and (Bitboards.RANK_2 or Bitboards.RANK_7)) != 0L // from 2nd or 7th rank
            && (move.dst.bb() and (Bitboards.RANK_4 or Bitboards.RANK_5)) != 0L
        ) {
            move.src.enPassantActualCapture() // enPassantActualCapture not used for intended purpose here
        } else {
            Square(-1)
        }

        // special cases for the king
        if (movingType == PieceType.KING) {
            // update castling rights when king moves
            castlingRights = castlingRights and (if (movingColor == Color.WHITE) Bitboards.RANK_1.inv() else Bitboards.RANK_8.inv())

            // move rooks when castling
            if (move.castle != -1) {
                place(Piece(movingColor, PieceType.ROOK), Square.CASTLING_ROOK_TARGET_SQUARES[move.castle])
                remove(Square.CASTLING_ROOK_SQUARES[move.castle])
            }
        }

        // update castling rights if anything happens with the rooks
        castlingRights = castlingRights and move.src.bb().inv() and move.dst.bb().inv()

        // take piece from src square
        remove(move.src)

        // put piece on dst square
        if (move.promotion == PieceType.NONE) {
            place(movingPiece, move.dst)
        } else {
            // promote
            place(Piece(movingColor, move.promotion), move.dst)
        }

        // bookkeeping
        fullMoves += if (turn == Color.BLACK) 1 else 0
        turn = turn.opponent()

        return stateInfo
    }

    fun undoMove(move: Move, stateInfo: StateInfo) {
        val movedPiece = pieces[move.dst.value]
        val movedColor = movedPiece.color()
        val movedType = movedPiece.type()

        // bookkeeping
        turn = turn.opponent()
        fullMoves -= if (turn == Color.BLACK) 1 else 0

        // put piece back on src square
        if (move.promotion == PieceType.NONE) {
            place(movedPiece, move.src)
        } else {
            // un-promote
            place(Piece(movedColor, PieceType.PAWN), move.src)
        }

        // remove piece from dst square
        remove(move.dst)

        // revert castling rook movement
        if (move.castle != -1) {
            place(Piece(movedColor, PieceType.ROOK), Square.CASTLING_ROOK_SQUARES[move.castle])
            remove(Square.CASTLING_ROOK_TARGET_SQUARES[move.castle])
        }

        // capture
        if (move.capture != Piece.NONE) {
            var capturedSquare = move.dst

            if (move.isEp) {
                capturedSquare = capturedSquare.enPassantActualCapture()
            }

            // place captured piece back
            place(move.capture, capturedSquare)
        }

        // restore from StateInfo
        castlingRights = stateInfo.castlingRights
        epSquare = stateInfo.epSquare
        halfMoves = stateInfo.halfMoves
    }

    fun genPseudoLegalMoves(): List<Move> {
        val moves = ArrayList<Move>()

        for (i in 0..<pieces.size) {
            val piece = pieces[i]
            val src = Square(i)
            if (piece.color() == turn) {
                when (piece.type()) {
                    PieceType.KNIGHT -> {
                        // knight moves and attacks
                        Bitboards.forAllSquares(
                            MoveGen.KNIGHT_ATTACKS[i] and colorsBB[turn.idx()].inv()
                        ) { square ->
                            moves.add(Move(src, square, pieces[square.value]))
                        }
                    }

                    PieceType.PAWN -> {
                        // normal pawn attacks
                        Bitboards.forAllSquares(
                            MoveGen.INDEXED_PAWN_ATTACKS[turn.idx()][i] and colorsBB[turn.opponent().idx()]
                        ) { square ->
                            if (square.rank == turn.opponent().backRank()) {
                                moves.add(Move(src, square, pieces[square.value], promotion = PieceType.QUEEN))
                                moves.add(Move(src, square, pieces[square.value], promotion = PieceType.KNIGHT))
                                moves.add(Move(src, square, pieces[square.value], promotion = PieceType.ROOK))
                                moves.add(Move(src, square, pieces[square.value], promotion = PieceType.BISHOP))
                            } else {
                                moves.add(Move(src, square, pieces[square.value]))
                            }
                        }
                        // pawn moves
                        val front = Square(i + MoveGen.PAWN_DIRECTIONS[turn.idx()])
                        if (pieces[front.value] == Piece.NONE) {
                            if (front.rank == turn.opponent().backRank()) {
                                moves.add(Move(src, front, Piece.NONE, promotion = PieceType.QUEEN))
                                moves.add(Move(src, front, Piece.NONE, promotion = PieceType.KNIGHT))
                                moves.add(Move(src, front, Piece.NONE, promotion = PieceType.ROOK))
                                moves.add(Move(src, front, Piece.NONE, promotion = PieceType.BISHOP))
                            } else {
                                moves.add(Move(src, front, Piece.NONE))
                            }

                            val doublePushSquare = Square(i + 2 * MoveGen.PAWN_DIRECTIONS[turn.idx()])
                            if (src.rank == turn.pawnStartingRank() && pieces[doublePushSquare.value] == Piece.NONE) {
                                moves.add(Move(src, doublePushSquare, Piece.NONE))
                            }
                        }
                    }

                    PieceType.KING -> {
                        // normal king moves and attacks
                        Bitboards.forAllSquares(
                            MoveGen.KING_ATTACKS[i] and colorsBB[turn.idx()].inv()
                        ) { square ->
                            moves.add(Move(src, square, pieces[square.value]))
                        }
                    }

                    PieceType.BISHOP -> {
                        // bishop moves and attacks
                        Bitboards.forAllSquares(
                            MoveGen.slidingMoves(src, occupiedBB, MoveGen.BISHOP_RELATIVE_MOVEMENTS) and colorsBB[turn.idx()].inv()
                        ) { square ->
                            moves.add(Move(src, square, pieces[square.value]))
                        }
                    }

                    PieceType.ROOK -> {
                        // rook moves and attacks
                        Bitboards.forAllSquares(
                            MoveGen.slidingMoves(src, occupiedBB, MoveGen.ROOK_RELATIVE_MOVEMENTS) and colorsBB[turn.idx()].inv()
                        ) { square ->
                            moves.add(Move(src, square, pieces[square.value]))
                        }
                    }

                    PieceType.QUEEN -> {
                        // queen moves and attacks
                        Bitboards.forAllSquares(
                            (MoveGen.slidingMoves(src, occupiedBB, MoveGen.ROOK_RELATIVE_MOVEMENTS)
                                    or MoveGen.slidingMoves(src, occupiedBB, MoveGen.BISHOP_RELATIVE_MOVEMENTS)
                                    ) and colorsBB[turn.idx()].inv()
                        ) { square ->
                            moves.add(Move(src, square, pieces[square.value]))
                        }
                    }
                }
            }
        }



        return moves
    }

    fun genMoves(): List<Move> {
        val moves = genPseudoLegalMoves()
        val currentColor = turn

        return moves.filter(fun(move: Move): Boolean {
            var legal = true

            val stateInfo = doMove(move)
            for (subMove in genPseudoLegalMoves()) {
                if (subMove.dst == kingSquares[currentColor.idx()]) {
                    legal = false
                    break
                }
            }
            undoMove(move, stateInfo)

            return legal
        })
    }

    override fun toString(): String {
        val b: StringBuilder = StringBuilder()

        b.append("to move: $turn\n")

        for (rank in 7 downTo 0) {
            for (file in 0..7) {
                b.append(Piece.SYMBOL_MAP[pieces[rank * 8 + file]])
                if (file < 7) b.append(" ")
            }
            b.append("\n")
        }

        return b.toString()
    }

    fun toFen(): String {
        val sb = StringBuilder()

        // 1st Part: ranks
        for (ri in 7 downTo 0) {
            var empty = 0

            for (piece in pieces.sliceArray(ri*8..ri*8+7)) {
                if (piece == Piece.NONE) {
                    empty++
                } else {
                    if (empty != 0) sb.append(empty.toString())
                    sb.append(piece.toString())
                    empty = 0
                }
            }
            if (empty != 0) sb.append(empty.toString())

            if (ri != 0) sb.append("/")
        }

        sb.append(" ")
        // 2nd Part: whose turn is it?
        sb.append(if (turn == Color.BLACK) "b" else "w")

        sb.append(" ")
        // 3rd Part: castling rights
        if (castlingRights and Bitboards.H1 != 0L) sb.append("K")
        if (castlingRights and Bitboards.A1 != 0L) sb.append("Q")
        if (castlingRights and Bitboards.H1 != 0L) sb.append("k")
        if (castlingRights and Bitboards.A1 != 0L) sb.append("q")
        if (castlingRights and Bitboards.CASTLING_ALL == 0L) sb.append("-")

        sb.append(" ")
        // 4th Part: en passant square
        if (epSquare != Square(-1))
            sb.append(epSquare.toUci())
        else
            sb.append("-")

        sb.append(" ")
        // 5th Part: half move clock
        sb.append(halfMoves.toString())

        sb.append(" ")
        // 6th Part: full move counter
        sb.append(fullMoves.toString())

        return sb.toString()
    }

    companion object {
        fun fromFen(fen: String): Board {
            val board = Board()

            val parts = fen.split(" ")

            // 1st Part: ranks
            val ranks = parts[0].split("/")

            if (ranks.size != 8) throw IllegalArgumentException("invalid number of ranks in fen string '$fen'")

            for (ri in 0..7) {
                var fi = 0

                for (c in ranks[7 - ri]) {
                    if (c in '1'..'8') {
                        fi += c - '0'
                    } else if (c in 'a'..'z' || c in 'A'..'Z') {
                        board.place(Piece.fromSymbol(c), Square(ri, fi))
                        fi++
                    }
                }
            }

            // 2nd Part: whose turn is it?
            board.turn = when (parts[1]) {
                "w" -> Color.WHITE
                "b" -> Color.BLACK
                else -> throw IllegalArgumentException("invalid turn color '${parts[1]}' in fen string")
            }

            // 3rd Part: castling rights
            for (c in parts[2]) {
                board.castlingRights = board.castlingRights or when (c) {
                    'Q' -> Bitboards.A1
                    'K' -> Bitboards.H1
                    'q' -> Bitboards.A8
                    'k' -> Bitboards.H8
                    '-' -> 0
                    else -> throw IllegalArgumentException("invalid castling rights '${parts[2]}' in fen string")
                }
            }

            // 4th Part: en passant square
            if (parts[3] != "-")
                board.epSquare = Square.parseUci(parts[3])

            // 5th Part: half move clock
            board.halfMoves = parts[4].toInt()

            // 6th Part: full move counter
            board.fullMoves = parts[5].toInt()

            return board
        }

        fun startPos(): Board {
            return fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        }
    }

    class StateInfo(val castlingRights: Long, val epSquare: Square, val halfMoves: Int)
}