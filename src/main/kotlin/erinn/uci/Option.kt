package party.elias.erinn.uci

class Option<T>(
    val name: String,
    val type: Type<T>,
    val default: T,
    val min: T? = null,
    val max: T? = null,
    val vars: List<T>? = null,
    val changeListener: (T) -> Unit = {}
) {
    var value = default

    fun set(rawValue: String?) {
        val parsed = if (rawValue != null) {
            type.parse(rawValue)
        } else {
            default
        }

        changeListener(parsed)
        value = parsed
    }

    fun getUCIMessage(): String {
        val s = StringBuilder("option name $name type ${type.name()}")

        if (default != null)
            s.append(" default $default")
        if (min != null)
            s.append(" min $min")
        if (max != null)
            s.append(" max $max")

        vars?.forEach { s.append(" var $it") }

        return s.toString()
    }

    sealed interface Type<out T> {
        fun parse(s: String): T
        fun name(): String

        object CHECK : Type<Boolean> {
            override fun parse(s: String): Boolean = s.toBooleanStrict()
            override fun name(): String = "check"
        }

        object SPIN : Type<Int> {
            override fun parse(s: String): Int = s.toInt()
            override fun name(): String = "spin"
        }

        object COMBO : Type<String> {
            override fun parse(s: String): String = s
            override fun name(): String = "combo"
        }

        object BUTTON : Type<Unit> {
            override fun parse(s: String): Unit {}
            override fun name(): String = "button"
        }

        object STRING : Type<String> {
            override fun parse(s: String): String = s
            override fun name(): String = "string"
        }
    }
}