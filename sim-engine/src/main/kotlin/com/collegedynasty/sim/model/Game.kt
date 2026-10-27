package com.collegedynasty.sim.model

import com.collegedynasty.sim.rules.BasketballRules
import com.collegedynasty.sim.rules.LeagueTuning

/** Input description of a game to simulate. The seed fully determines the outcome. */
data class Game(
    val id: String,
    val home: Team,
    val away: Team,
    val seed: Long,
    val rules: BasketballRules = BasketballRules.NCAA_MEN,
    val tuning: LeagueTuning = LeagueTuning.COLLEGE,
    val neutralSite: Boolean = false,
) {
    init {
        require(home.id != away.id) { "A team cannot play itself" }
        val homeIds = home.roster.map { it.id }.toSet()
        require(away.roster.none { it.id in homeIds }) { "Player ids must be unique across both teams" }
    }
}

data class PeriodScore(val period: Int, val label: String, val home: Int, val away: Int)

/** Complete, immutable output of one simulated game. */
data class GameResult(
    val game: Game,
    val homeScore: Int,
    val awayScore: Int,
    val periodScores: List<PeriodScore>,
    val boxScore: BoxScore,
    /** Chronological play-by-play; suitable for instant display or incremental reveal. */
    val events: List<PlayByPlayEvent>,
) {
    val periodsPlayed: Int get() = periodScores.size
    val overtimes: Int get() = (periodsPlayed - game.rules.regulationPeriods).coerceAtLeast(0)
    val isTie: Boolean get() = homeScore == awayScore
    val winner: Team? get() = when {
        homeScore > awayScore -> game.home
        awayScore > homeScore -> game.away
        else -> null
    }
}
