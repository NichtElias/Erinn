package party.elias.erinn

class SearchStack {
    val stack: Array<Entry> = Array(Engine.MAX_SEARCH_PLY) { Entry() }

    operator fun get(plyIndex: Int): Entry = stack[plyIndex]

    fun forEach(action: (Entry) -> Unit) {
        for (i in stack.indices) {
            action(this[i])
        }
    }

    fun putKiller(move: Move, plyFromRoot: Int) {
        if (move != stack[plyFromRoot].killers[0]) {
            stack[plyFromRoot].killers[1] = stack[plyFromRoot].killers[0]
        }
        stack[plyFromRoot].killers[0] = move
    }

    fun isKiller(move: Move, plyFromRoot: Int): Boolean {
        return stack[plyFromRoot].killers[0] == move || stack[plyFromRoot].killers[1] == move
    }

    fun resetKillers() {
        stack.forEach { sse ->
            sse.killers[0] = Move.NULL_MOVE
            sse.killers[1] = Move.NULL_MOVE
        }
    }

    fun pushPvMove(plyFromRoot: Int, move: Move) {
        stack[plyFromRoot].pv[0] = move
        System.arraycopy(
            stack[plyFromRoot + 1].pv.array, 0,
            stack[plyFromRoot].pv.array, 1,
            stack[plyFromRoot + 1].pvLength
        )
        stack[plyFromRoot].pvLength = stack[plyFromRoot + 1].pvLength + 1
    }

    class Entry {
        val pv: MoveArray = MoveArray(Engine.MAX_SEARCH_PLY)
        var pvLength: Int = 0
        val killers: MoveArray = MoveArray(2)
        var staticEval: Score = Engine.INVALID_SCORE
    }
}