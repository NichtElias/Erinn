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
import party.elias.NNUE
import party.elias.Score
import party.elias.TranspositionTable
import java.io.File
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

    var nnueInitialized = false

    var running = true

    while (running) {
        val cmd = readln().split(" ")

        if (cmd[0] == "quit") {

            running = false

        } else if (cmd[0] == "uci") {

            println("id name schachn")
            println("id author NichtElias")
            println("option name Hash type spin default 256 min 1 max 8192")
            println("uciok")

        } else if (cmd[0] == "setoption") {

            if (cmd[1] == "name") {
                val name = cmd[2]

                if (cmd.size >= 5 && cmd[3] == "value") {
                    val value = cmd[4]

                    when (name) {
                        "Hash" -> {
                            engine.setHashTableSize(value.toInt())
                        }
                    }
                }

            }

        } else if (cmd[0] == "isready") {

            if (!nnueInitialized) {
                NNUE.load()
                nnueInitialized = true
            }
            println("readyok")

        } else if (cmd[0] == "ucinewgame") {

            engine.tt.clear()

        } else if (cmd[0] == "position") {

            engine.resetKillers()
            engine.ageHistory()

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

                var depthLimit = 48
                var bTime = -1
                var wTime = -1
                var bInc = 0
                var wInc = 0
                var moveTime = -1

                var i = 1
                while (i < cmd.size) {
                    when (cmd[i]) {
                        "depth" -> depthLimit = min(cmd[++i].toInt(), 48)
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

            if (cmd.size > 1 && cmd[1] == "fen") {
                println(engine.position.toFen())
            } else {

                print(engine.position.toString())

                if (engine.position.isDrawByRepetition()) {
                    println("draw by threefold repetition")
                }
            }

        } else if (cmd[0] == "eval") {
            // println(scoreString(engine.qSearch(-Engine.MATE_SCORE, Engine.MATE_SCORE)))
            println(scoreString(engine.evaluate()))
        } else if (cmd[0] == "genpos") {

            val depth = cmd[1].toInt()
            val gameCount = cmd[2].toInt()
            val seed = cmd[3].toInt()
            val file = cmd[4]

            engine.genEvalPosFromSelfPlayGames(seed, depth, gameCount, File(file))

            println("genposdone")

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

fun scoreString(score: Score): String {
    return if (abs(score) >= Engine.MIN_MATE_SCORE) {
        if (score >= 0) {
            "mate ${(Engine.MATE_SCORE - score + 1) / 2}"
        } else {
            "mate ${(-Engine.MATE_SCORE - score) / 2}"
        }
    } else {
        "cp $score"
    }
}

fun sendUciInfo(depth: Int, time: Duration, nodes: Long, score: Score, pv: ArrayList<Move>) {
    val nps = nodes * 1000 / max(time.toInt(DurationUnit.MILLISECONDS), 1)
    val scoreStr = scoreString(score)
    if (pv.isEmpty()) {
        println("info depth $depth time ${time.toInt(DurationUnit.MILLISECONDS)} nodes $nodes score $scoreStr nps $nps")
    } else {
        var pvStr = ""
        for (move in pv) {
            pvStr += " ${move.toUci()}"
        }
        println("info depth $depth time ${time.toInt(DurationUnit.MILLISECONDS)} nodes $nodes pv$pvStr score $scoreStr nps $nps")
    }
}