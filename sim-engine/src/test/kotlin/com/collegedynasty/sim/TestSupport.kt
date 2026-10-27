package com.collegedynasty.sim

import com.collegedynasty.sim.engine.GameSimulator
import com.collegedynasty.sim.model.GameResult
import com.collegedynasty.sim.output.GameResultValidator
import com.collegedynasty.sim.random.RandomSource
import com.collegedynasty.sim.sample.SampleData
import kotlin.test.assertTrue

val simulator = GameSimulator()

fun sampleResult(seed: Long): GameResult = simulator.simulate(SampleData.sampleGame(seed))

fun assertValid(result: GameResult) {
    val problems = GameResultValidator.validate(result)
    assertTrue(problems.isEmpty(), "Seed ${result.game.seed}: $problems")
}

/** Degenerate random source that always returns the same value; used to stress termination. */
class ConstantRandom(private val value: Double) : RandomSource {
    override fun nextDouble(): Double = value
    override fun nextInt(from: Int, until: Int): Int = (from + ((until - from) * value).toInt()).coerceIn(from, until - 1)
}

/** Wraps another source and counts how often the engine draws from it. */
class CountingRandom(private val delegate: RandomSource) : RandomSource {
    var draws = 0
        private set
    override fun nextDouble(): Double = delegate.nextDouble().also { draws++ }
    override fun nextInt(from: Int, until: Int): Int = delegate.nextInt(from, until).also { draws++ }
}
