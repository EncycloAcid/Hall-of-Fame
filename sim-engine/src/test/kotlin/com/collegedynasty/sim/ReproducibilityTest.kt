package com.collegedynasty.sim

import com.collegedynasty.sim.random.SeededRandom
import com.collegedynasty.sim.sample.SampleData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ReproducibilityTest {
    @Test
    fun `same seed produces an identical game`() {
        for (seed in listOf(1L, 42L, 2026L, Long.MAX_VALUE)) {
            assertEquals(sampleResult(seed), sampleResult(seed))
        }
    }

    @Test
    fun `different seeds produce different games`() {
        val distinct = (1L..20L).map { sampleResult(it).events }.toSet()
        assertTrue(distinct.size >= 19)
        assertNotEquals(sampleResult(1).events, sampleResult(2).events)
    }

    @Test
    fun `injected random source drives every outcome`() {
        val game = SampleData.sampleGame(seed = 99)
        val counting = CountingRandom(SeededRandom(99))
        val injected = simulator.simulate(game, counting)
        assertTrue(counting.draws > 500, "Engine should draw from the injected source")
        assertEquals(simulator.simulate(game), injected)
    }
}
