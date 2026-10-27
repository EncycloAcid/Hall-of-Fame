package com.collegedynasty.sim.model

import kotlin.math.roundToInt

/** One player's box score line. Minutes are stored as exact seconds of game clock played. */
data class PlayerGameStats(
    val playerId: String,
    val playerName: String,
    val position: Position,
    val teamId: String,
    val starter: Boolean,
    val secondsPlayed: Int,
    val points: Int,
    val fieldGoalsMade: Int,
    val fieldGoalsAttempted: Int,
    val threePointersMade: Int,
    val threePointersAttempted: Int,
    val freeThrowsMade: Int,
    val freeThrowsAttempted: Int,
    val offensiveRebounds: Int,
    val defensiveRebounds: Int,
    val assists: Int,
    val steals: Int,
    val blocks: Int,
    val turnovers: Int,
    val personalFouls: Int,
) {
    val rebounds: Int get() = offensiveRebounds + defensiveRebounds
    val minutes: Int get() = (secondsPlayed / 60.0).roundToInt()
    val minutesDisplay: String get() = "%d:%02d".format(secondsPlayed / 60, secondsPlayed % 60)
}

/** Team totals; always equal to the sum of that team's [PlayerGameStats]. */
data class TeamGameStats(
    val teamId: String,
    val secondsPlayed: Int,
    val points: Int,
    val fieldGoalsMade: Int,
    val fieldGoalsAttempted: Int,
    val threePointersMade: Int,
    val threePointersAttempted: Int,
    val freeThrowsMade: Int,
    val freeThrowsAttempted: Int,
    val offensiveRebounds: Int,
    val defensiveRebounds: Int,
    val assists: Int,
    val steals: Int,
    val blocks: Int,
    val turnovers: Int,
    val personalFouls: Int,
) {
    val rebounds: Int get() = offensiveRebounds + defensiveRebounds
    val fieldGoalPct: Double get() = pct(fieldGoalsMade, fieldGoalsAttempted)
    val threePointPct: Double get() = pct(threePointersMade, threePointersAttempted)
    val freeThrowPct: Double get() = pct(freeThrowsMade, freeThrowsAttempted)

    /** Standard possession estimate: FGA - OREB + TOV + 0.475 * FTA (college coefficient). */
    val estimatedPossessions: Double
        get() = fieldGoalsAttempted - offensiveRebounds + turnovers + 0.475 * freeThrowsAttempted

    companion object {
        fun sumOf(teamId: String, players: List<PlayerGameStats>) = TeamGameStats(
            teamId = teamId,
            secondsPlayed = players.sumOf { it.secondsPlayed },
            points = players.sumOf { it.points },
            fieldGoalsMade = players.sumOf { it.fieldGoalsMade },
            fieldGoalsAttempted = players.sumOf { it.fieldGoalsAttempted },
            threePointersMade = players.sumOf { it.threePointersMade },
            threePointersAttempted = players.sumOf { it.threePointersAttempted },
            freeThrowsMade = players.sumOf { it.freeThrowsMade },
            freeThrowsAttempted = players.sumOf { it.freeThrowsAttempted },
            offensiveRebounds = players.sumOf { it.offensiveRebounds },
            defensiveRebounds = players.sumOf { it.defensiveRebounds },
            assists = players.sumOf { it.assists },
            steals = players.sumOf { it.steals },
            blocks = players.sumOf { it.blocks },
            turnovers = players.sumOf { it.turnovers },
            personalFouls = players.sumOf { it.personalFouls },
        )

        private fun pct(made: Int, attempted: Int) = if (attempted == 0) 0.0 else made.toDouble() / attempted
    }
}

data class TeamBoxScore(
    val teamId: String,
    val teamName: String,
    val players: List<PlayerGameStats>,
    val totals: TeamGameStats,
)

data class BoxScore(val home: TeamBoxScore, val away: TeamBoxScore)
