package party.elias.erinn.uci

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import party.elias.erinn.Board
import party.elias.erinn.Color
import party.elias.erinn.Engine
import party.elias.erinn.Limits
import party.elias.erinn.Move
import party.elias.erinn.Score
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

    val commands: List<Command> = listOf(
        Command("uci")
            .withHandler {
                println("id name Erinn 1.1")
                println("id author NichtElias")
                options.forEach {
                    println(it.getUCIMessage())
                }
                println("uciok")
            },

        Command("setoption")
            .with(Parameter("name"))
            .with(Parameter("value"))
            .withHandler { args ->
                if (args["name"].isNullOrEmpty()) return@withHandler

                val option = options.find { option -> option.name == args["name"]!!.getOrNull(0) }

                option?.set(args["value"]?.getOrNull(0))
            },

        Command("isready")
            .withHandler {
                NNUE.init()
                println("readyok")
            },

        Command("ucinewgame")
            .withHandler {
                engine.tt.clear()
            },

        Command("position")
            .with(Parameter("startpos", 0))
            .with(Parameter("fen", 6))
            .with(Parameter("moves", -1))
            .withHandler { args ->
                val fenArg = args["fen"]
                val movesArg = args["moves"]

                var startingFen = ""
                if (args.containsKey("startpos")) {
                    startingFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
                }

                if (fenArg != null) {
                    startingFen = fenArg.joinToString(separator = " ")
                }

                engine.position = Board.fromFen(startingFen)

                movesArg?.forEach {
                    engine.position.doMove(Move.fromUci(it, engine.position), Engine.MAX_SEARCH_PLY)
                }
            },

        Command("go")
            .with(Parameter("perft"))
            .with(Parameter("depth"))
            .with(Parameter("nodes"))
            .with(Parameter("wtime"))
            .with(Parameter("btime"))
            .with(Parameter("winc"))
            .with(Parameter("binc"))
            .with(Parameter("movetime"))
            .withHandler { args ->
                val perftArg = args["perft"]
                val depthArg = getSingleIntArg(args["depth"], 48)
                val nodesArg = getSingleLongArg(args["nodes"], Long.MAX_VALUE)
                val wTimeArg = getSingleIntArg(args["wtime"], -1)
                val bTimeArg = getSingleIntArg(args["btime"], -1)
                val wIncArg = getSingleIntArg(args["winc"], 0)
                val bIncArg = getSingleIntArg(args["binc"], 0)
                val moveTimeArg = getSingleIntArg(args["movetime"], -1)

                if (perftArg != null) {
                    if (perftArg.isEmpty()) return@withHandler

                    val results = engine.perftDivide(perftArg.first().toInt())

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
            },

        Command("stop")
            .withHandler {
                engine.stop = true
            },

        Command("quit")
            .withHandler {
                running = false
            },

        Command("show")
            .with(Parameter("fen", 0))
            .withHandler { args ->
                if (args.containsKey("fen")) {
                    println(engine.position.toFen())
                } else {
                    print(engine.position.toString())

                    if (engine.position.isDrawByRepetition()) {
                        println("draw by threefold repetition")
                    }
                }
            },

        Command("eval")
            .withHandler {
                engine.accStack.init(engine.position)
                println(scoreString(engine.evaluate(0)))
            },

        Command("genpos")
            .with(Parameter("nodes"))
            .with(Parameter("games"))
            .with(Parameter("seed"))
            .with(Parameter("file", -1))
            .withHandler { args ->
                val nodesArg = getSingleLongArg(args["nodes"], -1)
                val gamesArg = getSingleIntArg(args["games"], -1)
                val seedArg = args["seed"]
                val fileArg = args["file"]

                if (nodesArg < 0 || gamesArg < 0 || seedArg == null || fileArg == null) return@withHandler

                val filePath = fileArg.joinToString(separator = " ")

                engine.genEvalPosFromSelfPlayGames(
                    getSingleIntArg(seedArg, 0),
                    nodesArg,
                    gamesArg,
                    File(filePath)
                )

                println("genposdone")
            }
    )


    fun run() {
        while (running) {
            val cmdTokens = readln().trim().split(" ")

            commands.find { it.name == cmdTokens[0] }?.parseAndHandle(cmdTokens)
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


    private fun getSingleIntArg(arg: List<String>?, default: Int): Int {
        if (arg.isNullOrEmpty()) return default
        return arg.first().toInt()
    }

    private fun getSingleLongArg(arg: List<String>?, default: Long): Long {
        if (arg.isNullOrEmpty()) return default
        return arg.first().toLong()
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
        val debug = registerOption(Option("Debug", Type.CHECK, false) { value ->
            engine.debugMode = value
        })
    }
}