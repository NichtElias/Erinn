package party.elias.erinn.uci

class Command(val name: String) {
    val params: ArrayList<Parameter> = ArrayList()
    var handler: CommandHandler? = null

    fun with(parameter: Parameter): Command {
        params.add(parameter)
        return this
    }

    fun withHandler(handler: CommandHandler): Command {
        this.handler = handler
        return this
    }

    fun parseAndHandle(tokens: List<String>) {
        val ctx = ParseContext(this, tokens, 1)

        val args: HashMap<String, List<String>> = HashMap()

        while (!ctx.hasReachedEnd()) {
            val token = ctx.nextToken()
            val param = params.find { p -> p.name == token }

            if (param == null) continue

            args[param.name] = param.parse(ctx)
        }

        handler?.handle(args)
    }

    fun hasParameterWithName(name: String): Boolean {
        return params.any { p -> p.name == name }
    }

    class ParseContext(val command: Command, var tokens: List<String>, var index: Int) {
        fun nextToken(): String {
            return tokens[index++]
        }

        fun hasReachedEnd(): Boolean {
            return index >= tokens.size
        }
    }
}