package party.elias

import kotlin.random.Random

class TranspositionTable {

    val entries: Array<Entry?> = Array(TT_SIZE) { null }

    fun store(key: Long, draft: Int, perspective: Color, plyFromRoot: Int, score: Score, boundType: BoundType, bestMove: Move? = null) {
        val entry = Entry(key, draft, adjustScore(score, perspective, plyFromRoot), boundType, bestMove)

        entries[(key and TT_INDEX_MASK).toInt()] = entry
    }

    fun get(key: Long): Entry? {
        val entry = entries[(key and TT_INDEX_MASK).toInt()]
        if (entry?.key == key)
            return entry
        return null
    }

    fun clear() {
        for (i in 0..<entries.size) {
            entries[i] = null;
        }
    }

    companion object {
        const val TT_SIZE: Int = 0x100000
        const val TT_INDEX_MASK: Long = (TT_SIZE - 1).toLong()

        const val SEED = 84927659

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

        /**
         * Works for both adjusting scores that go into the table and scores that come out of it.
         * Just pass `plyFromRoot` normally when inserting and `-plyFromRoot` when reading.
         * */
        private fun adjustScore(score: Score, perspective: Color, plyFromRoot: Int): Score {
            var score = score

            if (perspective == Color.BLACK)
                score = -score // score in tt is always from white's perspective

            // adjust from "mate in x ply from root" to "mate in x ply from this position"
            if (score >= Engine.MIN_MATE_SCORE) // black is getting mated
                return score + plyFromRoot
            if (score <= -Engine.MIN_MATE_SCORE) // white is getting mated
                return score - plyFromRoot

            return score
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

    enum class BoundType {
        UPPER,
        LOWER,
        EXACT
    }

    data class Entry(val key: Long, val draft: Int, private val score: Score, val bound: BoundType, val bestMove: Move?) {
        fun getAdjustedScore(perspective: Color, plyFromRoot: Int): Score {
            return adjustScore(score, perspective, -plyFromRoot)
        }
    }
}