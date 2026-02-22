import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import party.elias.Board
import party.elias.Move
import party.elias.Piece
import party.elias.PieceType
import party.elias.Square

class MoveTests {
    @Test
    fun uciParsing() {
        val pos = Board.fromFen("rnb2bnr/1ppP1ppk/8/4pP2/8/8/1PPPP1PP/RNBQKBNR w KQkq e6 0 1")

        Assertions.assertEquals(Move(Square(0), Square(63), PieceType.NONE,
            Piece.fromSymbol('r'), false, -1),
            Move.fromUci("a1h8", pos))

        Assertions.assertEquals(Move(Square(51), Square(59), PieceType.QUEEN,
            Piece.NONE, false, -1),
            Move.fromUci("d7d8q", pos))

        Assertions.assertEquals(Move(Square(37), Square(44), PieceType.NONE,
            Piece.fromSymbol('p'), true, -1),
            Move.fromUci("f5e6", pos))

        Assertions.assertThrows(IllegalArgumentException::class.java, { Move.fromUci("d7d8j", pos) })
    }

    @Test
    fun uciRendering() {
        Assertions.assertEquals("a1h8",
            Move(Square(0), Square(63), PieceType.NONE, Piece.NONE, false, -1).toUci())

        Assertions.assertEquals("d7d8q",
            Move(Square(51), Square(59), PieceType.QUEEN, Piece.NONE, false, -1).toUci())

        Assertions.assertEquals("h2h1n",
            Move(Square(15), Square(7), PieceType.KNIGHT, Piece.NONE, false, -1).toUci())
    }
}