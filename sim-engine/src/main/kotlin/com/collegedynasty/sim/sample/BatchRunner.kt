package com.collegedynasty.sim.sample

import com.collegedynasty.sim.batch.BatchSimulator
import com.collegedynasty.sim.rules.BasketballRules
import com.collegedynasty.sim.rules.LeagueTuning

/** Runs N games between pool teams and prints aggregate statistics. Args: [games] [baseSeed] [ncaa-men|ncaa-women|nba]. */
fun main(args: Array<String>) {
    val games = args.getOrNull(0)?.toIntOrNull() ?: 1000
    val baseSeed = args.getOrNull(1)?.toLongOrNull() ?: 1L
    val (rules, tuning) = when (args.getOrNull(2) ?: "ncaa-men") {
        "ncaa-men" -> BasketballRules.NCAA_MEN to LeagueTuning.COLLEGE
        "ncaa-women" -> BasketballRules.NCAA_WOMEN to LeagueTuning.COLLEGE
        "nba" -> BasketballRules.NBA to LeagueTuning.PROFESSIONAL
        else -> error("Unknown rule set '${args[2]}' (expected ncaa-men, ncaa-women or nba)")
    }
    val pool = SampleData.leaguePool()
    val start = System.nanoTime()
    val summary = BatchSimulator().run(games) { i -> SampleData.batchGame(pool, i, baseSeed, rules, tuning) }
    val ms = (System.nanoTime() - start) / 1_000_000
    println("Rules: ${rules.name}")
    println(summary.format())
    println("  Wall time              ${ms}ms")
    summary.validationFailures.take(20).forEach { println("  ! $it") }
}
