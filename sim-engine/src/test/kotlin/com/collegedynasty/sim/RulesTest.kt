package com.collegedynasty.sim

import com.collegedynasty.sim.rules.BasketballRules
import com.collegedynasty.sim.rules.BonusAward
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RulesTest {
    @Test
    fun `ncaa men bonus thresholds`() {
        val r = BasketballRules.NCAA_MEN
        assertEquals(BonusAward.NONE, r.bonusAward(6, 1))
        assertEquals(BonusAward.ONE_AND_ONE, r.bonusAward(7, 1))
        assertEquals(BonusAward.ONE_AND_ONE, r.bonusAward(9, 2))
        assertEquals(BonusAward.TWO_SHOTS, r.bonusAward(10, 2))
        assertEquals(BonusAward.TWO_SHOTS, r.bonusAward(10, 3))
        assertTrue(r.resetsTeamFouls(2))
        assertFalse(r.resetsTeamFouls(3), "Overtime fouls carry over from the second half")
    }

    @Test
    fun `nba penalty thresholds`() {
        val r = BasketballRules.NBA
        assertEquals(BonusAward.NONE, r.bonusAward(4, 2))
        assertEquals(BonusAward.TWO_SHOTS, r.bonusAward(5, 2))
        assertEquals(BonusAward.NONE, r.bonusAward(3, 5))
        assertEquals(BonusAward.TWO_SHOTS, r.bonusAward(4, 5))
        assertTrue(r.resetsTeamFouls(5))
    }

    @Test
    fun `period lengths and names`() {
        val r = BasketballRules.NCAA_MEN
        assertEquals(2400, r.regulationSeconds)
        assertEquals(2700, r.gameLengthSeconds(3))
        assertEquals("1st Half", r.periodName(1))
        assertEquals("OT", r.periodName(3))
        assertEquals("2OT", r.periodName(4))
        assertEquals("4th Quarter", BasketballRules.NBA.periodName(4))
    }
}
