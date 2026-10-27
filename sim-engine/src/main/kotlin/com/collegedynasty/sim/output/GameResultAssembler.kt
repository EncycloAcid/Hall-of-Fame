package com.collegedynasty.sim.output

import com.collegedynasty.sim.model.EventType
import com.collegedynasty.sim.model.Game
import com.collegedynasty.sim.model.GameResult
import com.collegedynasty.sim.model.PeriodScore
import com.collegedynasty.sim.model.PlayByPlayEvent

internal object GameResultAssembler {
    fun assemble(game: Game, events: List<PlayByPlayEvent>): GameResult {
        val last = events.last()
        var prevHome = 0
        var prevAway = 0
        val periodScores = events.filter { it.type == EventType.PERIOD_END }.map { e ->
            PeriodScore(e.period, game.rules.periodName(e.period), e.homeScore - prevHome, e.awayScore - prevAway).also {
                prevHome = e.homeScore
                prevAway = e.awayScore
            }
        }
        return GameResult(
            game = game,
            homeScore = last.homeScore,
            awayScore = last.awayScore,
            periodScores = periodScores,
            boxScore = BoxScoreBuilder.build(game.home, game.away, events),
            events = events,
        )
    }
}
