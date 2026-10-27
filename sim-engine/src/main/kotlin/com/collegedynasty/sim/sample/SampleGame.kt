package com.collegedynasty.sim.sample

import com.collegedynasty.sim.engine.GameSimulator
import com.collegedynasty.sim.output.TextReport

/** Prints one complete sample game: line score, both box scores and the full play-by-play. */
fun main(args: Array<String>) {
    val seed = args.firstOrNull()?.toLongOrNull() ?: 2026L
    val result = GameSimulator().simulate(SampleData.sampleGame(seed))
    print(TextReport.full(result))
}
