package com.collegedynasty.sim.engine

import com.collegedynasty.sim.model.FoulType
import com.collegedynasty.sim.model.Player
import com.collegedynasty.sim.model.ShotType
import com.collegedynasty.sim.model.TurnoverType
import kotlin.random.Random

/**
 * Builds play-by-play text. Uses its own PRNG (derived from the game seed) so wording changes can
 * never alter simulated outcomes.
 */
internal class Narrator(seed: Long) {
    private val random = Random(seed xor 0x5DEECE66DL)

    private fun pick(options: List<String>) = options[random.nextInt(options.size)]

    fun shot(shooter: Player, type: ShotType, made: Boolean, putback: Boolean, assister: Player?, blocker: Player?): String {
        val name = shooter.shortName
        if (blocker != null) {
            val what = when (type) {
                ShotType.INSIDE -> "layup"
                ShotType.MIDRANGE -> "jumper"
                ShotType.THREE_POINTER -> "three-point attempt"
            }
            return "$name has his $what blocked by ${blocker.shortName}"
        }
        val text = if (made) {
            when (type) {
                ShotType.INSIDE -> when {
                    putback -> pick(listOf("scores on a putback", "tips it back in", "puts back the offensive board"))
                    shooter.attributes.athleticism >= 75 && random.nextDouble() < 0.35 ->
                        pick(listOf("throws down a dunk", "finishes with a two-handed slam", "rises for an alley-oop dunk"))
                    else -> pick(listOf("makes a driving layup", "finishes at the rim", "banks in a short hook", "scores in the paint"))
                }
                ShotType.MIDRANGE -> pick(
                    listOf("hits a pull-up jumper", "knocks down a mid-range jumper", "drains a turnaround jumper", "hits an elbow jumper"),
                )
                ShotType.THREE_POINTER -> pick(
                    listOf("drills a three-pointer", "hits a three from the wing", "buries a corner three", "knocks down a three from the top of the key"),
                )
            }
        } else {
            when (type) {
                ShotType.INSIDE -> if (putback) "misses the putback attempt" else pick(listOf("misses a layup", "misses a contested shot in the paint", "can't finish at the rim"))
                ShotType.MIDRANGE -> pick(listOf("misses a jumper", "misses a pull-up jumper", "clanks a mid-range jumper"))
                ShotType.THREE_POINTER -> pick(listOf("misses a three-pointer", "misses a three from the corner", "misses a deep three"))
            }
        }
        val assist = assister?.let { " (assist: ${it.shortName})" } ?: ""
        return "$name $text$assist"
    }

    fun freeThrow(shooter: Player, made: Boolean, number: Int, total: Int, oneAndOne: Boolean): String {
        val label = when {
            oneAndOne && number == 1 -> "the front end of the one-and-one"
            oneAndOne -> "the back end of the one-and-one"
            total == 1 -> "the and-one free throw"
            else -> "free throw $number of $total"
        }
        return "${shooter.shortName} ${if (made) "makes" else "misses"} $label"
    }

    fun rebound(rebounder: Player, offensive: Boolean): String =
        if (offensive) "${rebounder.shortName} grabs the offensive rebound" else "${rebounder.shortName} pulls down the defensive rebound"

    fun turnover(player: Player, type: TurnoverType, stealer: Player?): String = when (type) {
        TurnoverType.STEAL -> "${stealer!!.shortName} steals the ball from ${player.shortName}"
        TurnoverType.BAD_PASS -> "${player.shortName} throws the ball away"
        TurnoverType.TRAVEL -> "${player.shortName} is called for traveling"
        TurnoverType.OUT_OF_BOUNDS -> "${player.shortName} steps out of bounds"
        TurnoverType.OFFENSIVE_FOUL -> "Turnover by ${player.shortName} (offensive foul)"
        TurnoverType.SHOT_CLOCK -> "Shot clock violation"
    }

    fun foul(fouler: Player, fouled: Player?, type: FoulType, personalFouls: Int): String {
        val what = when (type) {
            FoulType.SHOOTING -> "shooting foul"
            FoulType.PERSONAL -> "personal foul"
            FoulType.OFFENSIVE -> "offensive foul"
            FoulType.INTENTIONAL -> "foul to stop the clock"
        }
        val on = fouled?.let { " on ${it.shortName}" } ?: ""
        return "${fouler.shortName} commits a $what$on ($personalFouls PF)"
    }

    fun foulOut(player: Player): String = "${player.shortName} has fouled out"

    fun substitution(teamName: String, playerIn: Player, playerOut: Player): String =
        "$teamName substitution: ${playerIn.shortName} in for ${playerOut.shortName}"

    fun jumpBall(winner: Player, teamName: String): String = "${winner.shortName} wins the jump ball for $teamName"
}
