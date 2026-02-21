import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import party.elias.Move
import party.elias.PieceType
import party.elias.Square

class MoveTests {
    @Test
    fun uciParsing() {
        Assertions.assertEquals(Move(Square(0), Square(63), PieceType.NONE),
            Move.fromUci("a1h8"))

        Assertions.assertEquals(Move(Square(51), Square(59), PieceType.QUEEN),
            Move.fromUci("d7d8q"))

        Assertions.assertEquals(Move(Square(15), Square(7), PieceType.KNIGHT),
            Move.fromUci("h2h1n"))

        Assertions.assertThrows(IllegalArgumentException::class.java, { Move.fromUci("a7a8j") })
    }

    @Test
    fun uciRendering() {
        Assertions.assertEquals("a1h8",
            Move(Square(0), Square(63), PieceType.NONE).toUci())

        Assertions.assertEquals("d7d8q",
            Move(Square(51), Square(59), PieceType.QUEEN).toUci())

        Assertions.assertEquals("h2h1n",
            Move(Square(15), Square(7), PieceType.KNIGHT).toUci())
    }
}