package com.collegedynasty.sim.batch

import com.collegedynasty.sim.engine.GameSimulator
import com.collegedynasty.sim.model.Game
import com.collegedynasty.sim.model.GameResult
import com.collegedynasty.sim.model.Team
import com.collegedynasty.sim.output.GameResultValidator

/** Aggregate statistics over many simulated games (per-team averages unless noted). */
data class BatchSummary(
    val games: Int,
    val pointsPerTeam: Double,
    val possessionsPerTeam: Double,
    val fieldGoalPct: Double,
    val threePointPct: Double,
    val threePointAttemptRate: Double,
    val freeThrowPct: Double,
    val freeThrowRate: Double,
    val turnoversPerTeam: Double,
    val offensiveReboundPct: Double,
    val assistsPerTeam: Double,
    val stealsPerTeam: Double,
    val blocksPerTeam: Double,
    val foulsPerTeam: Double,
    val overtimeRate: Double,
    val homeWinPct: Double,
    /** Share of games won by the team with the higher top-8 average overall rating. */
    val favoriteWinPct: Double,
    val averageMargin: Double,
    val minTeamScore: Int,
    val maxTeamScore: Int,
    val maxPlayerMinutes: Double,
    val averageTopStarterMinutes: Double,
    val tiedGames: Int,
    val validationFailures: List<String>,
) {
    fun format(): String = buildString {
        appendLine("Batch simulation: $games games")
        appendLine("  Points per team        %6.1f   (range %d-%d)".format(pointsPerTeam, minTeamScore, maxTeamScore))
        appendLine("  Possessions per team   %6.1f".format(possessionsPerTeam))
        appendLine("  FG%%                    %6.1f".format(fieldGoalPct * 100))
        appendLine("  3P%%                    %6.1f".format(threePointPct * 100))
        appendLine("  3PA / FGA              %6.1f%%".format(threePointAttemptRate * 100))
        appendLine("  FT%%                    %6.1f".format(freeThrowPct * 100))
        appendLine("  FTA / FGA              %6.3f".format(freeThrowRate))
        appendLine("  Turnovers per team     %6.1f".format(turnoversPerTeam))
        appendLine("  Offensive rebound %%    %6.1f".format(offensiveReboundPct * 100))
        appendLine("  Assists per team       %6.1f".format(assistsPerTeam))
        appendLine("  Steals per team        %6.1f".format(stealsPerTeam))
        appendLine("  Blocks per team        %6.1f".format(blocksPerTeam))
        appendLine("  Fouls per team         %6.1f".format(foulsPerTeam))
        appendLine("  Overtime games         %6.1f%%".format(overtimeRate * 100))
        appendLine("  Home win %%             %6.1f".format(homeWinPct * 100))
        appendLine("  Favorite win %%         %6.1f".format(favoriteWinPct * 100))
        appendLine("  Average margin         %6.1f".format(averageMargin))
        appendLine("  Top starter minutes    %6.1f   (max single game %.1f)".format(averageTopStarterMinutes, maxPlayerMinutes))
        appendLine("  Tied games             %6d".format(tiedGames))
        append("  Validation failures    %6d".format(validationFailures.size))
    }
}

/** Runs many games through [GameSimulator], validates each one and aggregates league-style averages. */
class BatchSimulator(private val simulator: GameSimulator = GameSimulator()) {

    /** [gameFactory] builds the game for each index (0 until [games]); give each game its own seed. */
    fun run(games: Int, gameFactory: (index: Int) -> Game): BatchSummary {
        require(games > 0)
        val results = ArrayList<GameResult>(games)
        val failures = mutableListOf<String>()
        repeat(games) { i ->
            val result = simulator.simulate(gameFactory(i))
            GameResultValidator.validate(result).forEach { failures += "game $i (seed ${result.game.seed}): $it" }
            results += result
        }
        return summarize(results, failures)
    }

    private fun summarize(results: List<GameResult>, failures: List<String>): BatchSummary {
        val teams = results.flatMap { listOf(it.boxScore.home.totals, it.boxScore.away.totals) }
        val n = teams.size.toDouble()
        val fgm = teams.sumOf { it.fieldGoalsMade }.toDouble()
        val fga = teams.sumOf { it.fieldGoalsAttempted }.toDouble()
        val oreb = results.sumOf { it.boxScore.home.totals.offensiveRebounds + it.boxScore.away.totals.offensiveRebounds }.toDouble()
        val orebChances = results.sumOf {
            val h = it.boxScore.home.totals
            val a = it.boxScore.away.totals
            h.offensiveRebounds + a.defensiveRebounds + a.offensiveRebounds + h.defensiveRebounds
        }.toDouble()
        val decided = results.filter { !it.isTie }
        val starterMinutes = results.flatMap { r ->
            listOf(r.boxScore.home, r.boxScore.away).map { box -> box.players.maxOf { it.secondsPlayed } / 60.0 }
        }
        return BatchSummary(
            games = results.size,
            pointsPerTeam = teams.sumOf { it.points } / n,
            possessionsPerTeam = teams.sumOf { it.estimatedPossessions } / n,
            fieldGoalPct = fgm / fga,
            threePointPct = teams.sumOf { it.threePointersMade } / teams.sumOf { it.threePointersAttempted }.toDouble(),
            threePointAttemptRate = teams.sumOf { it.threePointersAttempted } / fga,
            freeThrowPct = teams.sumOf { it.freeThrowsMade } / teams.sumOf { it.freeThrowsAttempted }.toDouble(),
            freeThrowRate = teams.sumOf { it.freeThrowsAttempted } / fga,
            turnoversPerTeam = teams.sumOf { it.turnovers } / n,
            offensiveReboundPct = oreb / orebChances,
            assistsPerTeam = teams.sumOf { it.assists } / n,
            stealsPerTeam = teams.sumOf { it.steals } / n,
            blocksPerTeam = teams.sumOf { it.blocks } / n,
            foulsPerTeam = teams.sumOf { it.personalFouls } / n,
            overtimeRate = results.count { it.overtimes > 0 } / results.size.toDouble(),
            homeWinPct = decided.count { it.homeScore > it.awayScore } / decided.size.toDouble(),
            favoriteWinPct = decided.count { it.winner?.id == favorite(it.game)?.id } /
                decided.count { favorite(it.game) != null }.coerceAtLeast(1).toDouble(),
            averageMargin = decided.sumOf { kotlin.math.abs(it.homeScore - it.awayScore) } / decided.size.toDouble(),
            minTeamScore = teams.minOf { it.points },
            maxTeamScore = teams.maxOf { it.points },
            maxPlayerMinutes = starterMinutes.max(),
            averageTopStarterMinutes = starterMinutes.average(),
            tiedGames = results.count { it.isTie },
            validationFailures = failures,
        )
    }

    companion object {
        fun topEightRating(team: Team): Double =
            team.roster.map { it.overall }.sortedDescending().take(8).average()

        fun favorite(game: Game): Team? {
            val h = topEightRating(game.home)
            val a = topEightRating(game.away)
            return when {
                h > a -> game.home
                a > h -> game.away
                else -> null
            }
        }
    }
}
