package com.collegedynasty.sim

import com.collegedynasty.sim.output.LiveGameFeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveGameFeedTest {
    @Test
    fun `revealing event by event reaches the final result`() {
        val result = sampleResult(2026)
        val feed = LiveGameFeed(result)
        assertEquals(0, feed.homeScore)
        var count = 0
        while (feed.next() != null) count++
        assertEquals(result.events.size, count)
        assertTrue(feed.isFinished)
        assertNull(feed.next())
        assertEquals(result.homeScore, feed.homeScore)
        assertEquals(result.boxScore, feed.boxScoreSoFar())
    }

    @Test
    fun `advancing by game time shows a consistent partial box score`() {
        val result = sampleResult(2026)
        val feed = LiveGameFeed(result)
        val revealed = feed.advanceTo(result.game.rules.periodLengthSeconds)
        assertTrue(revealed.isNotEmpty())
        assertTrue(revealed.all { it.elapsedSeconds <= result.game.rules.periodLengthSeconds })
        val partial = feed.boxScoreSoFar()
        assertEquals(feed.homeScore, partial.home.totals.points)
        assertEquals(feed.awayScore, partial.away.totals.points)
        assertTrue(feed.homeScore <= result.homeScore && !feed.isFinished)
    }
}
