package party.elias.erinn.uci

fun interface CommandHandler {
    fun handle(args: Map<String, List<String>>)
}