package party.elias

typealias Bitboard = Long
typealias BitboardArray = LongArray

object Bitboards {
    const val A1: Bitboard = 1L
    const val H1: Bitboard = 1L shl 7
    const val A8: Bitboard = 1L shl 56
    const val H8: Bitboard = 1L shl 63
}
