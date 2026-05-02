import party.elias.Board
import party.elias.Engine
import party.elias.Move
import party.elias.Score
import party.elias.Square
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class BoardTests {

    fun forRandomPositions(rng: Random, games: Int, minMoves: Int, maxMoves: Int, f: (Board) -> Unit) {
        val engine = Engine()

        for (i in 0..games) {
            engine.position = Board.startPos()

            for (m in 0..rng.nextInt(minMoves, maxMoves)) {
                val allMoves = ArrayList<Move>()

                engine.moveGens[m].begin(inCheck = engine.position.isColorInCheck(engine.position.turn))

                while (true) {
                    val move = engine.moveGens[0].nextMove() ?: break
                    allMoves.add(move)
                }

                if (allMoves.isEmpty()) break

                f(engine.position)

                engine.position.doMove(allMoves[rng.nextInt(allMoves.size)])
            }
        }
    }

    @Test
    fun legalityCheckWorks() {
        val rng = Random(3865252)
        val engine = Engine()

        val bunchOfRandomMoves = ArrayList<Move>()

        forRandomPositions(rng, 100, 10, 50) { b ->
            val allMoves = ArrayList<Move>()
            engine.position = b
            engine.moveGens[0].begin(inCheck = b.isColorInCheck(b.turn))

            while (true) {
                val move = engine.moveGens[0].nextMove() ?: break
                allMoves.add(move)
            }

            val move = allMoves[rng.nextInt(allMoves.size)]
            bunchOfRandomMoves.add(move)
        }

        forRandomPositions(rng, 100, 10, 50) { b ->
            engine.position = b
            val sampleIndex = rng.nextInt(0, bunchOfRandomMoves.size - 50)
            for (i in sampleIndex..sampleIndex+50) {
                val sampleMove = bunchOfRandomMoves[i]

                var moveWasGenerated = false
                engine.moveGens[0].begin(inCheck = b.isColorInCheck(b.turn))
                while (true) {
                    val move = engine.moveGens[0].nextMove() ?: break
                    if (move == sampleMove) {
                        moveWasGenerated = true
                        break
                    }
                }
                assertEquals(moveWasGenerated, b.isLegalMove(sampleMove))
            }
        }
    }

    fun seeTestHelper(fen: String, uciMove: String, expectedScore: Score) {
        val pos = Board.fromFen(fen)
        assertEquals(expectedScore, pos.see(Move.fromUci(uciMove, pos)), "got wrong SEE value for fen $fen")
    }

    @Test
    fun seeTest() {
        seeTestHelper("1k1r4/1pp4p/p7/4p3/8/P5P1/1PP4P/2K1R3 w - - 0 1", "e1e5", 100)
        seeTestHelper("rnbqkbnr/ppp1pppp/8/3p4/2B1P3/8/PPPP1PPP/RNBQK1NR w KQkq - 0 1", "e4d5", 100)
        seeTestHelper("2k5/2p5/p7/1p2N3/1b2ppP1/4nP2/PP2B1P1/n1K5 w - - 3 23", "e2b5", -200)
        seeTestHelper("2k5/2p5/p7/1p2N3/1b2ppP1/1P2nP2/P3B1P1/n1K5 b - - 0 23", "a1b3", -200)
        seeTestHelper("r2k1b1r/pp1bpBpp/2n2n2/8/1q1P4/1QN2N2/PP3PPP/R1B1K2R w KQ - 1 11", "b3b4", 0)
        seeTestHelper("1k1r3q/1ppn3p/p4b2/4p3/8/P2N2P1/1PP1R1BP/2K1Q3 w - - 0 1", "d3e5", -200)
    }

    @Test
    fun putsOpponentInCheckTest() {
        val rng = Random(3865252)
        val engine = Engine()

        forRandomPositions(rng, 100, 0, 200) { b ->
            engine.position = b
            engine.moveGens[0].begin(inCheck = b.isColorInCheck(b.turn))

            while (true) {
                val move = engine.moveGens[0].nextMove() ?: break

                val stateInfo = engine.position.doMove(move)
                val putsInCheck = engine.position.isColorInCheck(engine.position.turn)
                engine.position.undoMove(move, stateInfo)

                assertEquals(putsInCheck, engine.position.putsOpponentInCheck(move), "unexpected putsOpponentInCheck result for move ${move.toUci()} at position fen ${b.toFen()}")
            }
        }
    }
}