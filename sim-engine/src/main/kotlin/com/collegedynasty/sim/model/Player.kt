package com.collegedynasty.sim.model

import kotlin.math.roundToInt

/**
 * Simulation-facing player model. Deliberately independent of persistence (database entities)
 * and presentation (UI models); map to/from those at the module boundary.
 */
data class Player(
    val id: String,
    val firstName: String,
    val lastName: String,
    val position: Position,
    val jerseyNumber: Int,
    val attributes: PlayerAttributes,
) {
    init {
        require(id.isNotBlank()) { "Player id must not be blank" }
        require(jerseyNumber in 0..99) { "Jersey number must be in 0..99" }
    }

    val fullName: String get() = "$firstName $lastName"
    val shortName: String get() = "${firstName.first()}. $lastName"

    /**
     * Coarse overall rating used only by the coaching/rotation logic to order the depth chart.
     * It is never used directly to decide possession outcomes.
     */
    val overall: Int
        get() = with(attributes) {
            val bestScoring = maxOf(insideScoring, midrangeShooting, threePointShooting)
            val avgScoring = (insideScoring + midrangeShooting + threePointShooting) / 3.0
            val creation = (playmaking + ballHandling) / 2.0
            val defense = if (position.isBig) {
                0.35 * perimeterDefense + 0.65 * interiorDefense
            } else {
                0.75 * perimeterDefense + 0.25 * interiorDefense
            }
            val creationWeight = if (position.isGuard) 0.20 else 0.08
            val reboundWeight = if (position.isBig) 0.14 else 0.06
            val raw = 0.22 * bestScoring + 0.10 * avgScoring + creationWeight * creation +
                0.18 * defense + reboundWeight * rebounding + 0.10 * athleticism +
                0.10 * basketballIq + 0.04 * freeThrows
            (raw / (0.22 + 0.10 + creationWeight + 0.18 + reboundWeight + 0.10 + 0.10 + 0.04)).roundToInt()
        }
}
