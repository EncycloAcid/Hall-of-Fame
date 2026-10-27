package com.collegedynasty.sim

import com.collegedynasty.sim.model.EventType
import com.collegedynasty.sim.rules.BasketballRules
import com.collegedynasty.sim.rules.LeagueTuning
import com.collegedynasty.sim.sample.SampleData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminationTest {
    private val ruleSets = listOf(
        BasketballRules.NCAA_MEN to LeagueTuning.COLLEGE,
        BasketballRules.NCAA_WOMEN to LeagueTuning.COLLEGE,
        BasketballRules.NBA to LeagueTuning.PROFESSIONAL,
    )

    @Test
    fun `games terminate under every rule set with a full clock`() {
        for ((rules, tuning) in ruleSets) {
            for (seed in 1L..100L) {
                val r = simulator.simulate(SampleData.sampleGame(seed, rules, tuning))
                assertEquals(EventType.GAME_END, r.events.last().type)
                assertEquals(rules.gameLengthSeconds(r.periodsPlayed), r.events.last().elapsedSeconds)
                assertTrue(r.periodsPlayed >= rules.regulationPeriods)
                assertTrue(!r.isTie, "${rules.name} seed $seed ended tied")
                assertValid(r)
            }
        }
    }

    @Test
    fun `degenerate random sources still terminate`() {
        for (value in listOf(0.0, 0.25, 0.5, 0.75, 0.999_999)) {
            for ((rules, tuning) in ruleSets) {
                val r = simulator.simulate(SampleData.sampleGame(1, rules, tuning), ConstantRandom(value))
                assertEquals(EventType.GAME_END, r.events.last().type)
                assertTrue(r.periodsPlayed <= rules.regulationPeriods + rules.maxOvertimes)
                assertValid(r)
            }
        }
    }

    @Test
    fun `period count matches regulation plus overtimes`() {
        val r = sampleResult(7)
        val starts = r.events.count { it.type == EventType.PERIOD_START }
        val ends = r.events.count { it.type == EventType.PERIOD_END }
        assertEquals(r.periodsPlayed, starts)
        assertEquals(r.periodsPlayed, ends)
    }
}
