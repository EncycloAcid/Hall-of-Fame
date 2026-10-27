package com.collegedynasty.sim

import com.collegedynasty.sim.batch.BatchSimulator
import com.collegedynasty.sim.sample.SampleData
import kotlin.test.Test
import kotlin.test.assertTrue

class BatchSimulationTest {
    @Test
    fun `one thousand games are valid and statistically plausible`() {
        val pool = SampleData.leaguePool()
        val s = BatchSimulator().run(1_000) { i -> SampleData.batchGame(pool, i, baseSeed = 1) }
        println(s.format())

        assertTrue(s.validationFailures.isEmpty(), s.validationFailures.take(10).joinToString("\n"))
        assertTrue(s.tiedGames == 0)
        // Broad college-basketball ranges (NCAA D1 men averages sit near the middle of each band).
        assertTrue(s.pointsPerTeam in 62.0..80.0, "points ${s.pointsPerTeam}")
        assertTrue(s.possessionsPerTeam in 62.0..76.0, "possessions ${s.possessionsPerTeam}")
        assertTrue(s.fieldGoalPct in 0.40..0.48, "FG% ${s.fieldGoalPct}")
        assertTrue(s.threePointPct in 0.30..0.38, "3P% ${s.threePointPct}")
        assertTrue(s.freeThrowPct in 0.64..0.77, "FT% ${s.freeThrowPct}")
        assertTrue(s.turnoversPerTeam in 10.0..17.0, "TO ${s.turnoversPerTeam}")
        assertTrue(s.offensiveReboundPct in 0.22..0.35, "OREB% ${s.offensiveReboundPct}")
        assertTrue(s.overtimeRate in 0.005..0.10, "OT rate ${s.overtimeRate}")
        // Home court and talent matter, but upsets still happen.
        assertTrue(s.homeWinPct in 0.50..0.70, "home win ${s.homeWinPct}")
        assertTrue(s.favoriteWinPct in 0.60..0.92, "favorite win ${s.favoriteWinPct}")
        assertTrue(s.maxPlayerMinutes <= 55.0)
    }
}
