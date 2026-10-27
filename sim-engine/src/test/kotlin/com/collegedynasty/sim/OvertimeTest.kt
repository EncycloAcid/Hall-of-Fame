package com.collegedynasty.sim

import com.collegedynasty.sim.model.EventType
import com.collegedynasty.sim.rules.BasketballRules
import com.collegedynasty.sim.sample.SampleData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OvertimeTest {
    private val overtimeSeed: Long = (1L..5_000L).first { sampleResult(it).overtimes > 0 }

    @Test
    fun `tied games go to overtime and end with a winner`() {
        val r = sampleResult(overtimeSeed)
        val rules = r.game.rules
        val regulationEnd = r.events.last { it.type == EventType.PERIOD_END && it.period == rules.regulationPeriods }
        assertEquals(regulationEnd.homeScore, regulationEnd.awayScore, "Regulation must end tied")
        assertTrue(r.periodsPlayed > rules.regulationPeriods)
        assertNotNull(r.winner)
        assertEquals(rules.gameLengthSeconds(r.periodsPlayed), r.events.last().elapsedSeconds)
        assertEquals("OT", r.periodScores[rules.regulationPeriods].label)
        // Every overtime before the last must itself end tied.
        r.events.filter { it.type == EventType.PERIOD_END && it.period > rules.regulationPeriods && it.period < r.periodsPlayed }
            .forEach { assertEquals(it.homeScore, it.awayScore) }
        assertValid(r)
    }

    @Test
    fun `overtime periods use the overtime length`() {
        val r = sampleResult(overtimeSeed)
        val otStart = r.events.first { it.type == EventType.PERIOD_START && it.period == r.game.rules.regulationPeriods + 1 }
        assertEquals(r.game.rules.overtimeLengthSeconds, otStart.clockSeconds)
        assertEquals(r.game.rules.regulationSeconds, otStart.elapsedSeconds)
    }

    @Test
    fun `overtime cap terminates a tied game`() {
        val capped = SampleData.sampleGame(overtimeSeed).copy(rules = BasketballRules.NCAA_MEN.copy(maxOvertimes = 0))
        val r = simulator.simulate(capped)
        assertEquals(capped.rules.regulationPeriods, r.periodsPlayed)
        assertTrue(r.isTie)
        assertValid(r)
    }
}
