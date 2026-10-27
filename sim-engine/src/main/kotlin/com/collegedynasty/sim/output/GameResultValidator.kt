package com.collegedynasty.sim.output

import com.collegedynasty.sim.model.EventType
import com.collegedynasty.sim.model.GameResult
import com.collegedynasty.sim.model.Lineup
import com.collegedynasty.sim.model.TurnoverType

/**
 * Independent consistency checks on a finished game. Returns human-readable problems (empty when
 * the result is internally consistent). Used by the unit tests and the batch harness.
 */
object GameResultValidator {

    fun validate(result: GameResult): List<String> {
        val problems = mutableListOf<String>()
        fun check(condition: Boolean, message: () -> String) {
            if (!condition) problems += message()
        }

        val game = result.game
        val rules = game.rules
        val events = result.events
        val gameSeconds = rules.gameLengthSeconds(result.periodsPlayed)

        // Event stream ordering and clock sanity.
        check(events.isNotEmpty() && events.last().type == EventType.GAME_END) { "Last event must be GAME_END" }
        events.forEachIndexed { i, e ->
            check(e.sequence == i + 1) { "Event sequence gap at index $i" }
            check(e.clockSeconds in 0..rules.periodLength(e.period)) { "Clock out of range at #${e.sequence}" }
            if (i > 0) {
                val prev = events[i - 1]
                check(e.elapsedSeconds >= prev.elapsedSeconds) { "Elapsed time went backwards at #${e.sequence}" }
                check(e.period >= prev.period) { "Period went backwards at #${e.sequence}" }
                val homeDelta = e.homeScore - prev.homeScore
                val awayDelta = e.awayScore - prev.awayScore
                val expectedHome = if (e.teamId == game.home.id) e.points else 0
                val expectedAway = if (e.teamId == game.away.id) e.points else 0
                check(homeDelta == expectedHome && awayDelta == expectedAway) { "Score change does not match event points at #${e.sequence}" }
            }
        }
        check(events.last().elapsedSeconds == gameSeconds) { "Game ended at ${events.last().elapsedSeconds}s, expected $gameSeconds" }

        // Final score reconciliation: events, scoreboard, box score, scoring arithmetic.
        for ((teamId, box, final) in listOf(
            Triple(game.home.id, result.boxScore.home, result.homeScore),
            Triple(game.away.id, result.boxScore.away, result.awayScore),
        )) {
            val eventPoints = events.filter { it.teamId == teamId }.sumOf { it.points }
            val t = box.totals
            check(eventPoints == final) { "$teamId: event points $eventPoints != final $final" }
            check(box.players.sumOf { it.points } == final) { "$teamId: player points != final" }
            check(t.points == 2 * t.fieldGoalsMade + t.threePointersMade + t.freeThrowsMade) { "$teamId: team scoring arithmetic" }
            check(t.secondsPlayed == Lineup.SIZE * gameSeconds) { "$teamId: ${t.secondsPlayed}s played, expected ${Lineup.SIZE * gameSeconds}" }
            check(t.assists <= t.fieldGoalsMade) { "$teamId: more assists than made field goals" }
            val steals = events.count { it.teamId != teamId && it.turnoverType == TurnoverType.STEAL }
            check(t.steals == steals) { "$teamId: steals do not match steal turnovers" }
            for (p in box.players) {
                check(p.points == 2 * p.fieldGoalsMade + p.threePointersMade + p.freeThrowsMade) { "${p.playerId}: scoring arithmetic" }
                check(p.fieldGoalsMade <= p.fieldGoalsAttempted) { "${p.playerId}: FGM > FGA" }
                check(p.threePointersMade <= p.threePointersAttempted) { "${p.playerId}: 3PM > 3PA" }
                check(p.threePointersAttempted <= p.fieldGoalsAttempted) { "${p.playerId}: 3PA > FGA" }
                check(p.freeThrowsMade <= p.freeThrowsAttempted) { "${p.playerId}: FTM > FTA" }
                check(p.secondsPlayed in 0..gameSeconds) { "${p.playerId}: minutes out of range" }
                check(p.personalFouls <= rules.personalFoulLimit) { "${p.playerId}: exceeded foul limit" }
            }
        }
        val homeBlocks = result.boxScore.home.totals.blocks
        val awayMisses = result.boxScore.away.totals.let { it.fieldGoalsAttempted - it.fieldGoalsMade }
        check(homeBlocks <= awayMisses) { "Home blocks exceed away missed field goals" }

        // Period scores and termination rules.
        check(result.periodScores.sumOf { it.home } == result.homeScore) { "Home period scores do not sum to final" }
        check(result.periodScores.sumOf { it.away } == result.awayScore) { "Away period scores do not sum to final" }
        check(result.periodsPlayed >= rules.regulationPeriods) { "Fewer periods than regulation" }
        check(!result.isTie || result.overtimes == rules.maxOvertimes) { "Game ended tied before the overtime limit" }

        // Lineups: always five distinct players; disqualified players never return.
        val onCourt = mutableMapOf<String, MutableSet<String>>()
        val disqualified = mutableSetOf<String>()
        for (e in events) {
            when (e.type) {
                EventType.PERIOD_START -> {
                    onCourt[game.home.id] = e.homeLineup!!.toMutableSet()
                    onCourt[game.away.id] = e.awayLineup!!.toMutableSet()
                    (e.homeLineup + e.awayLineup).forEach { check(it !in disqualified) { "Disqualified $it started a period" } }
                }
                EventType.SUBSTITUTION -> {
                    val court = onCourt.getValue(e.teamId!!)
                    check(e.secondaryPlayerId in court) { "Substituted-out player not on court at #${e.sequence}" }
                    check(e.playerId !in court) { "Substituted-in player already on court at #${e.sequence}" }
                    check(e.playerId !in disqualified) { "Disqualified player re-entered at #${e.sequence}" }
                    court.remove(e.secondaryPlayerId)
                    court.add(e.playerId!!)
                }
                EventType.FOUL_OUT -> disqualified += e.playerId!!
                else -> Unit
            }
            onCourt.values.forEach { check(it.size == Lineup.SIZE) { "Lineup size ${it.size} at #${e.sequence}" } }
        }
        return problems.distinct()
    }
}
