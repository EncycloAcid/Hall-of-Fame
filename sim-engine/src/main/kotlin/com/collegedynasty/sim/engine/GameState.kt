package com.collegedynasty.sim.engine

import com.collegedynasty.sim.model.Player
import com.collegedynasty.sim.model.PlayerAttributes
import com.collegedynasty.sim.model.Team

/** Mutable per-game player state. Internal to the engine; never exposed in results. */
internal class PlayerState(val player: Player, val teamId: String) {
    var energy: Double = 100.0
    var fouls: Int = 0
    var disqualified: Boolean = false

    val id: String get() = player.id
    val attrs: PlayerAttributes get() = player.attributes

    /** Attribute value scaled by fatigue: a fully rested player plays at 100%, an exhausted one at 86%. */
    fun eff(value: Int): Double = value * (0.86 + 0.14 * energy / 100.0)
}

internal class TeamState(val team: Team, val isHome: Boolean) {
    val players: List<PlayerState> = team.roster.map { PlayerState(it, team.id) }
    val byId: Map<String, PlayerState> = players.associateBy { it.id }
    val onCourt: MutableList<PlayerState> = mutableListOf()
    var score: Int = 0
    var teamFouls: Int = 0

    val id: String get() = team.id

    fun courtAverage(selector: (PlayerState) -> Double): Double = onCourt.sumOf(selector) / onCourt.size
}
