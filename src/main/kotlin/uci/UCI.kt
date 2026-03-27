package party.elias.uci

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import party.elias.Board
import party.elias.Color
import party.elias.Engine
import party.elias.Limits
import party.elias.Move
import party.elias.Score
import party.elias.TranspositionTable
import kotlin.collections.isEmpty
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

val searchScope = CoroutineScope(Dispatchers.Default + Job())

suspend fun run() {

    val engine = Engine()

    var running = true

    while (running) {
        val cmd = readln().split(" ")

        if (cmd[0] == "quit") {

            running = false

        } else if (cmd[0] == "uci") {

            println("id name schachn")
            println("id author NichtElias")
            println("uciok")

        } else if (cmd[0] == "isready") {

            println("readyok")

        } else if (cmd[0] == "ucinewgame") {

            engine.tt.clear()

        } else if (cmd[0] == "position") {

            engine.resetKillers()

            var i = 1
            if (cmd[i] == "startpos") {
                engine.position = Board.startPos()
                i++
            } else if (cmd[i] == "fen") {
                i++
                val fenString = StringBuilder()
                while (i < cmd.size && cmd[i] != "moves") {
                    if (!fenString.isEmpty()) {
                        fenString.append(" ")
                    }
                    fenString.append(cmd[i])
                    i++
                }

                engine.position = Board.fromFen(fenString.toString())
            }

            if (i < cmd.size && cmd[i] == "moves") {
                i++

                while (i < cmd.size) {
                    engine.position.doMove(Move.fromUci(cmd[i++], engine.position))
                }
            }

        } else if (cmd[0] == "go") {

            if (cmd.size > 1 && cmd[1] == "perft") {
                val depth = cmd[2].toInt()

                val results = engine.perftDivide(depth)

                var total = 0L
                for ((move, nodes) in results) {
                    println("${move.toUci()}: $nodes")
                    total += nodes
                }
                println()
                println("Nodes searched: $total")
                println()
            } else {

                var depthLimit = 64
                var bTime = -1
                var wTime = -1
                var bInc = 0
                var wInc = 0
                var moveTime = -1

                var i = 1
                while (i < cmd.size) {
                    when (cmd[i]) {
                        "depth" -> depthLimit = cmd[++i].toInt()
                        "btime" -> bTime = cmd[++i].toInt()
                        "wtime" -> wTime = cmd[++i].toInt()
                        "binc" -> bInc = cmd[++i].toInt()
                        "winc" -> wInc = cmd[++i].toInt()
                        "movetime" -> moveTime = cmd[++i].toInt()
                    }
                    i++
                }

                val ourTime = if (engine.position.turn == Color.BLACK) bTime else wTime
                val ourInc = if (engine.position.turn == Color.BLACK) bInc else wInc

                var limits = if (moveTime != -1) {
                    Limits(depthLimit, moveTime.milliseconds, moveTime.milliseconds)
                } else if (ourTime != -1) {
                    val softLimit = ourTime / 30 + ourInc / 2
                    // -50 is because the gui doesn't immediately receive the bestmove once the search is stopped
                    val hardLimit = min(ourTime - 50, softLimit * 3)
                    Limits(depthLimit, min(softLimit, hardLimit).milliseconds, hardLimit.milliseconds)
                } else {
                    Limits(depthLimit)
                }

                val searchTimer = searchScope.launch {
                    delay(limits.hardTime)
                    engine.stop = true
                }

                searchScope.launch {
                    val (move, score) = engine.iterDeep(limits)
                    println("bestmove ${move.toUci()} $score")

                    searchTimer.cancel()
                }
            }

        } else if (cmd[0] == "stop") {

            engine.stop = true

        } else if (cmd[0] == "show") { // nonstandard

            print(engine.position.toString())

            if (engine.position.isDrawByRepetition()) {
                println("draw by threefold repetition")
            }

        }
    }

}

fun uciPositionCmd(fen: String, vararg moves: Move): String {
    val sb = StringBuilder()

    for (move in moves) {
        sb.append(move.toUci())
        sb.append(" ")
    }

    return if (moves.isEmpty()) {
        "position fen $fen"
    } else {
        "position fen $fen moves $sb"
    }
}

fun sendUciInfo(depth: Int, time: Duration, nodes: Long, score: Score, currentBestMove: Move) {
    val nps = nodes * 1000 / max(time.toInt(DurationUnit.MILLISECONDS), 1)
    val scoreStr = if (abs(score) >= Engine.MIN_MATE_SCORE) {
        if (score >= 0) {
            "mate ${(Engine.MATE_SCORE - score + 1) / 2}"
        } else {
            "mate ${(-Engine.MATE_SCORE - score) / 2}"
        }
    } else {
        "cp $score"
    }
    if (currentBestMove == Move.NULL_MOVE) {
        println("info depth $depth time ${time.toInt(DurationUnit.MILLISECONDS)} nodes $nodes score $scoreStr nps $nps")
    } else {
        println("info depth $depth time ${time.toInt(DurationUnit.MILLISECONDS)} nodes $nodes pv ${currentBestMove.toUci()} score $scoreStr nps $nps")
    }
}