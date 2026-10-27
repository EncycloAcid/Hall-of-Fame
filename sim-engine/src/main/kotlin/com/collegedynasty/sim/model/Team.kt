package com.collegedynasty.sim.model

/** Coaching tendencies that shape (but never directly decide) possession outcomes. 1..99, 50 = neutral. */
data class TeamTactics(
    val pace: Int = 50,
    val threePointEmphasis: Int = 50,
) {
    init {
        require(pace in 1..99) { "pace must be in 1..99" }
        require(threePointEmphasis in 1..99) { "threePointEmphasis must be in 1..99" }
    }
}

data class Team(
    val id: String,
    val name: String,
    val abbreviation: String,
    val roster: List<Player>,
    val tactics: TeamTactics = TeamTactics(),
    /** Optional explicit starters; when null the engine picks a balanced lineup by overall rating. */
    val startingLineup: Lineup? = null,
) {
    init {
        require(id.isNotBlank()) { "Team id must not be blank" }
        require(roster.size >= Lineup.SIZE) { "Team $id needs at least ${Lineup.SIZE} players" }
        require(roster.map { it.id }.toSet().size == roster.size) { "Team $id has duplicate player ids" }
        startingLineup?.let { lineup ->
            val ids = roster.map { it.id }.toSet()
            require(lineup.playerIds.all { it in ids }) { "Starting lineup for $id references unknown players" }
        }
    }

    fun player(id: String): Player = roster.first { it.id == id }
}
