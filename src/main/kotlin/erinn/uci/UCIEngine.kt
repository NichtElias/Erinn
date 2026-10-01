package party.elias.erinn.uci

import kotlinx.coroutines.*
import party.elias.erinn.*
import party.elias.erinn.nnue.NNUE
import party.elias.erinn.uci.Option.Type
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

object UCIEngine {
    val searchScope = CoroutineScope(Dispatchers.Default + Job())
    val engine: Engine = Engine()
    val options: ArrayList<Option<*>> = ArrayList()
    var running = true

    init {
        Options // ensure the options are initialized by loading the object
    }

    fun handleUci(cmd: Command) {
        println("id name Erinn 2")
        println("id author NichtElias")
        options.forEach {
            println(it.getUCIMessage())
        }
        println("uciok")
    }

    fun handleDebug(cmd: Command) {
        if (cmd.hasFlag("on")) {
            engine.debugMode = true
        } else if (cmd.hasFlag("off")) {
            engine.debugMode = false
        }
    }

    fun handleSetOption(cmd: Command) {
        val name = cmd.getGreedyKeywordArgString("name", setOf("value"))

        if (name == null || name == "")
            return

        val option = options.find { option -> option.name == name }

        option?.set(cmd.getGreedyKeywordArgString("value", setOf("name")))
    }

    fun handleIsReady(cmd: Command) {
        NNUE.init()
        println("readyok")
    }

    fun handleUciNewGame(cmd: Command) {
        engine.tt.clear()
    }

    fun handlePosition(cmd: Command) {
        val fenArg = cmd.getGreedyKeywordArgString("fen", setOf("moves"))
        val movesArg = cmd.getGreedyKeywordArg("moves", setOf("fen"))

        engine.searchStack.resetKillers()
        engine.historyTables.age()

        var startingFen = ""
        if (cmd.hasFlag("startpos")) {
            startingFen = Board.STARTING_FEN
        }

        if (!fenArg.isNullOrBlank()) {
            startingFen = fenArg
        }

        engine.position = Board.fromFen(startingFen)

        movesArg?.forEach {
            engine.position.doMove(Move.fromUci(it, engine.position), Engine.MAX_SEARCH_PLY)
        }
    }

    fun handleGo(cmd: Command) {
        val perftArg = cmd.getKeywordArg("perft")
        val depthArg = cmd.getKeywordArg("depth")?.toIntOrNull() ?: 48
        val nodesArg = cmd.getKeywordArg("nodes")?.toLongOrNull() ?: Long.MAX_VALUE
        val wTimeArg = cmd.getKeywordArg("wtime")?.toIntOrNull() ?: -1
        val bTimeArg = cmd.getKeywordArg("btime")?.toIntOrNull() ?: -1
        val wIncArg = cmd.getKeywordArg("winc")?.toIntOrNull() ?: 0
        val bIncArg = cmd.getKeywordArg("binc")?.toIntOrNull() ?: 0
        val moveTimeArg = cmd.getKeywordArg("movetime")?.toIntOrNull() ?: -1

        if (perftArg != null) {
            val perftDepth = perftArg.toIntOrNull() ?: return

            val results = engine.perftDivide(perftDepth)

            var total = 0L
            for ((move, nodes) in results) {
                println("${move.toUci()}: $nodes")
                total += nodes
            }
            println()
            println("Nodes searched: $total")
            println()
        } else {
            val ourTime = if (engine.position.turn == Color.BLACK) bTimeArg else wTimeArg
            val ourInc = if (engine.position.turn == Color.BLACK) bIncArg else wIncArg

            var softTime = Duration.INFINITE
            var hardTime = Duration.INFINITE

            if (moveTimeArg != -1) {
                softTime = moveTimeArg.milliseconds
                hardTime = moveTimeArg.milliseconds
            } else if (ourTime != -1) {
                softTime = (ourTime / 35 + ourInc / 2).milliseconds
                hardTime = max(ourTime * 70 / 100 - Options.moveTimeBuffer.value, 0).milliseconds
            }

            val limits = Limits(
                depthArg,
                hardNodes = nodesArg,
                softTime = softTime,
                hardTime = hardTime
            )

            val searchTimer = searchScope.launch {
                delay(limits.hardTime)
                engine.stop = true
            }

            searchScope.launch {
                try {
                    val (move, score) = engine.iterDeep(limits)
                    println("bestmove ${move.toUci()}")
                } catch (e: Exception) {
                    e.printStackTrace()
                    exitProcess(1)
                }

                searchTimer.cancel()
            }
        }
    }

    fun handleStop(cmd: Command) {
        engine.stop = true
    }

    fun handleQuit(cmd: Command) {
        running = false
    }

    fun handleShow(cmd: Command) {
        if (cmd.hasFlag("fen")) {
            println(engine.position.toFen())
        } else {
            print(engine.position.toString())

            if (engine.position.isDrawByRepetition()) {
                println("draw by threefold repetition")
            }
        }
    }

    fun handleEval(cmd: Command) {
        engine.accStack.init(engine.position)
        println(scoreString(engine.evaluate(0)))
    }

    fun handleGenPos(cmd: Command) {
        val nodesArg = cmd.getKeywordArg("nodes")?.toLongOrNull()
        val gamesArg = cmd.getKeywordArg("games")?.toIntOrNull()
        val seedArg = cmd.getKeywordArg("seed")?.toIntOrNull()
        val fileArg = cmd.getGreedyKeywordArgString("file", setOf("nodes", "games", "seed"))

        if (nodesArg == null || gamesArg == null || seedArg == null || fileArg == null) return

        engine.genEvalPosFromSelfPlayGames(
            seedArg,
            nodesArg,
            gamesArg,
            File(fileArg)
        )

        println("genposdone")
    }

    fun run() {
        while (running) {
            val cmd = Command(readln())

            val commandDefinition = try {
                CommandDefinition.valueOf(cmd.command.uppercase())
            } catch (e: IllegalArgumentException) {
                continue
            }

            commandDefinition.handler.accept(cmd)
        }
    }

    fun uciPositionCmd(fen: String, moves: List<Move>): String {
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

    fun sendUciInfo(depth: Int, time: Duration, nodes: Long, score: Score, pv: ArrayList<Move>, ttFullPerMill: Int) {
        val nps = nodes * 1000 / max(time.toInt(DurationUnit.MILLISECONDS), 1)
        val scoreStr = scoreString(score)
        if (pv.isEmpty()) {
            println("info depth $depth time ${time.toInt(DurationUnit.MILLISECONDS)} nodes $nodes score $scoreStr nps $nps hashfull $ttFullPerMill")
        } else {
            var pvStr = ""
            for (move in pv) {
                pvStr += " ${move.toUci()}"
            }
            println("info depth $depth time ${time.toInt(DurationUnit.MILLISECONDS)} nodes $nodes score $scoreStr nps $nps hashfull $ttFullPerMill pv$pvStr")
        }
    }

    private fun <T> registerOption(option: Option<T>): Option<T> {
        options.add(option)
        return option
    }

    object Options {
        val hash = registerOption(Option("Hash", Type.SPIN, 256, 1, Int.MAX_VALUE) { value ->
            engine.setHashTableSize(value)
        })
        val moveTimeBuffer = registerOption(Option("MoveTimeBuffer", Type.SPIN, 20, 0, 1000))
    }
}