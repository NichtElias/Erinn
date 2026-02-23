package party.elias

class Engine {

    var position: Board = Board.startPos()

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
                results[move] = perft(depth - 1)
            }
        }

        return results
    }

}