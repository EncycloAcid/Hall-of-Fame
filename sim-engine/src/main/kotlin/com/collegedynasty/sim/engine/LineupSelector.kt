package com.collegedynasty.sim.engine

import com.collegedynasty.sim.model.Lineup

/**
 * Greedy best-five selection with light positional balance: 1..3 guards and 1..3 bigs whenever the
 * candidate pool allows it. Falls back to pure score order when the roster cannot satisfy the limits.
 */
internal object LineupSelector {
    private const val MIN_GUARDS = 1
    private const val MAX_GUARDS = 3
    private const val MIN_BIGS = 1
    private const val MAX_BIGS = 3

    fun select(candidates: List<PlayerState>, score: (PlayerState) -> Double): List<PlayerState> {
        val sorted = candidates.sortedByDescending(score)
        val chosen = mutableListOf<PlayerState>()
        for (candidate in sorted) {
            if (chosen.size == Lineup.SIZE) break
            val trial = chosen + candidate
            if (feasible(trial, sorted.filter { it !in trial })) chosen += candidate
        }
        for (candidate in sorted) {
            if (chosen.size == Lineup.SIZE) break
            if (candidate !in chosen) chosen += candidate
        }
        return chosen
    }

    private fun feasible(trial: List<PlayerState>, remaining: List<PlayerState>): Boolean {
        val guards = trial.count { it.player.position.isGuard }
        val bigs = trial.count { it.player.position.isBig }
        if (guards > MAX_GUARDS || bigs > MAX_BIGS) return false
        val slotsLeft = Lineup.SIZE - trial.size
        val needGuards = (MIN_GUARDS - guards).coerceAtLeast(0)
        val needBigs = (MIN_BIGS - bigs).coerceAtLeast(0)
        if (needGuards + needBigs > slotsLeft) return false
        if (needGuards > 0 && remaining.count { it.player.position.isGuard } < needGuards) return false
        if (needBigs > 0 && remaining.count { it.player.position.isBig } < needBigs) return false
        return true
    }
}
