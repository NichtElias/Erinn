package party.elias

import kotlin.random.Random

typealias BoundType = Short

class TranspositionTable(val capacity: Int) {

    val entries: LongArray = LongArray(capacity * 2)

    fun store(key: Long, draft: Int, perspective: Color, plyFromRoot: Int, score: Score, boundType: BoundType, bestMove: Move) {
        val index = (key.toULong() % capacity.toUInt()).toInt() * 2

        val newValue = TTValue(
            (if (boundType == BOUND_UPPER && bestMove == Move.NULL_MOVE)
                    (if (entries[index] == key) TTValue(entries[index + 1]).bestMove else Move.NULL_MOVE.toCompact())
            else bestMove.toCompact()),
            adjustScore(score, perspective, plyFromRoot),
            draft,
            boundType
        )

        entries[index] = key
        entries[index + 1] = newValue.v
    }

    fun get(key: Long): TTValue {
        val index = (key.toULong() % capacity.toUInt()).toInt() * 2

        if (entries[index] == key) return TTValue(entries[index + 1])

        return TTValue(0)
    }

    fun clear() {
        entries.fill(0)
    }

    fun fullPerMill(): Int {
        // checks 1000 equally spaced slots for an estimate of fullness

        // will always report 0 if entries.size is < 1000, but we can live with that
        val stride = entries.size / 1000

        var occupiedSlots = 0
        for (i in 0..<1000) {
            if (entries[i * stride] != 0L) occupiedSlots++
        }

        return occupiedSlots
    }

    companion object {
        const val ENTRY_SIZE = 8 + 8

        const val BOUND_UPPER: Short = 0b01
        const val BOUND_LOWER: Short = 0b10
        const val BOUND_EXACT: Short = 0b11

        const val SEED = 84927659

        var HASH_BLACK_TURN: Long = 0
        val HASH_PIECES: LongArray = LongArray(64 * 12)
        val HASH_CASTLING: LongArray = LongArray(4)
        val HASH_EP_FILE: LongArray = LongArray(8)

        fun pieceHash(piece: Piece, square: Square): Long {
            return HASH_PIECES[square.v * 12 + piece.color.idx * 6 + piece.type.idx]
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

    @JvmInline
    value class TTValue(val v: Long) {
        val bestMove: CompactMove get() = CompactMove((v and 0x3FFFFF).toInt())
        val score: Score get() = ((v ushr 22) and 0xFFFF).toShort().toInt()
        val draft: Short get() = ((v ushr 38) and 0xFF).toShort()
        val boundType: BoundType get() = ((v ushr 46) and 0b11).toShort()

        constructor(
            bestMove: CompactMove, score: Score, draft: Int, boundType: BoundType
        ) : this(
            (bestMove.v.toLong() and 0x3FFFFF) // +22 bits
            or ((score and 0xFFFF).toLong() shl 22) // +16 bits
            or ((draft and 0xFF).toLong() shl 38) // +8 bits
            or ((boundType.toLong() and 0b11) shl 46) // + 2 bits
        ) // 48 bits used

        fun getAdjustedScore(perspective: Color, plyFromRoot: Int): Score {
            return adjustScore(score, perspective, -plyFromRoot)
        }
    }
}