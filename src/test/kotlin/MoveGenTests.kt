import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.opentest4j.AssertionFailedError
import party.elias.Board
import party.elias.Engine
import party.elias.Move
import party.elias.uci.uciPositionCmd
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class MoveGenTests {
    val stockfish = Stockfish()
    val engine = Engine()

    @Test
    fun startPos() {
        perftCompare(4, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
    }

    @Test
    fun position2() {
        perftCompare(4, "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
    }

    @Test
    fun position3() {
        perftCompare(5, "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1 ")
    }

    @Test
    fun position4() {
        perftCompare(4, "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1")
    }

    @Test
    fun position5() {
        perftCompare(4, "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8")
    }

    @Test
    fun position6() {
        perftCompare(4, "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10")
    }

    fun perftCompare(depth: Int, fen: String, moves: List<Move> = ArrayList()) {
        stockfish.setPosition(fen, *moves.toTypedArray())
        engine.position = Board.fromFen(fen)
        for (move in moves) {
            engine.position.doMove(move)
        }

        val stockfishResults = stockfish.perft(depth)
        val engineResults = engine.perftDivide(depth)

        if (stockfishResults.values.sum() != engineResults.values.sum()) {
            // check for falsely generated moved
            for (move in engineResults.keys) {
                if (!stockfishResults.containsKey(move.toUci())) {
                    throw AssertionFailedError("generated illegal move '${move.toUci()}' at '${engine.position.toFen()}'")
                }
            }

            // check for missing moves
            for (move in stockfishResults.keys) {
                if (!engineResults.containsKey(Move.fromUci(move, engine.position))) {
                    throw AssertionFailedError("missing move '$move' at '${engine.position.toFen()}'")
                }
            }

            // moves match at this depth, time to check where to search deeper
            for ((move, count) in engineResults) {
                if (count != stockfishResults[move.toUci()]) {
                    val newMoves = ArrayList<Move>()
                    newMoves.addAll(moves)
                    newMoves.add(move)

                    perftCompare(depth - 1, fen, newMoves)
                }
            }
        }
    }
}