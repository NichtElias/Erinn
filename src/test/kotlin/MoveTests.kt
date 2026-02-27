import party.elias.Board
import party.elias.Move
import party.elias.Piece
import party.elias.PieceType
import party.elias.Square
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoveTests {
    @Test
    fun uciParsing() {
        val pos = Board.fromFen("rnb2bnr/1ppP1ppk/8/4pP2/8/8/1PPPP1PP/RNBQKBNR w KQkq e6 0 1")

        assertEquals(Move(
            Square(0), Square(63), Piece.fromSymbol('r'),
            PieceType.NONE
        ),
            Move.fromUci("a1h8", pos))

        assertEquals(Move(
            Square(51), Square(59), Piece.NONE,
            PieceType.QUEEN
        ),
            Move.fromUci("d7d8q", pos))

        assertEquals(Move(
            Square(37), Square(44), Piece.fromSymbol('p'),
            PieceType.NONE, true
        ),
            Move.fromUci("f5e6", pos))

        assertFailsWith(IllegalArgumentException::class) { Move.fromUci("d7d8j", pos) }
    }

    @Test
    fun uciRendering() {
        assertEquals("a1h8",
            Move(Square(0), Square(63), Piece.NONE, PieceType.NONE).toUci())

        assertEquals("d7d8q",
            Move(Square(51), Square(59), Piece.NONE, PieceType.QUEEN).toUci())

        assertEquals("h2h1n",
            Move(Square(15), Square(7), Piece.NONE, PieceType.KNIGHT).toUci())
    }
}