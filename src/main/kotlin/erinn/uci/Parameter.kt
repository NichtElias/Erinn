package party.elias.erinn.uci

class Parameter(val name: String, val maxValues: Int = 1) {

    fun parse(ctx: Command.ParseContext): List<String> {
        val values: ArrayList<String> = ArrayList()

        while (!ctx.hasReachedEnd() && (values.size < maxValues || maxValues == -1)) {
            val token = ctx.nextToken()
            if (ctx.command.hasParameterWithName(token)) {
                ctx.index--
                break
            }

            values.add(token)
        }

        return values
    }
}