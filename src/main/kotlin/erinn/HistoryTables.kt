package party.elias.erinn

import kotlin.math.abs

class HistoryTables {
    val mainTable: IntArray = IntArray(2 * 64 * 64)
    val contTable: IntArray = IntArray(CONT_PLIES.size * 2 * 6 * 64 * 6 * 64)

    fun update(color: Color, searchStack: SearchStack, plyFromRoot: Int, move: Move, movingPieceType: PieceType, bonus: Int) {
        updateMain(color, move, bonus)
        if (plyFromRoot >= 1)
            updateCont(color, searchStack[plyFromRoot - 1], move, movingPieceType, bonus)
    }

    fun updateMain(color: Color, move: Move, bonus: Int) {
        val clampedBonus = bonus.coerceIn(-HISTORY_MAX, HISTORY_MAX)
        val index = getMainIdx(color, move)

        mainTable[index] += clampedBonus - mainTable[index] * abs(clampedBonus) / HISTORY_MAX
    }

    fun updateCont(color: Color, searchStackEntry: SearchStack.Entry, move: Move, movingPieceType: PieceType, bonus: Int) {
        val clampedBonus = bonus.coerceIn(-HISTORY_MAX, HISTORY_MAX)
        val index = getContIdx(color, searchStackEntry, move, movingPieceType)

        contTable[index] += clampedBonus - contTable[index] * abs(clampedBonus) / HISTORY_MAX
    }

    fun getValue(color: Color, searchStack: SearchStack, plyFromRoot: Int, move: Move, movingPieceType: PieceType): Int {
        var value = mainTable[getMainIdx(color, move)]

        if (plyFromRoot >= 1)
            value += contTable[getContIdx(color, searchStack[plyFromRoot - 1], move, movingPieceType)]

        return value
    }

    private fun getMainIdx(color: Color, move: Move) =
        color.idx * 64 * 64 + move.src.v * 64 + move.dst.v

    private fun getContIdx(color: Color, searchStackEntry: SearchStack.Entry, move: Move, movingPieceType: PieceType) =
        color.idx * 6 * 64 * 6 * 64 + searchStackEntry.movingPieceType.idx * 64 * 6 * 64 + searchStackEntry.move.dst.v * 6 * 64 + movingPieceType.idx * 64 + move.dst.v

    fun age() {
        for (i in mainTable.indices) {
            mainTable[i] /= 2
        }

        for (i in contTable.indices) {
            contTable[i] /= 2
        }
    }

    fun reset() {
        for (i in mainTable.indices) {
            mainTable[i] = 0
        }

        for (i in contTable.indices) {
            contTable[i] = 0
        }
    }

    companion object {
        const val HISTORY_MAX = 1 shl 16
        val CONT_PLIES = intArrayOf(1)
    }
}