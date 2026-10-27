package com.collegedynasty.sim.output

import com.collegedynasty.sim.model.BoxScore
import com.collegedynasty.sim.model.EventType
import com.collegedynasty.sim.model.PlayByPlayEvent
import com.collegedynasty.sim.model.PlayerGameStats
import com.collegedynasty.sim.model.ShotType
import com.collegedynasty.sim.model.Team
import com.collegedynasty.sim.model.TeamBoxScore
import com.collegedynasty.sim.model.TeamGameStats

/**
 * Derives a complete box score purely from a play-by-play event list. Works on any prefix of a
 * game's events, which lets a live viewer show an up-to-date box score at any point.
 */
object BoxScoreBuilder {

    private class Line {
        var seconds = 0
        var points = 0
        var fgm = 0
        var fga = 0
        var tpm = 0
        var tpa = 0
        var ftm = 0
        var fta = 0
        var oreb = 0
        var dreb = 0
        var ast = 0
        var stl = 0
        var blk = 0
        var tov = 0
        var pf = 0
    }

    fun build(home: Team, away: Team, events: List<PlayByPlayEvent>): BoxScore {
        val lines = (home.roster + away.roster).associate { it.id to Line() }
        val line = { id: String? -> lines.getValue(requireNotNull(id) { "Event is missing a player id" }) }
        val onCourt = mutableMapOf(home.id to mutableSetOf<String>(), away.id to mutableSetOf())
        val starters = mutableMapOf<String, List<String>>()
        var lastElapsed = events.firstOrNull()?.elapsedSeconds ?: 0

        for (e in events) {
            val dt = e.elapsedSeconds - lastElapsed
            if (dt > 0) onCourt.values.forEach { ids -> ids.forEach { lines.getValue(it).seconds += dt } }
            lastElapsed = e.elapsedSeconds

            when (e.type) {
                EventType.PERIOD_START -> {
                    val h = requireNotNull(e.homeLineup)
                    val a = requireNotNull(e.awayLineup)
                    onCourt[home.id] = h.toMutableSet()
                    onCourt[away.id] = a.toMutableSet()
                    starters.putIfAbsent(home.id, h)
                    starters.putIfAbsent(away.id, a)
                }
                EventType.SUBSTITUTION -> onCourt.getValue(requireNotNull(e.teamId)).apply {
                    remove(e.secondaryPlayerId)
                    add(requireNotNull(e.playerId))
                }
                EventType.SHOT_MADE -> {
                    line(e.playerId).apply {
                        fgm++; fga++; points += e.points
                        if (e.shotType == ShotType.THREE_POINTER) { tpm++; tpa++ }
                    }
                    e.secondaryPlayerId?.let { line(it).ast++ }
                }
                EventType.SHOT_MISSED -> {
                    line(e.playerId).apply {
                        fga++
                        if (e.shotType == ShotType.THREE_POINTER) tpa++
                    }
                    e.secondaryPlayerId?.let { line(it).blk++ }
                }
                EventType.FREE_THROW_MADE -> line(e.playerId).apply { ftm++; fta++; points += e.points }
                EventType.FREE_THROW_MISSED -> line(e.playerId).fta++
                EventType.OFFENSIVE_REBOUND -> line(e.playerId).oreb++
                EventType.DEFENSIVE_REBOUND -> line(e.playerId).dreb++
                EventType.TURNOVER -> {
                    line(e.playerId).tov++
                    e.secondaryPlayerId?.let { line(it).stl++ }
                }
                EventType.FOUL -> line(e.playerId).pf++
                EventType.JUMP_BALL, EventType.FOUL_OUT, EventType.TIMEOUT,
                EventType.PERIOD_END, EventType.GAME_END -> Unit
            }
        }

        fun teamBox(team: Team): TeamBoxScore {
            val starterIds = starters[team.id].orEmpty()
            val ordered = starterIds.map(team::player) + team.roster.filter { it.id !in starterIds }
            val players = ordered.map { p ->
                val l = lines.getValue(p.id)
                PlayerGameStats(
                    playerId = p.id, playerName = p.fullName, position = p.position, teamId = team.id,
                    starter = p.id in starterIds, secondsPlayed = l.seconds, points = l.points,
                    fieldGoalsMade = l.fgm, fieldGoalsAttempted = l.fga,
                    threePointersMade = l.tpm, threePointersAttempted = l.tpa,
                    freeThrowsMade = l.ftm, freeThrowsAttempted = l.fta,
                    offensiveRebounds = l.oreb, defensiveRebounds = l.dreb,
                    assists = l.ast, steals = l.stl, blocks = l.blk, turnovers = l.tov, personalFouls = l.pf,
                )
            }
            return TeamBoxScore(team.id, team.name, players, TeamGameStats.sumOf(team.id, players))
        }

        return BoxScore(home = teamBox(home), away = teamBox(away))
    }
}
