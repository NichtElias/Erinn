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