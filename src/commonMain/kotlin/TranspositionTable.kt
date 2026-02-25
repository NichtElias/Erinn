package party.elias

import kotlin.math.min
import kotlin.random.Random

class TranspositionTable {


    companion object {
        val SEED = 84927659

        var HASH_BLACK_TURN: Long = 0
        val HASH_PIECES: LongArray = LongArray(64 * 12)
        val HASH_CASTLING: LongArray = LongArray(4)
        val HASH_EP_FILE: LongArray = LongArray(8)

        fun pieceHash(piece: Piece, square: Square): Long {
            return HASH_PIECES[square.value * 12 + piece.color().idx() * 6 + piece.type().idx()]
        }

        fun castlingHash(castlingRights: Bitboard): Long {
            var hash = 0L
            if (castlingRights and Bitboards.A1 != 0L) hash = hash xor HASH_CASTLING[0]
            if (castlingRights and Bitboards.H1 != 0L) hash = hash xor HASH_CASTLING[1]
            if (castlingRights and Bitboards.A8 != 0L) hash = hash xor HASH_CASTLING[2]
            if (castlingRights and Bitboards.H8 != 0L) hash = hash xor HASH_CASTLING[3]

            return hash
        }

        init {
            val rng = Random(SEED)

            HASH_BLACK_TURN = rng.nextLong()

            for (i in 0..<HASH_PIECES.size) {
                HASH_PIECES[i] = rng.nextLong()
            }

            for (i in 0..<HASH_CASTLING.size) {
                HASH_CASTLING[i] = rng.nextLong()
            }

            for (i in 0..<HASH_EP_FILE.size) {
                HASH_EP_FILE[i] = rng.nextLong()
            }
        }
    }
}