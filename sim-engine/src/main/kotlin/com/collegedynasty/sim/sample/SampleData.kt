package com.collegedynasty.sim.sample

import com.collegedynasty.sim.model.Game
import com.collegedynasty.sim.model.Team
import com.collegedynasty.sim.model.TeamTactics
import com.collegedynasty.sim.random.SeededRandom
import com.collegedynasty.sim.rules.BasketballRules
import com.collegedynasty.sim.rules.LeagueTuning

/** Fixed fictional teams and matchups used by the sample runner, tests and batch harness. */
object SampleData {
    fun riverton(): Team = RosterGenerator(seed = 11).team(
        "RIV", "Riverton State Ravens", "RIV", strength = 66, tactics = TeamTactics(pace = 62, threePointEmphasis = 60),
    )

    fun lakeshore(): Team = RosterGenerator(seed = 22).team(
        "LAK", "Lakeshore Lions", "LAK", strength = 63, tactics = TeamTactics(pace = 45, threePointEmphasis = 45),
    )

    fun sampleGame(seed: Long, rules: BasketballRules = BasketballRules.NCAA_MEN, tuning: LeagueTuning = LeagueTuning.COLLEGE) =
        Game(id = "sample-$seed", home = riverton(), away = lakeshore(), seed = seed, rules = rules, tuning = tuning)

    /** A deterministic pool of teams spanning weak to elite programs. */
    fun leaguePool(size: Int = 24, seed: Long = 7): List<Team> {
        val strengths = SeededRandom(seed)
        return (1..size).map { i ->
            RosterGenerator(seed * 1_000 + i).team(
                id = "T$i",
                name = "Team $i",
                abbreviation = "T$i",
                strength = strengths.nextInt(52, 75),
                tactics = TeamTactics(pace = strengths.nextInt(30, 75), threePointEmphasis = strengths.nextInt(30, 75)),
            )
        }
    }

    /** Game [index] of a batch: two distinct pool teams chosen from a per-game seed. */
    fun batchGame(pool: List<Team>, index: Int, baseSeed: Long, rules: BasketballRules = BasketballRules.NCAA_MEN, tuning: LeagueTuning = LeagueTuning.COLLEGE): Game {
        val seed = baseSeed * 1_000_003 + index
        val pick = SeededRandom(seed xor 0x9E3779B97F4A7C15uL.toLong())
        val home = pool[pick.nextInt(0, pool.size)]
        var away = pool[pick.nextInt(0, pool.size)]
        while (away.id == home.id) away = pool[pick.nextInt(0, pool.size)]
        return Game(id = "batch-$index", home = home, away = away, seed = seed, rules = rules, tuning = tuning)
    }
}
