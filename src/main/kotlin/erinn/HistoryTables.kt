package party.elias.erinn

import kotlin.math.abs

class HistoryTables {
    val mainTable: IntArray = IntArray(2 * 64 * 64)
    val contTable: IntArray = IntArray(6 * 64 * 12 * 64)

    fun update(searchStack: SearchStack, plyFromRoot: Int, move: Move, movingPiece: Piece, bonus: Int) {
        updateMain(movingPiece.color, move, bonus)
        if (plyFromRoot >= 1 && !searchStack[plyFromRoot - 1].move.isNull())
            updateCont(searchStack[plyFromRoot - 1], move, movingPiece, bonus)
    }

    fun updateMain(color: Color, move: Move, bonus: Int) {
        val clampedBonus = bonus.coerceIn(-HISTORY_MAX, HISTORY_MAX)
        val index = getMainIdx(color, move)

        mainTable[index] += clampedBonus - mainTable[index] * abs(clampedBonus) / HISTORY_MAX
    }

    fun updateCont(searchStackEntry: SearchStack.Entry, move: Move, movingPiece: Piece, bonus: Int) {
        val clampedBonus = bonus.coerceIn(-HISTORY_MAX, HISTORY_MAX)
        val index = getContIdx(searchStackEntry, move, movingPiece)

        contTable[index] += clampedBonus - contTable[index] * abs(clampedBonus) / HISTORY_MAX
    }

    fun getValue(color: Color, searchStack: SearchStack, plyFromRoot: Int, move: Move, movingPiece: Piece): Int {
        var value = mainTable[getMainIdx(color, move)]

        if (plyFromRoot >= 1 && !searchStack[plyFromRoot - 1].move.isNull())
            value += contTable[getContIdx(searchStack[plyFromRoot - 1], move, movingPiece)]

        return value
    }

    private fun getMainIdx(color: Color, move: Move) =
        color.idx * 64 * 64 + move.src.v * 64 + move.dst.v

    private fun getContIdx(searchStackEntry: SearchStack.Entry, move: Move, movingPiece: Piece) =
        searchStackEntry.movingPiece.type.idx * 64 * 12 * 64 + searchStackEntry.move.dst.v * 12 * 64 + movingPiece.idx * 64 + move.dst.v

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
        const val HISTORY_MAX = 1 shl 14
    }
}