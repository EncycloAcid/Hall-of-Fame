package com.collegedynasty.sim

import com.collegedynasty.sim.model.EventType
import com.collegedynasty.sim.model.Lineup
import com.collegedynasty.sim.model.TeamGameStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoringAndStatTotalsTest {
    private val results = (1L..60L).map(::sampleResult)

    @Test
    fun `team points equal 2xFGM plus 3PM plus FTM for every player and team`() {
        for (r in results) {
            for (box in listOf(r.boxScore.home, r.boxScore.away)) {
                for (p in box.players) {
                    assertEquals(2 * p.fieldGoalsMade + p.threePointersMade + p.freeThrowsMade, p.points, p.playerId)
                }
                val t = box.totals
                assertEquals(2 * t.fieldGoalsMade + t.threePointersMade + t.freeThrowsMade, t.points)
            }
        }
    }

    @Test
    fun `final score reconciles with player points, event points and period scores`() {
        for (r in results) {
            assertEquals(r.homeScore, r.boxScore.home.players.sumOf { it.points })
            assertEquals(r.awayScore, r.boxScore.away.players.sumOf { it.points })
            assertEquals(r.homeScore, r.events.filter { it.teamId == r.game.home.id }.sumOf { it.points })
            assertEquals(r.awayScore, r.events.filter { it.teamId == r.game.away.id }.sumOf { it.points })
            assertEquals(r.homeScore, r.periodScores.sumOf { it.home })
            assertEquals(r.awayScore, r.periodScores.sumOf { it.away })
            val last = r.events.last()
            assertEquals(r.homeScore, last.homeScore)
            assertEquals(r.awayScore, last.awayScore)
        }
    }

    @Test
    fun `team totals equal the sum of player lines`() {
        for (r in results) {
            for (box in listOf(r.boxScore.home, r.boxScore.away)) {
                assertEquals(TeamGameStats.sumOf(box.teamId, box.players), box.totals)
                assertEquals(box.totals.offensiveRebounds + box.totals.defensiveRebounds, box.totals.rebounds)
            }
        }
    }

    @Test
    fun `minutes add up to five players for the whole game`() {
        for (r in results) {
            val gameSeconds = r.game.rules.gameLengthSeconds(r.periodsPlayed)
            assertEquals(Lineup.SIZE * gameSeconds, r.boxScore.home.totals.secondsPlayed)
            assertEquals(Lineup.SIZE * gameSeconds, r.boxScore.away.totals.secondsPlayed)
        }
    }

    @Test
    fun `counting stats match their source events`() {
        for (r in results) {
            val all = r.boxScore.home.players + r.boxScore.away.players
            assertEquals(r.events.count { it.type == EventType.SHOT_MADE && it.secondaryPlayerId != null }, all.sumOf { it.assists })
            assertEquals(r.events.count { it.type == EventType.SHOT_MISSED && it.secondaryPlayerId != null }, all.sumOf { it.blocks })
            assertEquals(r.events.count { it.type == EventType.TURNOVER }, all.sumOf { it.turnovers })
            assertEquals(r.events.count { it.type == EventType.FOUL }, all.sumOf { it.personalFouls })
            assertEquals(
                r.events.count { it.type == EventType.OFFENSIVE_REBOUND || it.type == EventType.DEFENSIVE_REBOUND },
                all.sumOf { it.rebounds },
            )
            assertTrue(all.all { it.personalFouls <= r.game.rules.personalFoulLimit })
        }
    }

    @Test
    fun `independent validator finds no problems`() {
        results.forEach(::assertValid)
    }
}
