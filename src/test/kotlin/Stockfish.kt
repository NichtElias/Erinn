import party.elias.Move
import party.elias.uci.uciPositionCmd
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class Stockfish {
    val process: Process = ProcessBuilder("stockfish").start()
    private val reader = BufferedReader(InputStreamReader(process.inputStream))
    private val writer = BufferedWriter(OutputStreamWriter(process.outputStream))

    init {
        reader.readLine() // skip first line
    }

    private fun send(cmd: String) {
        writer.write("$cmd\n")
        writer.flush()
    }

    fun setPosition(fen: String, vararg moves: Move) {
        send(uciPositionCmd(fen, *moves))
    }

    fun perft(depth: Int): Map<String, Long> {
        send("go perft $depth")

        val results = HashMap<String, Long>()

        var line: String
        do {
            line = reader.readLine()
        } while (line == "" || line.startsWith("info"))

        while (line != "" && !line.startsWith("Nodes searched: ")) {
            results[line.split(":")[0]] = line.split(" ")[1].toLong()

            line = reader.readLine()
        }

        if (!line.startsWith("Nodes searched: "))
            reader.readLine() // skip "Nodes searched: x"
        reader.readLine() // skip empty line

        return results
    }
}