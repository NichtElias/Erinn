import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import party.elias.Board
import party.elias.Engine

class MoveGenTests {
    @Test
    fun startPos() {

        val e = Engine()
        e.position = Board.startPos()

        Assertions.assertEquals(20, e.perft(1))
        Assertions.assertEquals(400, e.perft(2))
        Assertions.assertEquals(8902, e.perft(3))
        Assertions.assertEquals(197281, e.perft(4))
        Assertions.assertEquals(4865609, e.perft(5))

        e.position = Board.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 0")

        Assertions.assertEquals(48, e.perft(1))
        Assertions.assertEquals(2039, e.perft(2))
        Assertions.assertEquals(97862, e.perft(3))
        Assertions.assertEquals(4085603, e.perft(4))

        e.position = Board.fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")

        Assertions.assertEquals(14, e.perft(1))
        Assertions.assertEquals(191, e.perft(2))
        Assertions.assertEquals(2812, e.perft(3))
        Assertions.assertEquals(43238, e.perft(4))
        Assertions.assertEquals(674624, e.perft(5))

        e.position = Board.fromFen("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1")

        Assertions.assertEquals(6, e.perft(1))
        Assertions.assertEquals(264, e.perft(2))
        Assertions.assertEquals(9467, e.perft(3))
        Assertions.assertEquals(422333, e.perft(4))

        e.position = Board.fromFen("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8")

        Assertions.assertEquals(44, e.perft(1))
        Assertions.assertEquals(1486, e.perft(2))
        Assertions.assertEquals(62379, e.perft(3))
        Assertions.assertEquals(2103487, e.perft(4))

        e.position = Board.fromFen("r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10")

        Assertions.assertEquals(46, e.perft(1))
        Assertions.assertEquals(2079, e.perft(2))
        Assertions.assertEquals(89890, e.perft(3))
        Assertions.assertEquals(3894594, e.perft(4))
    }
}