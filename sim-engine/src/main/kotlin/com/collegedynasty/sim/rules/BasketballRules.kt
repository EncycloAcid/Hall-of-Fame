package com.collegedynasty.sim.rules

/** How many free throws a non-shooting (common) foul awards. */
enum class BonusAward { NONE, ONE_AND_ONE, TWO_SHOTS }

/**
 * Rulebook parameters. Everything that differs between college and professional basketball lives
 * here so the same engine can run either; see the [NCAA_MEN], [NCAA_WOMEN] and [NBA] presets.
 */
data class BasketballRules(
    val name: String,
    val regulationPeriods: Int,
    val periodLengthSeconds: Int,
    val overtimeLengthSeconds: Int,
    val shotClockSeconds: Int,
    /** Shot clock after an offensive rebound or a defensive common foul (whichever is greater is used). */
    val shotClockResetSeconds: Int,
    val personalFoulLimit: Int,
    /** Team-foul count (including the current foul) that starts one-and-one; null if the rule set has none. */
    val oneAndOneThreshold: Int?,
    /** Team-foul count (including the current foul) that starts two-shot bonus. */
    val twoShotBonusThreshold: Int,
    /** True if team fouls in overtime continue from the final regulation period (NCAA). */
    val overtimeFoulsCarryOver: Boolean,
    /** Two-shot threshold inside each overtime when fouls do not carry over (NBA). */
    val overtimeTwoShotBonusThreshold: Int,
    /** Game-clock interval (within a period) at which an automatic stoppage occurs, or null for none. */
    val officialTimeoutIntervalSeconds: Int?,
    /** Safety valve guaranteeing termination; a game still tied after this many overtimes ends tied. */
    val maxOvertimes: Int = 20,
    /** Labels used in play-by-play output. */
    val periodLabel: String,
) {
    init {
        require(regulationPeriods >= 1)
        require(periodLengthSeconds > 0 && overtimeLengthSeconds > 0)
        require(shotClockSeconds > 0 && shotClockResetSeconds in 1..shotClockSeconds)
        require(personalFoulLimit >= 1)
        require(twoShotBonusThreshold >= 1 && overtimeTwoShotBonusThreshold >= 1)
        require(oneAndOneThreshold == null || oneAndOneThreshold < twoShotBonusThreshold)
        require(maxOvertimes >= 0)
    }

    val regulationSeconds: Int get() = regulationPeriods * periodLengthSeconds

    fun isOvertime(period: Int): Boolean = period > regulationPeriods

    fun periodLength(period: Int): Int = if (isOvertime(period)) overtimeLengthSeconds else periodLengthSeconds

    /** Total game-clock seconds for a game that lasted [periods] periods. */
    fun gameLengthSeconds(periods: Int): Int = (1..periods).sumOf { periodLength(it) }

    /** True if team fouls reset when [period] begins. */
    fun resetsTeamFouls(period: Int): Boolean = !(isOvertime(period) && overtimeFoulsCarryOver)

    /** Free throws awarded for a defensive common foul given the fouling team's count including that foul. */
    fun bonusAward(teamFoulsIncludingThis: Int, period: Int): BonusAward {
        val twoShot = if (isOvertime(period) && !overtimeFoulsCarryOver) overtimeTwoShotBonusThreshold else twoShotBonusThreshold
        return when {
            teamFoulsIncludingThis >= twoShot -> BonusAward.TWO_SHOTS
            oneAndOneThreshold != null && !(isOvertime(period) && !overtimeFoulsCarryOver) &&
                teamFoulsIncludingThis >= oneAndOneThreshold -> BonusAward.ONE_AND_ONE
            else -> BonusAward.NONE
        }
    }

    fun periodName(period: Int): String = when {
        isOvertime(period) -> {
            val n = period - regulationPeriods
            if (n == 1) "OT" else "${n}OT"
        }
        else -> "${ordinal(period)} $periodLabel"
    }

    companion object {
        /** NCAA Division I men: two 20-minute halves, 30s shot clock, one-and-one at 7, double bonus at 10. */
        val NCAA_MEN = BasketballRules(
            name = "NCAA Men",
            regulationPeriods = 2,
            periodLengthSeconds = 20 * 60,
            overtimeLengthSeconds = 5 * 60,
            shotClockSeconds = 30,
            shotClockResetSeconds = 20,
            personalFoulLimit = 5,
            oneAndOneThreshold = 7,
            twoShotBonusThreshold = 10,
            overtimeFoulsCarryOver = true,
            overtimeTwoShotBonusThreshold = 10,
            officialTimeoutIntervalSeconds = 4 * 60,
            periodLabel = "Half",
        )

        /** NCAA women: four 10-minute quarters, two shots from the 5th team foul per quarter. */
        val NCAA_WOMEN = BasketballRules(
            name = "NCAA Women",
            regulationPeriods = 4,
            periodLengthSeconds = 10 * 60,
            overtimeLengthSeconds = 5 * 60,
            shotClockSeconds = 30,
            shotClockResetSeconds = 20,
            personalFoulLimit = 5,
            oneAndOneThreshold = null,
            twoShotBonusThreshold = 5,
            overtimeFoulsCarryOver = true,
            overtimeTwoShotBonusThreshold = 5,
            officialTimeoutIntervalSeconds = 5 * 60,
            periodLabel = "Quarter",
        )

        /** NBA: four 12-minute quarters, 24s shot clock, six fouls, penalty from the 5th team foul (4th in OT). */
        val NBA = BasketballRules(
            name = "NBA",
            regulationPeriods = 4,
            periodLengthSeconds = 12 * 60,
            overtimeLengthSeconds = 5 * 60,
            shotClockSeconds = 24,
            shotClockResetSeconds = 14,
            personalFoulLimit = 6,
            oneAndOneThreshold = null,
            twoShotBonusThreshold = 5,
            overtimeFoulsCarryOver = false,
            overtimeTwoShotBonusThreshold = 4,
            officialTimeoutIntervalSeconds = 6 * 60,
            periodLabel = "Quarter",
        )

        private fun ordinal(n: Int) = when {
            n % 100 in 11..13 -> "${n}th"
            n % 10 == 1 -> "${n}st"
            n % 10 == 2 -> "${n}nd"
            n % 10 == 3 -> "${n}rd"
            else -> "${n}th"
        }
    }
}
