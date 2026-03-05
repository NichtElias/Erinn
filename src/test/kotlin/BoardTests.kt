import party.elias.Board
import party.elias.Move
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class BoardTests {

    fun forRandomPositions(rng: Random, games: Int, minMoves: Int, maxMoves: Int, f: (Board) -> Unit) {
        for (i in 0..games) {
            val b = Board.startPos()

            for (m in 0..rng.nextInt(minMoves, maxMoves)) {
                val allMoves = ArrayList<Move>()
                b.forMoves { move -> allMoves.add(move) }

                if (allMoves.isEmpty()) break

                b.doMove(allMoves[rng.nextInt(allMoves.size)])

                f(b)
            }
        }
    }

    @Test
    fun legalityCheckWorks() {
        val rng = Random(3865252)

        val bunchOfRandomMoves = ArrayList<Move>()

        forRandomPositions(rng, 100, 10, 50) { b ->
            val allMoves = ArrayList<Move>()
            b.forMoves { move -> allMoves.add(move) }

            val move = allMoves[rng.nextInt(allMoves.size)]
            bunchOfRandomMoves.add(move)
        }

        forRandomPositions(rng, 100, 10, 50) { b ->
            val sampleIndex = rng.nextInt(0, bunchOfRandomMoves.size - 50)
            for (i in sampleIndex..sampleIndex+50) {
                val sampleMove = bunchOfRandomMoves[i]

                var moveWasGenerated = false
                b.forMoves { move ->
                    if (move == sampleMove) {
                        moveWasGenerated = true
                        return@forMoves
                    }
                }
                assertEquals(moveWasGenerated, b.isLegalMove(sampleMove))
            }
        }
    }
}