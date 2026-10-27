package com.collegedynasty.sim.model

/**
 * Basketball skill ratings on a 1..99 scale. A value of roughly 60 represents an average
 * Division I rotation player; see [com.collegedynasty.sim.rules.LeagueTuning.attributeReference].
 */
data class PlayerAttributes(
    val insideScoring: Int,
    val midrangeShooting: Int,
    val threePointShooting: Int,
    val freeThrows: Int,
    val playmaking: Int,
    val ballHandling: Int,
    val perimeterDefense: Int,
    val interiorDefense: Int,
    val rebounding: Int,
    val athleticism: Int,
    val basketballIq: Int,
) {
    init {
        asMap().forEach { (name, value) ->
            require(value in MIN..MAX) { "$name must be in $MIN..$MAX but was $value" }
        }
    }

    fun asMap(): Map<String, Int> = linkedMapOf(
        "insideScoring" to insideScoring,
        "midrangeShooting" to midrangeShooting,
        "threePointShooting" to threePointShooting,
        "freeThrows" to freeThrows,
        "playmaking" to playmaking,
        "ballHandling" to ballHandling,
        "perimeterDefense" to perimeterDefense,
        "interiorDefense" to interiorDefense,
        "rebounding" to rebounding,
        "athleticism" to athleticism,
        "basketballIq" to basketballIq,
    )

    companion object {
        const val MIN = 1
        const val MAX = 99

        fun uniform(value: Int) = PlayerAttributes(
            value, value, value, value, value, value, value, value, value, value, value,
        )
    }
}
