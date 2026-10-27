package com.collegedynasty.sim.model

/** Five distinct players on the floor for one team. */
data class Lineup(val playerIds: List<String>) {
    init {
        require(playerIds.size == SIZE) { "A lineup needs exactly $SIZE players" }
        require(playerIds.toSet().size == SIZE) { "Lineup players must be distinct" }
    }

    companion object {
        const val SIZE = 5
    }
}
