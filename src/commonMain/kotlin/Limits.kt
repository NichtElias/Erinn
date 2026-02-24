package party.elias

import kotlin.time.Duration

data class Limits(val depth: Int = 64, val softTime: Duration = Duration.INFINITE, val hardTime: Duration = Duration.INFINITE)
