package party.elias.uci

import party.elias.Engine

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

            // TODO

        } else if (cmd[0] == "go") {

            // TODO

        } else if (cmd[0] == "stop") {

            // TODO

        } else if (cmd[0] == "show") { // nonstandard

            print(engine.current.toString())

        }
    }

}