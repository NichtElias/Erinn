package party.elias.uci

import party.elias.Board
import party.elias.Engine
import party.elias.Move
import kotlin.collections.isEmpty

fun run() {

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

            // TODO

        } else if (cmd[0] == "position") {

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

            if (cmd[1] == "perft") {
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
            }

        } else if (cmd[0] == "stop") {

            // TODO

        } else if (cmd[0] == "show") { // nonstandard

            print(engine.position.toString())

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