package com.collegedynasty.sim.sample

import com.collegedynasty.sim.model.Player
import com.collegedynasty.sim.model.PlayerAttributes
import com.collegedynasty.sim.model.Position
import com.collegedynasty.sim.model.Team
import com.collegedynasty.sim.model.TeamTactics
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Deterministic fictional-roster generator for samples, tests and batch runs. Not a recruiting or
 * player-generation system; it only produces plausible attribute spreads by position.
 */
class RosterGenerator(seed: Long) {
    private val random = Random(seed)

    /** Builds a 13-man roster whose rotation players sit around [strength] (roughly 45 weak .. 80 elite). */
    fun team(id: String, name: String, abbreviation: String, strength: Int, tactics: TeamTactics = TeamTactics()): Team {
        val players = SLOTS.mapIndexed { i, (position, tierOffset) ->
            player("$id-${i + 1}", position, strength + tierOffset, jersey = JERSEYS[i])
        }
        return Team(id, name, abbreviation, players, tactics)
    }

    private fun player(id: String, position: Position, level: Int, jersey: Int): Player {
        val o = ARCHETYPES.getValue(position)
        fun attr(offset: Int) = (level + offset + noise()).roundToInt().coerceIn(25, 99)
        val attributes = PlayerAttributes(
            insideScoring = attr(o[0]),
            midrangeShooting = attr(o[1]),
            threePointShooting = attr(o[2]),
            freeThrows = attr(o[3]),
            playmaking = attr(o[4]),
            ballHandling = attr(o[5]),
            perimeterDefense = attr(o[6]),
            interiorDefense = attr(o[7]),
            rebounding = attr(o[8]),
            athleticism = attr(o[9]),
            basketballIq = attr(o[10]),
        )
        return Player(id, FIRST_NAMES.random(random), LAST_NAMES.random(random), position, jersey, attributes)
    }

    /** Approximately normal noise with standard deviation ~6. */
    private fun noise(): Double = (List(4) { random.nextDouble() }.sum() - 2.0) * 10.4

    private companion object {
        val SLOTS = listOf(
            Position.POINT_GUARD to 6, Position.SHOOTING_GUARD to 6, Position.SMALL_FORWARD to 6,
            Position.POWER_FORWARD to 6, Position.CENTER to 6,
            Position.POINT_GUARD to 0, Position.SHOOTING_GUARD to 0, Position.SMALL_FORWARD to 0,
            Position.POWER_FORWARD to -1, Position.CENTER to -2,
            Position.SHOOTING_GUARD to -7, Position.SMALL_FORWARD to -8, Position.CENTER to -9,
        )
        val JERSEYS = listOf(1, 3, 5, 10, 11, 12, 13, 15, 20, 21, 22, 24, 33)

        // Offsets: inside, mid, three, FT, playmaking, handling, perD, intD, reb, athleticism, IQ
        val ARCHETYPES = mapOf(
            Position.POINT_GUARD to intArrayOf(-5, 2, 4, 4, 14, 14, 4, -14, -14, 3, 4),
            Position.SHOOTING_GUARD to intArrayOf(0, 5, 9, 6, 3, 6, 5, -10, -9, 3, 0),
            Position.SMALL_FORWARD to intArrayOf(4, 3, 2, 0, -2, 0, 5, -2, 0, 5, 0),
            Position.POWER_FORWARD to intArrayOf(8, 0, -6, -5, -8, -8, -3, 8, 10, 2, 0),
            Position.CENTER to intArrayOf(12, -8, -18, -10, -10, -14, -8, 15, 15, 0, 0),
        )

        val FIRST_NAMES = listOf(
            "Marcus", "Jalen", "Tyler", "Devin", "Isaiah", "Caleb", "Andre", "Malik", "Jordan", "Trey",
            "Cameron", "Darius", "Elijah", "Noah", "Xavier", "Brandon", "Kendall", "Terrance", "Omar", "Luka",
            "Quentin", "Rashad", "Simon", "Wesley", "Zion", "Grant", "Hunter", "Jamal", "Keon", "Reggie",
        )
        val LAST_NAMES = listOf(
            "Johnson", "Williams", "Carter", "Brooks", "Mitchell", "Hayes", "Coleman", "Reed", "Foster", "Bennett",
            "Sanders", "Price", "Ward", "Barnes", "Griffin", "Henderson", "Wallace", "Fields", "Porter", "Hamilton",
            "Graham", "Simmons", "Patterson", "Jenkins", "Boyd", "Holloway", "Dixon", "Mercer", "Okafor", "Lindqvist",
        )
    }
}
