package party.elias

import kotlin.random.Random

typealias BoundType = Short

class TranspositionTable(capacity: Int) {

    val entries: Array<Entry?> = Array(capacity) { null }

    fun store(key: Long, draft: Int, perspective: Color, plyFromRoot: Int, score: Score, boundType: BoundType, bestMove: Move? = null) {
        val entry = Entry(key, draft.toShort(), adjustScore(score, perspective, plyFromRoot), boundType, (bestMove ?: Move.NULL_MOVE).toCompact())

        entries[(key.toULong() % entries.size.toUInt()).toInt()] = entry
    }

    fun get(key: Long): Entry? {
        val entry = entries[(key.toULong() % entries.size.toUInt()).toInt()]
        if (entry?.key == key)
            return entry
        return null
    }

    fun clear() {
        for (i in entries.indices) {
            entries[i] = null
        }
    }

    fun fullPerMill(): Int {
        // checks 1000 equally spaced slots for an estimate of fullness

        // will always report 0 if entries.size is < 1000, but we can live with that
        val stride = entries.size / 1000

        var occupiedSlots = 0
        for (i in 0..<1000) {
            if (entries[i * stride] != null) occupiedSlots++
        }

        return occupiedSlots
    }

    companion object {
        const val ENTRY_SIZE = (4 // reference to entry in array (reference)
                + 12 // object header
                + 8 // key (long)
                + 2 // draft (short)
                + 4 // score (int)
                + 2 // bound type (short)
                + 4 // best move (int)
                + 0) // padding

        const val BOUND_UPPER: Short = 0b01
        const val BOUND_LOWER: Short = 0b10
        const val BOUND_EXACT: Short = 0b11

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

            for (i in HASH_PIECES.indices) {
                HASH_PIECES[i] = rng.nextLong()
            }

            for (i in HASH_CASTLING.indices) {
                HASH_CASTLING[i] = rng.nextLong()
            }

            for (i in HASH_EP_FILE.indices) {
                HASH_EP_FILE[i] = rng.nextLong()
            }
        }
    }


    data class Entry(val key: Long, val draft: Short, private val score: Score, val bound: BoundType, val bestMove: CompactMove) {
        fun getAdjustedScore(perspective: Color, plyFromRoot: Int): Score {
            return adjustScore(score, perspective, -plyFromRoot)
        }
    }
}