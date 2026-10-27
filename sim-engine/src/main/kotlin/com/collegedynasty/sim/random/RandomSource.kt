package com.collegedynasty.sim.random

import kotlin.random.Random

/**
 * Injectable source of randomness. All stochastic decisions in the engine go through this
 * interface so games are reproducible from a seed and tests can supply scripted values.
 */
interface RandomSource {
    /** Uniform double in [0, 1). */
    fun nextDouble(): Double

    /** Uniform int in [from, until). */
    fun nextInt(from: Int, until: Int): Int

    fun chance(probability: Double): Boolean = nextDouble() < probability
}

/** Deterministic [RandomSource] backed by Kotlin's seeded PRNG (stable across JVMs and platforms). */
class SeededRandom(val seed: Long) : RandomSource {
    private val random = Random(seed)

    override fun nextDouble(): Double = random.nextDouble()

    override fun nextInt(from: Int, until: Int): Int = random.nextInt(from, until)
}

/** Picks one item with probability proportional to its (non-negative) weight. */
fun <T> RandomSource.weightedPick(items: List<T>, weight: (T) -> Double): T {
    require(items.isNotEmpty()) { "Cannot pick from an empty list" }
    val weights = items.map { weight(it).coerceAtLeast(0.0) }
    val total = weights.sum()
    if (total <= 0.0) return items[nextInt(0, items.size)]
    var roll = nextDouble() * total
    for (i in items.indices) {
        roll -= weights[i]
        if (roll < 0) return items[i]
    }
    return items.last()
}
