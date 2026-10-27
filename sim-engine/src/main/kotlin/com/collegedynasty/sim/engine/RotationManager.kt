package com.collegedynasty.sim.engine

import com.collegedynasty.sim.model.Lineup
import com.collegedynasty.sim.rules.BasketballRules
import com.collegedynasty.sim.rules.LeagueTuning

/** Game context the coaching logic looks at when deciding substitutions. */
internal data class RotationSituation(
    val period: Int,
    val clockSeconds: Int,
    val elapsedSeconds: Int,
    /** This team's score minus the opponent's. */
    val margin: Int,
)

/**
 * Simple coaching AI. At every dead ball it values each eligible player by overall rating, current
 * energy and foul trouble, picks the best balanced five, and swaps players only when the gain beats
 * a hysteresis margin (so the rotation does not churn on every whistle).
 */
internal class RotationManager(private val rules: BasketballRules, private val tuning: LeagueTuning) {

    fun startingFive(team: TeamState): List<PlayerState> {
        team.team.startingLineup?.let { lineup -> return lineup.playerIds.map(team.byId::getValue) }
        return LineupSelector.select(team.players) { it.player.overall.toDouble() }
    }

    /** Returns (playerOut, playerIn) pairs. Players in [protectedIds] (e.g. a free-throw shooter) stay on. */
    fun substitutions(
        team: TeamState,
        situation: RotationSituation,
        protectedIds: Set<String>,
    ): List<Pair<PlayerState, PlayerState>> {
        val value = { p: PlayerState -> value(p, situation) }
        val eligible = team.players.filter { !it.disqualified }
        val desired = if (eligible.size >= Lineup.SIZE) {
            LineupSelector.select(eligible, value)
        } else {
            // Not enough eligible players: disqualified players already on the floor stay on.
            eligible + team.onCourt.filter { it.disqualified }.take(Lineup.SIZE - eligible.size)
        }
        val outs = team.onCourt
            .filter { it !in desired && it.id !in protectedIds }
            .sortedBy { if (it.disqualified) Double.NEGATIVE_INFINITY else value(it) }
        val ins = desired.filter { it !in team.onCourt }.sortedByDescending(value)
        return outs.zip(ins).filter { (out, inn) -> out.disqualified || value(inn) - value(out) > HYSTERESIS }
    }

    private fun value(p: PlayerState, s: RotationSituation): Double {
        var v = p.player.overall + ENERGY_WEIGHT * (p.energy - 100.0)
        v -= foulTroublePenalty(p, s)
        if (isGarbageTime(s)) v -= p.player.overall * 0.5
        return v
    }

    private fun foulTroublePenalty(p: PlayerState, s: RotationSituation): Double {
        val limit = rules.personalFoulLimit
        val remainingRegulation = rules.regulationSeconds - s.elapsedSeconds
        if (remainingRegulation <= CRUNCH_TIME_SECONDS) return 0.0
        val progress = (s.elapsedSeconds.toDouble() / rules.regulationSeconds).coerceIn(0.0, 1.0)
        val allowed = 1 + (limit - 1) * progress
        return if (p.fouls > allowed) FOUL_TROUBLE_PENALTY else 0.0
    }

    private fun isGarbageTime(s: RotationSituation): Boolean =
        s.period >= rules.regulationPeriods &&
            !rules.isOvertime(s.period) &&
            s.clockSeconds <= tuning.garbageTimeSeconds &&
            kotlin.math.abs(s.margin) >= tuning.garbageTimeMargin

    private companion object {
        const val ENERGY_WEIGHT = 0.55
        const val HYSTERESIS = 3.0
        const val FOUL_TROUBLE_PENALTY = 25.0
        const val CRUNCH_TIME_SECONDS = 6 * 60
    }
}
