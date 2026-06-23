package party.elias

import java.util.TreeSet
import kotlin.math.max
import kotlin.math.min

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
    val kingProtectors: BitboardArray = BitboardArray(2) // pieces preventing the kings from being in check from sliding pieces

    // just a reusable array for SEE, doesn't get affected by doMove/undoMove
    val seeGain: IntArray = IntArray(32)

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
        // save some info for undoing the move
        val stateInfo = StateInfo(
            castlingRights,
            epSquare,
            halfMoves,
            zobristHash,
            kingProtectors.clone()
        )

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
        epSquare = Square(-1)
        if (movingType == PieceType.PAWN
            && (move.src.bb() and (Bitboards.RANK_2 or Bitboards.RANK_7)) != 0L // from 2nd or 7th rank
            && (move.dst.bb() and (Bitboards.RANK_4 or Bitboards.RANK_5)) != 0L
        ) {
            val candidateEpSquare = move.src.enPassantActualCapture() // enPassantActualCapture not used for intended purpose here

            // check if ep is actually possible (barring pins, that would be too hard to check) for better FIDE compliance
            if (Bitboards.EN_PASSANT_CAPTURER_SQUARES[candidateEpSquare.value] and piecesBB[PieceType.PAWN.idx()] and colorsBB[turn.opponent().idx()] != 0L) {
                epSquare = candidateEpSquare

                // update hash with new ep file
                zobristHash = zobristHash xor TranspositionTable.HASH_EP_FILE[epSquare.file]
            }
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

        // calculate info related to being in check (currentKingProtectors)
        calcCheckInfo()

        return stateInfo
    }

    fun undoMove(move: Move, stateInfo: StateInfo) {
        val movedPiece = pieces[move.dst.value]
        val movedColor = movedPiece.color()

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
        kingProtectors[0] = stateInfo.kingProtectors[0]
        kingProtectors[1] = stateInfo.kingProtectors[1]
    }

    fun doNullMove(): StateInfo {
        val stateInfo = StateInfo(
            castlingRights,
            epSquare,
            halfMoves,
            zobristHash,
            kingProtectors
        ) // save some info for undoing the move

        // increment half moves
        halfMoves++

        // remove old ep file from hash
        if (epSquare != Square(-1)) {
            zobristHash = zobristHash xor TranspositionTable.HASH_EP_FILE[epSquare.file]
        }

        // set ep square
        epSquare = Square(-1)

        // update hash with turn change
        zobristHash = zobristHash xor TranspositionTable.HASH_BLACK_TURN

        // bookkeeping
        fullMoves += if (turn == Color.BLACK) 1 else 0
        turn = turn.opponent()
        positionHistory[ply] = zobristHash

        // no need to update the nnue accumulators as they don't change from a null move

        // calculate info related to being in check (currentKingProtectors)
        calcCheckInfo()

        return stateInfo
    }

    fun undoNullMove(stateInfo: StateInfo) {
        // bookkeeping
        turn = turn.opponent()
        fullMoves -= if (turn == Color.BLACK) 1 else 0

        // restore from StateInfo
        castlingRights = stateInfo.castlingRights
        epSquare = stateInfo.epSquare
        halfMoves = stateInfo.halfMoves
        zobristHash = stateInfo.zobristHash
        kingProtectors[0] = stateInfo.kingProtectors[0]
        kingProtectors[1] = stateInfo.kingProtectors[1]

        // no need to restore nnue accumulators, as they don't change from a null move
    }

    fun calcCheckInfo() {
        updateKingProtectors(Color.WHITE)
        updateKingProtectors(Color.BLACK)
    }

    fun updateKingProtectors(color: Color) {
        val ksq = kingSquares[color.idx()]

        kingProtectors[color.idx()] = 0

        var snipers = (((MoveGen.ROOK_ATTACK_MASKS[ksq.value] and (piecesBB[PieceType.ROOK.idx()] or piecesBB[PieceType.QUEEN.idx()]))
                or (MoveGen.BISHOP_ATTACK_MASKS[ksq.value] and (piecesBB[PieceType.BISHOP.idx()] or piecesBB[PieceType.QUEEN.idx()]))
                ) and colorsBB[color.opponent().idx()])

        while (snipers != 0L) {
            val sniperSq = Square(snipers.countTrailingZeroBits())
            snipers = snipers and sniperSq.bb().inv()

            val between = Bitboards.between(ksq, sniperSq) and occupiedBB

            if (between.countOneBits() == 1) {
                kingProtectors[color.idx()] = kingProtectors[color.idx()] or between
            }
        }
    }

    fun nonKpPieceCount(color: Color): Int {
        return ((piecesBB[PieceType.BISHOP.idx()] or piecesBB[PieceType.KNIGHT.idx()]
                or piecesBB[PieceType.ROOK.idx()] or piecesBB[PieceType.QUEEN.idx()]
                ) and colorsBB[color.idx()]).countOneBits()
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

    fun isDrawByInsufficientMaterial(): Boolean {
        if (piecesBB[PieceType.PAWN.idx()] == 0L && piecesBB[PieceType.QUEEN.idx()] == 0L && piecesBB[PieceType.ROOK.idx()] == 0L) {
            if (piecesBB[PieceType.KNIGHT.idx()].countOneBits() + piecesBB[PieceType.BISHOP.idx()].countOneBits() <= 1) {
                return true
            }

            val ourBishops = piecesBB[PieceType.BISHOP.idx()] and colorsBB[turn.idx()]
            val opponentBishops = piecesBB[PieceType.BISHOP.idx()] and colorsBB[turn.opponent().idx()]

            return (piecesBB[PieceType.KNIGHT.idx()] == 0L && ourBishops.countOneBits() == 1 && opponentBishops.countOneBits() == 1
                    && Square(ourBishops.countTrailingZeroBits()).parity == Square(opponentBishops.countTrailingZeroBits()).parity)
        }

        return false
    }

    fun see(captureMove: Move): Score {
        val firstVictim = captureMove.capture
        var attacker = pieces[captureMove.src.value]

        seeGain[0] = SEE_MATERIAL_VALUES[firstVictim.type().idx()]

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
            // if there are...
            if (nextAttackerSquare == null // ...no attackers left or...
                || (nextAttackerSquare.bb() and piecesBB[PieceType.KING.idx()] != 0L // ...the attacker we got is a king (which can only get selected as the last piece for a side)...
                        && attackersBB and nextAttackerSquare.bb().inv() != 0L) // ...and there are still other attackers left (which can only mean attackers of the other player, as the king was our last one)
            ) {
                d--
                break
            }

            seeGain[d] = SEE_MATERIAL_VALUES[attacker.type().idx()] - seeGain[d-1]

            attacker = pieces[nextAttackerSquare.value]
            attackerSquares.remove(nextAttackerSquare)
            tempOcc = tempOcc and nextAttackerSquare.bb().inv()
            attackersBB = attackersBB and tempOcc

            side = side.opponent()
        }

        while (d > 0) {
            seeGain[d-1] = -max(-seeGain[d-1], seeGain[d])
            d--
        }

        return seeGain[0]
    }

    fun areSquaresAttackedBy(squares: Bitboard, color: Color, occupancy: Bitboard = occupiedBB): Boolean {
        return Bitboards.checkSquares(squares) { square ->
            isSquareAttackedByNonKing(square, color, occupancy)
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

    private fun isSquareAttackedByNonKing(square: Square, attackingColor: Color, occupancy: Bitboard = occupiedBB): Boolean {

        // checking if a piece on the square could attack an opponents piece of the same type

        if (MoveGen.INDEXED_PAWN_ATTACKS[attackingColor.opponent().idx()][square.value]
            and piecesBB[PieceType.PAWN.idx()] and colorsBB[attackingColor.idx()] != 0L)
            return true
        if (Magic.getBishopAttacks(square.value, occupancy)
            and (piecesBB[PieceType.BISHOP.idx()] or piecesBB[PieceType.QUEEN.idx()])
            and colorsBB[attackingColor.idx()] != 0L)
            return true
        if (Magic.getRookAttacks(square.value, occupancy)
            and (piecesBB[PieceType.ROOK.idx()] or piecesBB[PieceType.QUEEN.idx()])
            and colorsBB[attackingColor.idx()] != 0L)
            return true
        if (MoveGen.KNIGHT_ATTACKS[square.value]
            and piecesBB[PieceType.KNIGHT.idx()] and colorsBB[attackingColor.idx()] != 0L)
            return true

        return false
    }

    /**
     * returns bitboard of all pieces of attackingColor that currently attack this square (except en passant)
     */
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

    fun moversTargeting(square: Square, movingColor: Color): Bitboard {
        val movingPieces = colorsBB[movingColor.idx()]

        var pawn: Bitboard = 0

        if (square.bb() and Bitboards.PAWN_MOVEABLE_AREAS[movingColor.idx()] != 0L) {
            val singlePushPawnSrcSquare = Square(square.value + -MoveGen.PAWN_DIRECTIONS[movingColor.idx()])
            if (pieces[singlePushPawnSrcSquare.value] == Piece(movingColor, PieceType.PAWN)) {

                pawn = singlePushPawnSrcSquare.bb()

            } else if (pieces[singlePushPawnSrcSquare.value] == Piece.NONE
                && Bitboards.PAWN_DOUBLE_PUSH_TARGET_RANKS[movingColor.idx()] and square.bb() != 0L
            ) {
                val doublePushPawnSrcSquare = Square(square.value + -MoveGen.PAWN_DIRECTIONS[movingColor.idx()] * 2)

                if (pieces[doublePushPawnSrcSquare.value] == Piece(movingColor, PieceType.PAWN))
                    pawn = doublePushPawnSrcSquare.bb()
            }
        }

        return pawn or ((Magic.getBishopAttacks(square.value, occupiedBB)
                and (piecesBB[PieceType.BISHOP.idx()] or piecesBB[PieceType.QUEEN.idx()]) and movingPieces)
                or (Magic.getRookAttacks(square.value, occupiedBB)
                and (piecesBB[PieceType.ROOK.idx()] or piecesBB[PieceType.QUEEN.idx()]) and movingPieces)
                or (MoveGen.KNIGHT_ATTACKS[square.value] and piecesBB[PieceType.KNIGHT.idx()] and movingPieces)
                or (MoveGen.KING_ATTACKS[square.value] and piecesBB[PieceType.KING.idx()] and movingPieces))
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

    fun naiveIsInCheckAfter(move: Move): Boolean {
        val currentColor = turn

        val stateInfo = doMove(move)
        val inCheck = isColorInCheck(currentColor)
        undoMove(move, stateInfo)

        return inCheck
    }

    fun putsCurrentPlayerInCheck(move: Move): Boolean {
        // just take the easy way out if it's en passant
        if (move.isEp) {
            return naiveIsInCheckAfter(move)
        }

        // king just can't walk onto an attacked square
        if (pieces[move.src.value].type() == PieceType.KING) {
            return (isSquareAttackedByNonKing(move.dst, turn.opponent())
                    || MoveGen.KING_ATTACKS[kingSquares[turn.opponent().idx()].value] and move.dst.bb() != 0L)
        }

        // for all other moves we check if the piece is pinned and if it's moving away from the attack ray
        return (kingProtectors[turn.idx()] and move.src.bb() != 0L // pinned
                && Bitboards.line(move.src, move.dst)
                and colorsBB[turn.idx()] and piecesBB[PieceType.KING.idx()] == 0L)
    }

    fun putsOpponentInCheck(move: Move): Boolean {
        // just take the easy way out if it's en passant
        if (move.isEp) {
            val stateInfo = doMove(move)
            val putsInCheck = isColorInCheck(turn)
            undoMove(move, stateInfo)
            return putsInCheck
        }

        val opponentKingBB = colorsBB[turn.opponent().idx()] and piecesBB[PieceType.KING.idx()]

        val movingPiece = pieces[move.src.value]
        if (attacksOf(move.dst, movingPiece.type(), movingPiece.color()) and opponentKingBB != 0L) {
            // we're moving to a square where we're attacking the king, so it's check
            return true
        }

        return (kingProtectors[turn.opponent().idx()] and move.src.bb() != 0L // piece was preventing check
            && Bitboards.line(move.src, move.dst) // and it's not moving on the line...
            and opponentKingBB == 0L) // ...that the opponent's king is on
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
        return isPseudoLegalMove(move) && !naiveIsInCheckAfter(move)
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

    fun toBinaryPosition(evalWdl: Float, resultWdl: Float): ByteArray {
        val headerSize = 7

        val evalWdl16 = java.lang.Float.floatToFloat16(evalWdl)
        val resultWdl16 = java.lang.Float.floatToFloat16(resultWdl)

        val wKingSq = kingSquares[Color.WHITE.idx()]
        val bKingSq = kingSquares[Color.BLACK.idx()]

        var nibbleCount = 0
        val piecesBuffer: IntArray = IntArray(32)

        var emptyCount = 0

        for (sq in 0..63) {
            val piece = pieces[sq]

            if (piece == Piece.NONE || piece.type() == PieceType.KING) {
                emptyCount++

                if (emptyCount == 6) {
                    piecesBuffer[nibbleCount / 2] = piecesBuffer[nibbleCount / 2] or (
                            0b1111 shl if ((nibbleCount and 1) == 0) 4 else 0
                    )
                    nibbleCount++
                    emptyCount = 0
                }

            } else {

                if (emptyCount > 0) {
                    piecesBuffer[nibbleCount / 2] = piecesBuffer[nibbleCount / 2] or (
                            BINARY_POSITION_EMPTY_MARKERS[emptyCount - 1] shl if ((nibbleCount and 1) == 0) 4 else 0
                    )
                    nibbleCount++
                    emptyCount = 0
                }

                piecesBuffer[nibbleCount / 2] = piecesBuffer[nibbleCount / 2] or (
                        (piece.color().value or PieceType.BISHOP_KNIGHT_SWAP_MAP[piece.type().idx()]) shl if ((nibbleCount and 1) == 0) 4 else 0
                )
                nibbleCount++

            }
        }

        if (emptyCount > 0) {
            piecesBuffer[nibbleCount / 2] = piecesBuffer[nibbleCount / 2] or (
                    BINARY_POSITION_EMPTY_MARKERS[emptyCount - 1] shl if ((nibbleCount and 1) == 0) 4 else 0
                    )
            nibbleCount++
            emptyCount = 0
        }

        val binPos = ByteArray(headerSize + (nibbleCount + 1) / 2)

        binPos[0] = ((evalWdl16.toInt() and 0xFFFF) ushr 8).toByte()
        binPos[1] = (evalWdl16.toInt() and 0xFF).toByte()

        binPos[2] = ((resultWdl16.toInt() and 0xFFFF) ushr 8).toByte()
        binPos[3] = (resultWdl16.toInt() and 0xFF).toByte()

        binPos[4] = ((turn.idx() shl 6) or wKingSq.value and 0x3F).toByte()
        binPos[5] = (bKingSq.value and 0x3F).toByte()

        binPos[6] = nibbleCount.toByte()

        for (i in 0..<((nibbleCount + 1) / 2)) {
            binPos[i + headerSize] = piecesBuffer[i].toByte()
        }

        return binPos
    }

    companion object {
        private val BINARY_POSITION_EMPTY_MARKERS = intArrayOf(0b0101, 0b0110, 0b0111, 0b1101, 0b1110, 0b1111)

        val SEE_MATERIAL_VALUES = intArrayOf(100, 300, 300, 500, 900, 20000)

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

            // calculate info related to being in check (currentKingProtectors)
            board.calcCheckInfo()

            return board
        }

        fun startPos(): Board {
            return fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        }
    }

    class StateInfo(
        val castlingRights: Long,
        val epSquare: Square,
        val halfMoves: Int,
        val zobristHash: Long,
        val kingProtectors: BitboardArray
    )
}