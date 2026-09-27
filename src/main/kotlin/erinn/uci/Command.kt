package party.elias.erinn.uci

class Command(cmdString: String) {
    val tokens: List<String> = cmdString.trim().split(" ")
    val command: String = tokens.getOrNull(0) ?: ""
    val args: List<String> = if (tokens.isNotEmpty()) tokens.subList(1, tokens.size) else emptyList()

    fun hasFlag(flag: String): Boolean = args.contains(flag)

    fun getKeywordArg(name: String): String? {
        val index = args.indexOf(name)
        if (index != -1 && index + 1 < args.size) {
            return args[index + 1]
        }
        return null
    }

    fun getGreedyKeywordArg(name: String, stopTokens: Set<String> = emptySet()): List<String>? {
        val index = args.indexOf(name)
        if (index == -1) return null
        if (index + 1 >= args.size) return emptyList()

        val resultParts = mutableListOf<String>()

        for (i in (index + 1) until args.size) {
            val token = args[i]
            if (stopTokens.contains(token)) {
                break
            }
            resultParts.add(token)
        }

        return resultParts
    }

    fun getGreedyKeywordArgString(name: String, stopTokens: Set<String> = emptySet()): String? {
        return getGreedyKeywordArg(name, stopTokens)?.joinToString(" ")
    }
}