package com.collegedynasty.sim.output

import com.collegedynasty.sim.model.EventType
import com.collegedynasty.sim.model.GameResult
import com.collegedynasty.sim.model.PlayByPlayEvent
import com.collegedynasty.sim.model.TeamBoxScore
import com.collegedynasty.sim.model.TeamGameStats

/** Plain-text renderings of a [GameResult] (console / log output). */
object TextReport {

    fun full(result: GameResult): String = buildString {
        appendLine(header(result))
        appendLine()
        appendLine(boxScore(result))
        appendLine("PLAY-BY-PLAY")
        appendLine("=".repeat(100))
        append(playByPlay(result))
    }

    fun header(result: GameResult): String = buildString {
        val g = result.game
        appendLine("${g.away.name} (${g.away.abbreviation}) at ${g.home.name} (${g.home.abbreviation})")
        appendLine("Rules: ${g.rules.name} | Seed: ${g.seed}${if (g.neutralSite) " | Neutral site" else ""}")
        appendLine()
        val labels = result.periodScores.map { it.label.replace(" Half", "H").replace(" Quarter", "Q") }
        append("%-6s".format("TEAM"))
        labels.forEach { append("%6s".format(it)) }
        appendLine("%7s".format("FINAL"))
        append("%-6s".format(g.away.abbreviation))
        result.periodScores.forEach { append("%6d".format(it.away)) }
        appendLine("%7d".format(result.awayScore))
        append("%-6s".format(g.home.abbreviation))
        result.periodScores.forEach { append("%6d".format(it.home)) }
        append("%7d".format(result.homeScore))
    }

    fun boxScore(result: GameResult): String = buildString {
        appendLine(teamBox(result.boxScore.away))
        appendLine(teamBox(result.boxScore.home))
    }

    fun teamBox(box: TeamBoxScore): String = buildString {
        appendLine(box.teamName.uppercase())
        val fmt = "%-24s %-3s %5s %4s %6s %6s %6s %4s %4s %4s %4s %4s %4s %4s %3s"
        appendLine(fmt.format("PLAYER", "POS", "MIN", "PTS", "FG", "3PT", "FT", "OREB", "DREB", "REB", "AST", "STL", "BLK", "TO", "PF"))
        appendLine("-".repeat(108))
        for (p in box.players) {
            val name = (if (p.starter) "* " else "  ") + p.playerName
            appendLine(
                fmt.format(
                    name.take(24), p.position.abbreviation, p.minutesDisplay, p.points,
                    "${p.fieldGoalsMade}-${p.fieldGoalsAttempted}",
                    "${p.threePointersMade}-${p.threePointersAttempted}",
                    "${p.freeThrowsMade}-${p.freeThrowsAttempted}",
                    p.offensiveRebounds, p.defensiveRebounds, p.rebounds,
                    p.assists, p.steals, p.blocks, p.turnovers, p.personalFouls,
                ),
            )
        }
        val t = box.totals
        appendLine("-".repeat(108))
        appendLine(
            fmt.format(
                "TOTALS", "", t.secondsPlayed / 60, t.points,
                "${t.fieldGoalsMade}-${t.fieldGoalsAttempted}",
                "${t.threePointersMade}-${t.threePointersAttempted}",
                "${t.freeThrowsMade}-${t.freeThrowsAttempted}",
                t.offensiveRebounds, t.defensiveRebounds, t.rebounds,
                t.assists, t.steals, t.blocks, t.turnovers, t.personalFouls,
            ),
        )
        appendLine(percentages(t))
    }

    private fun percentages(t: TeamGameStats) =
        "FG %.1f%% | 3PT %.1f%% | FT %.1f%% | Est. possessions %.1f".format(
            t.fieldGoalPct * 100, t.threePointPct * 100, t.freeThrowPct * 100, t.estimatedPossessions,
        )

    fun playByPlay(result: GameResult): String = buildString {
        result.events.forEach { appendLine(eventLine(result, it)) }
    }

    fun eventLine(result: GameResult, e: PlayByPlayEvent): String {
        val g = result.game
        val clock = "%2d:%02d".format(e.clockSeconds / 60, e.clockSeconds % 60)
        val period = g.rules.periodName(e.period)
        val score = "${g.away.abbreviation} ${e.awayScore}-${e.homeScore} ${g.home.abbreviation}"
        val team = when (e.teamId) {
            g.home.id -> g.home.abbreviation
            g.away.id -> g.away.abbreviation
            else -> ""
        }
        val marker = if (e.type == EventType.SHOT_MADE || e.type == EventType.FREE_THROW_MADE) "+${e.points}" else ""
        return "%-9s %s | %-16s | %-4s %-3s %s".format(period, clock, score, team, marker, e.description)
    }
}
