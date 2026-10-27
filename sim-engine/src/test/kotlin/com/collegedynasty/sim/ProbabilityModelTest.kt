package com.collegedynasty.sim

import com.collegedynasty.sim.engine.ProbabilityModel
import com.collegedynasty.sim.model.ShotType
import com.collegedynasty.sim.rules.LeagueTuning
import kotlin.test.Test
import kotlin.test.assertTrue

class ProbabilityModelTest {
    private val t = LeagueTuning.COLLEGE
    private val extremes = listOf(1.0, 30.0, 60.0, 99.0)

    private fun assertBounded(p: Double, lo: Double = 0.0, hi: Double = 1.0) = assertTrue(p in lo..hi, "p=$p outside $lo..$hi")

    @Test
    fun `all probabilities stay inside realistic bands for extreme ratings`() {
        for (a in extremes) for (b in extremes) {
            assertBounded(ProbabilityModel.turnover(t, a, b, true), 0.07, 0.30)
            assertBounded(ProbabilityModel.nonShootingFoul(t, a, b))
            assertBounded(ProbabilityModel.offensiveRebound(t, a, b, false), 0.14, 0.45)
            for (type in ShotType.entries) {
                assertBounded(ProbabilityModel.make(t, type, a, b, false), 0.15, 0.85)
                assertBounded(ProbabilityModel.block(t, type, a, b), 0.0, 0.20)
                assertBounded(ProbabilityModel.shootingFoul(t, type, a, b), 0.0, 0.40)
                assertBounded(ProbabilityModel.assist(t, type, a), 0.20, 0.95)
            }
            assertBounded(ProbabilityModel.freeThrow(t, a), 0.35, 0.95)
            assertTrue(ProbabilityModel.shotTypeWeights(t, a, b, a, 50).values.sum() in 0.999..1.001)
        }
    }

    @Test
    fun `better shooters make more shots but never all of them`() {
        for (type in ShotType.entries) {
            val weak = ProbabilityModel.make(t, type, 40.0, 60.0, null)
            val strong = ProbabilityModel.make(t, type, 90.0, 60.0, null)
            assertTrue(strong > weak)
            assertTrue(strong < 0.9)
        }
    }

    @Test
    fun `home court nudges but does not decide`() {
        val home = ProbabilityModel.make(t, ShotType.INSIDE, 60.0, 60.0, true)
        val away = ProbabilityModel.make(t, ShotType.INSIDE, 60.0, 60.0, false)
        assertTrue(home > away && home - away < 0.05)
    }
}
