package party.elias

import java.util.TreeSet
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

class Board {
    val piecesBB: BitboardArray = BitboardArray(6)
    val colorsBB: BitboardArray = BitboardArray(2)
    val kingSquares: Array<Square> = Array(2) { _ -> Square(-1) }
    var castlingRights: Bitboard = 0
    var epSquare: Square = Square(-1) // the square that can be attacked after a double pawn push
    var turn: Color = Color.WHITE
    var halfMoves: Int = 0
    var fullMoves: Int = 1
    val pieces: PieceArray = PieceArray()
    var zobristHash: Long = 0
    val positionHistory: LongArray = LongArray(Engine.MAX_GAME_PLY) // stores hashes of all positions in the game history up to this one
    var posHistoryStart: Int = 0 // when loading from fen string, we can't know the previous positions, so anything before this index is invalid

    val occupiedBB: Bitboard get() = colorsBB[0] or colorsBB[1]
    val ply: Int get() = (fullMoves - 1) * 2 + if (turn == Color.BLACK) 1 else 0 // for indexing positionHistory and the like

    fun place(piece: Piece, square: Square) {
        val pieceIdx = piece.type().idx()
        val colorIdx = piece.color().idx()
        piecesBB[pieceIdx] = piecesBB[pieceIdx] or square.bb()
        colorsBB[colorIdx] = colorsBB[colorIdx] or square.bb()

        if (pieceIdx == PieceType.KING.value)
            kingSquares[colorIdx] = square

        pieces[square.value] = piece

        zobristHash = zobristHash xor TranspositionTable.pieceHash(piece, square)
    }

    // remove piece on square, either because it moved elsewhere or it was captured
    fun remove(square: Square) {
        val piece = pieces[square.value]
        val pieceIdx = piece.type().idx()
        val colorIdx = piece.color().idx()
        piecesBB[pieceIdx] = piecesBB[pieceIdx] and square.bb().inv()
        colorsBB[colorIdx] = colorsBB[colorIdx] and square.bb().inv()

        pieces[square.value] = Piece.NONE

        zobristHash = zobristHash xor TranspositionTable.pieceHash(piece, square)
    }

    fun doMove(move: Move): StateInfo {
        val stateInfo = StateInfo(castlingRights, epSquare, halfMoves, zobristHash) // save some info for undoing the move

        val movingPiece = pieces[move.src.value]
        val movingColor = movingPiece.color()
        val movingType = movingPiece.type()

        // remove current castling rights from hash
        zobristHash = zobristHash xor TranspositionTable.castlingHash(castlingRights)

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

        // remove old ep file from hash
        if (epSquare != Square(-1)) {
            zobristHash = zobristHash xor TranspositionTable.HASH_EP_FILE[epSquare.file]
        }

        // set ep square
        if (movingType == PieceType.PAWN
            && (move.src.bb() and (Bitboards.RANK_2 or Bitboards.RANK_7)) != 0L // from 2nd or 7th rank
            && (move.dst.bb() and (Bitboards.RANK_4 or Bitboards.RANK_5)) != 0L
        ) {
            epSquare = move.src.enPassantActualCapture() // enPassantActualCapture not used for intended purpose here

            // update hash with new ep file
            zobristHash = zobristHash xor TranspositionTable.HASH_EP_FILE[epSquare.file]
        } else {
            epSquare = Square(-1)
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

        // add new castling rights to hash
        zobristHash = zobristHash xor TranspositionTable.castlingHash(castlingRights)

        // update hash with turn change
        zobristHash = zobristHash xor TranspositionTable.HASH_BLACK_TURN

        // bookkeeping
        fullMoves += if (turn == Color.BLACK) 1 else 0
        turn = turn.opponent()
        positionHistory[ply] = zobristHash

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
        zobristHash = stateInfo.zobristHash
    }

    fun isDrawByRepetition(): Boolean {
        val minRelRepIdx = min(halfMoves, ply - posHistoryStart)
        if (minRelRepIdx >= 4) {
            var occurrences = 1
            for (i in 2..minRelRepIdx step 2) {
                if (positionHistory[ply - i] == zobristHash) {
                    occurrences++
                    if (occurrences >= 3)
                        return true
                }
            }
        }
        return false
    }

    fun see(captureMove: Move): Score {

        val SEE_MATERIAL_VALUES = intArrayOf(100, 300, 300, 500, 900, 20000)

        val firstVictim = captureMove.capture
        var attacker = pieces[captureMove.src.value]

        val gain = IntArray(32)
        gain[0] = SEE_MATERIAL_VALUES[firstVictim.type().idx()]

        var d = 0
        var side = turn.opponent()

        val attackerSquares = TreeSet<Square>(Comparator { squareA, squareB ->
            return@Comparator SEE_MATERIAL_VALUES[pieces[squareA.value].type().idx()] - SEE_MATERIAL_VALUES[pieces[squareB.value].type().idx()]
        })

        var attackersBB = ((attackersTargeting(captureMove.dst, Color.WHITE)
                or attackersTargeting(captureMove.dst, Color.BLACK))
                and captureMove.src.bb().inv())
        Bitboards.forAllSquares(attackersBB) { attackerSquare ->
            attackerSquares.add(attackerSquare)
        }

        var tempOcc = occupiedBB and captureMove.src.bb().inv()

        while (true) {
            d++

            // add new attackers from x-ray attacks
            var newAttackerBB = 0L
            if (attacker.type() == PieceType.PAWN || attacker.type() == PieceType.BISHOP || attacker.type() == PieceType.QUEEN) {
                newAttackerBB = newAttackerBB or (Magic.getBishopAttacks(captureMove.dst.value, tempOcc)
                        and (piecesBB[PieceType.BISHOP.idx()] or piecesBB[PieceType.QUEEN.idx()])
                        and tempOcc and attackersBB.inv()
                )
            }
            if (attacker.type() == PieceType.ROOK || attacker.type() == PieceType.QUEEN) {
                newAttackerBB = newAttackerBB or (Magic.getRookAttacks(captureMove.dst.value, tempOcc)
                        and (piecesBB[PieceType.ROOK.idx()] or piecesBB[PieceType.QUEEN.idx()])
                        and tempOcc and attackersBB.inv()
                )
            }

            if (newAttackerBB != 0L) {
                assert(newAttackerBB.countOneBits() == 1) // can only be one new attacker per capture

                attackersBB = attackersBB or newAttackerBB
                attackerSquares.add(Square(newAttackerBB.countTrailingZeroBits()))
            }

            // get next attacker
            val nextAttackerSquare = attackerSquares.find { attackerSquare -> (attackerSquare.bb() and colorsBB[side.idx()]) != 0L}
            if (nextAttackerSquare == null) {
                d--
                break
            }

            gain[d] = SEE_MATERIAL_VALUES[attacker.type().idx()] - gain[d-1]

            attacker = pieces[nextAttackerSquare.value]
            attackerSquares.remove(nextAttackerSquare)
            tempOcc = tempOcc and nextAttackerSquare.bb().inv()
            attackersBB = attackersBB and tempOcc

            side = side.opponent()
        }

        while (d > 0) {
            gain[d-1] = -max(-gain[d-1], gain[d])
            d--
        }

        return gain[0]
    }

    fun areSquaresAttackedBy(squares: Bitboard, color: Color): Boolean {
        return Bitboards.checkSquares(squares) { square ->
            isSquareAttackedByNonKing(square, color)
        } || MoveGen.KING_ATTACKS[kingSquares[color.idx()].value] and squares != 0L
    }

    fun attackedSquaresOnBB(bb: Bitboard, attackingColor: Color): Bitboard {
        var attackedSquares: Bitboard = MoveGen.KING_ATTACKS[kingSquares[attackingColor.idx()].value]

        Bitboards.forAllSquares(bb) { square ->
            if (isSquareAttackedByNonKing(square, attackingColor)) {
                attackedSquares = attackedSquares or square.bb()
            }
        }

        return attackedSquares and bb
    }

    private fun isSquareAttackedByNonKing(square: Square, attackingColor: Color): Boolean {

        // checking if a piece on the square could attack an opponents piece of the same type

        if (MoveGen.INDEXED_PAWN_ATTACKS[attackingColor.opponent().idx()][square.value]
            and piecesBB[PieceType.PAWN.idx()] and colorsBB[attackingColor.idx()] != 0L)
            return true
        if (Magic.getBishopAttacks(square.value, occupiedBB)
            and (piecesBB[PieceType.BISHOP.idx()] or piecesBB[PieceType.QUEEN.idx()])
            and colorsBB[attackingColor.idx()] != 0L)
            return true
        if (Magic.getRookAttacks(square.value, occupiedBB)
            and (piecesBB[PieceType.ROOK.idx()] or piecesBB[PieceType.QUEEN.idx()])
            and colorsBB[attackingColor.idx()] != 0L)
            return true
        if (MoveGen.KNIGHT_ATTACKS[square.value]
            and piecesBB[PieceType.KNIGHT.idx()] and colorsBB[attackingColor.idx()] != 0L)
            return true

        return false
    }

    fun attackersTargeting(square: Square, attackingColor: Color): Bitboard {
        val attackingPieces = colorsBB[attackingColor.idx()]

        return ((MoveGen.INDEXED_PAWN_ATTACKS[attackingColor.opponent().idx()][square.value]
                and piecesBB[PieceType.PAWN.idx()] and attackingPieces)
                or (Magic.getBishopAttacks(square.value, occupiedBB)
                and (piecesBB[PieceType.BISHOP.idx()] or piecesBB[PieceType.QUEEN.idx()]) and attackingPieces)
                or (Magic.getRookAttacks(square.value, occupiedBB)
                and (piecesBB[PieceType.ROOK.idx()] or piecesBB[PieceType.QUEEN.idx()]) and attackingPieces)
                or (MoveGen.KNIGHT_ATTACKS[square.value]
                and piecesBB[PieceType.KNIGHT.idx()] and attackingPieces)
                or (MoveGen.KING_ATTACKS[square.value]
                and piecesBB[PieceType.KING.idx()] and attackingPieces))
    }

    fun attacksOf(square: Square, pieceType: PieceType, color: Color): Bitboard {
        return when (pieceType) {
            PieceType.PAWN -> MoveGen.INDEXED_PAWN_ATTACKS[color.idx()][square.value]
            PieceType.BISHOP -> Magic.getBishopAttacks(square.value, occupiedBB)
            PieceType.KNIGHT -> MoveGen.KNIGHT_ATTACKS[square.value]
            PieceType.ROOK -> Magic.getRookAttacks(square.value, occupiedBB)
            PieceType.QUEEN -> (Magic.getBishopAttacks(square.value, occupiedBB)
                    or Magic.getRookAttacks(square.value, occupiedBB))
            PieceType.KING -> MoveGen.KING_ATTACKS[square.value]
            else -> 0L // invalid piece
        }
    }

    fun isColorInCheck(color: Color): Boolean {
        return areSquaresAttackedBy(kingSquares[color.idx()].bb(), color.opponent())
    }

    fun isInCheckAfter(move: Move): Boolean {
        val currentColor = turn

        val stateInfo = doMove(move)
        val inCheck = isColorInCheck(currentColor)
        undoMove(move, stateInfo)

        return inCheck
    }

    fun isPseudoLegalMove(move: Move): Boolean {
        if (move == Move.NULL_MOVE) return false

        val piece = pieces[move.src.value]
        if (piece == Piece.NONE) return false // can't move null piece
        if (piece.color() != turn) return false // can't move opponent's piece

        if (move.isEp) {
            if (move.dst == epSquare && epSquare.bb() and MoveGen.INDEXED_PAWN_ATTACKS[turn.idx()][move.src.value] != 0L) {
                return true // this is a legal ep move
            }
        }

        if (move.castle != -1) {
            return (castlingRights and Square.CASTLING_ROOK_SQUARES[move.castle].bb() != 0L
                    && Bitboards.CASTLING_EMPTY[move.castle] and occupiedBB == 0L
                    && !areSquaresAttackedBy(Bitboards.CASTLING_UNATTACKED[move.castle], turn.opponent())
                    && move.src == Square.KING_STARTS[move.castle / 2]
                    && move.dst == Square.CASTLING_TARGET_SQUARES[move.castle]
                    && move.capture == Piece.NONE)
        }

        if (pieces[move.dst.value] != move.capture) return false // check if the right piece is captured

        if (piece.type() == PieceType.PAWN && move.capture == Piece.NONE) {
            val front = Square(move.src.value + MoveGen.PAWN_DIRECTIONS[turn.idx()])
            if (move.dst == front) return true // this is just a normal pawn push

            if (move.src.rank != turn.pawnStartingRank()) return false // can't do double push if not on starting rank
            if (pieces[front.value] != Piece.NONE) return false // can't do double push if there's something in the way
            val doublePushSquare = Square(move.src.value + 2 * MoveGen.PAWN_DIRECTIONS[turn.idx()])
            return move.dst == doublePushSquare // last possible pawn non-capture is double push, only need to check correct dst square now
        } else {
            val validTargetSquares = (attacksOf(move.src, piece.type(), piece.color())
                    and colorsBB[turn.idx()].inv())

            return validTargetSquares and move.dst.bb() != 0L
        }
    }

    /**
     * Checks pseudolegality and if it puts us into check. Use only for testing if hash move is legal and similar cases.
     * Don't use in move generation.
     */
    fun isLegalMove(move: Move): Boolean {
        return isPseudoLegalMove(move) && !isInCheckAfter(move)
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

            for (pieceValue in pieces.array.sliceArray(ri*8..ri*8+7)) {
                val piece = Piece(pieceValue)
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
            if (board.turn == Color.BLACK)
                board.zobristHash = board.zobristHash xor TranspositionTable.HASH_BLACK_TURN

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
            board.zobristHash = board.zobristHash xor TranspositionTable.castlingHash(board.castlingRights)

            // 4th Part: en passant square
            if (parts[3] != "-") {
                board.epSquare = Square.parseUci(parts[3])
                board.zobristHash = board.zobristHash xor TranspositionTable.HASH_EP_FILE[board.epSquare.file]
            }

            // 5th Part: half move clock
            board.halfMoves = parts[4].toInt()

            // 6th Part: full move counter
            board.fullMoves = parts[5].toInt()

            // put starting position in position history (can't include )
            board.positionHistory[board.ply] = board.zobristHash
            board.posHistoryStart = board.ply

            return board
        }

        fun startPos(): Board {
            return fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        }
    }

    class StateInfo(val castlingRights: Long, val epSquare: Square, val halfMoves: Int, val zobristHash: Long)
}