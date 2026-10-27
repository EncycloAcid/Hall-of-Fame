package com.collegedynasty.sim.output

import com.collegedynasty.sim.model.BoxScore
import com.collegedynasty.sim.model.GameResult
import com.collegedynasty.sim.model.PlayByPlayEvent

/**
 * Reveals an already-simulated game step by step, for a future live text viewer. The game is fully
 * decided up front; this class only controls how much of it is visible.
 */
class LiveGameFeed(private val result: GameResult) {
    var revealedCount: Int = 0
        private set

    val isFinished: Boolean get() = revealedCount == result.events.size
    val revealed: List<PlayByPlayEvent> get() = result.events.subList(0, revealedCount)
    val homeScore: Int get() = revealed.lastOrNull()?.homeScore ?: 0
    val awayScore: Int get() = revealed.lastOrNull()?.awayScore ?: 0

    /** Reveals and returns the next event, or null when the game is over. */
    fun next(): PlayByPlayEvent? = if (isFinished) null else result.events[revealedCount++]

    /** Reveals every event up to and including [elapsedSeconds] of game time; returns the newly revealed events. */
    fun advanceTo(elapsedSeconds: Int): List<PlayByPlayEvent> {
        val start = revealedCount
        while (!isFinished && result.events[revealedCount].elapsedSeconds <= elapsedSeconds) revealedCount++
        return result.events.subList(start, revealedCount)
    }

    /** Box score reflecting only the events revealed so far. */
    fun boxScoreSoFar(): BoxScore = BoxScoreBuilder.build(result.game.home, result.game.away, revealed)
}
