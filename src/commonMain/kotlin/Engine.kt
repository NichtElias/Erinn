package party.elias

typealias Score = Int

class Engine {

    var position: Board = Board.startPos()

    fun evaluate(): Score {
        var whiteMaterial = 0
        whiteMaterial += (position.piecesBB[PieceType.PAWN.value] and position.colorsBB[Color.WHITE.idx()]).countOneBits() * 100
        whiteMaterial += (position.piecesBB[PieceType.BISHOP.value] and position.colorsBB[Color.WHITE.idx()]).countOneBits() * 300
        whiteMaterial += (position.piecesBB[PieceType.KNIGHT.value] and position.colorsBB[Color.WHITE.idx()]).countOneBits() * 300
        whiteMaterial += (position.piecesBB[PieceType.ROOK.value] and position.colorsBB[Color.WHITE.idx()]).countOneBits() * 500
        whiteMaterial += (position.piecesBB[PieceType.QUEEN.value] and position.colorsBB[Color.WHITE.idx()]).countOneBits() * 1000
        whiteMaterial += (position.piecesBB[PieceType.KING.value] and position.colorsBB[Color.WHITE.idx()]).countOneBits() * 400

        var blackMaterial = 0
        blackMaterial += (position.piecesBB[PieceType.PAWN.value] and position.colorsBB[Color.BLACK.idx()]).countOneBits() * 100
        blackMaterial += (position.piecesBB[PieceType.BISHOP.value] and position.colorsBB[Color.BLACK.idx()]).countOneBits() * 300
        blackMaterial += (position.piecesBB[PieceType.KNIGHT.value] and position.colorsBB[Color.BLACK.idx()]).countOneBits() * 300
        blackMaterial += (position.piecesBB[PieceType.ROOK.value] and position.colorsBB[Color.BLACK.idx()]).countOneBits() * 500
        blackMaterial += (position.piecesBB[PieceType.QUEEN.value] and position.colorsBB[Color.BLACK.idx()]).countOneBits() * 1000
        blackMaterial += (position.piecesBB[PieceType.KING.value] and position.colorsBB[Color.BLACK.idx()]).countOneBits() * 400

        return (whiteMaterial - blackMaterial) * position.turn.scoreFactor()
    }

    fun qSearch(alpha: Score, beta: Score): Score {
        var alpha = alpha
        var bestScore = evaluate()

        if (bestScore >= beta) return bestScore
        if (bestScore > alpha) alpha = bestScore

        val captures = position.genMoves(capturesOnly = true)

        for (move in captures.sortedWith { moveA, moveB ->
            val vDiff = moveB.capture.type().idx() - moveA.capture.type().idx() // works because they're ordered by importance

            if (vDiff != 0) return@sortedWith vDiff

            return@sortedWith position.pieces[moveA.src.value].type().idx() - position.pieces[moveB.src.value].type().idx()
        }) {
            val stateInfo = position.doMove(move)
            val score = -qSearch(-beta, -alpha)
            position.undoMove(move, stateInfo)

            if (score >= beta)
                return score
            if (score > alpha)
                alpha = score
            if (score > bestScore)
                bestScore = score
        }

        return bestScore
    }

    fun search(remainingDepth: Int, alpha: Score = -MATE_SCORE, beta: Score = MATE_SCORE): Pair<Move, Score> {
        if (remainingDepth == 0) return Pair(Move.NULL_MOVE, qSearch(alpha, beta))

        var alpha = alpha

        val moves = position.genMoves()
        var bestScore: Score = -MATE_SCORE
        var bestMove: Move = Move.NULL_MOVE

        if (moves.isEmpty()) {
            if (position.isColorInCheck(position.turn))
                return Pair(Move.NULL_MOVE, -MATE_SCORE) // we got checkmated

            return Pair(Move.NULL_MOVE, 0) // stalemate
        }

        for (move in moves) {
            val stateInfo = position.doMove(move)
            val (_, negScore) = search(remainingDepth - 1, -beta, -alpha)
            val score = -negScore
            position.undoMove(move, stateInfo)

            if (score > bestScore) {
                bestScore = score
                bestMove = move
                if (score > alpha) {
                    alpha = score
                }
            }
            if (score >= beta) {
                return Pair(bestMove, bestScore)
            }
        }

        return Pair(bestMove, bestScore)
    }

    fun perft(depth: Int): Long {
        val moves = position.genMoves()

        if (depth == 1) return moves.size.toLong()

        var nodes = 0L
        for (move in moves) {
            val stateInfo = position.doMove(move)
            nodes += perft(depth - 1)
            position.undoMove(move, stateInfo)
        }
        return nodes
    }

    fun perftDivide(depth: Int): Map<Move, Long> {
        val moves = position.genMoves()

        val results = HashMap<Move, Long>()

        for (move in moves) {
            if (depth == 1) {
                results[move] = 1
            } else {
                val stateInfo = position.doMove(move)
                results[move] = perft(depth - 1)
                position.undoMove(move, stateInfo)
            }
        }

        return results
    }

    companion object {
        const val MATE_SCORE: Score = 32000
    }
}