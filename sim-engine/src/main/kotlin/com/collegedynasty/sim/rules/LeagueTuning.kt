package com.collegedynasty.sim.rules

/**
 * Statistical calibration of the possession model (league-wide tendencies), kept separate from the
 * rulebook in [BasketballRules]. Base rates describe an average offense against an average defense
 * (all relevant attributes equal to [attributeReference]); player ratings move them up or down and
 * every resulting probability is clamped to a realistic band in [com.collegedynasty.sim.engine.ProbabilityModel].
 */
data class LeagueTuning(
    val attributeReference: Double = 60.0,
    // Possession length
    val minPossessionSeconds: Int = 5,
    /** Exponent applied to a [0,1) triangular sample; larger values mean faster possessions. */
    val paceExponent: Double = 0.95,
    // Turnovers
    val baseTurnoverRate: Double = 0.17,
    val stealShareOfTurnovers: Double = 0.52,
    val offensiveFoulShareOfTurnovers: Double = 0.10,
    val shotClockViolationShareOfTurnovers: Double = 0.03,
    // Fouls
    val nonShootingFoulRate: Double = 0.16,
    val shootingFoulRateInside: Double = 0.20,
    val shootingFoulRateMidrange: Double = 0.07,
    val shootingFoulRateThree: Double = 0.02,
    // Shot selection (league shot mix before player/tactic adjustments)
    val shotMixInside: Double = 0.42,
    val shotMixMidrange: Double = 0.22,
    val shotMixThree: Double = 0.36,
    // Make probability on unblocked attempts
    val makeRateInside: Double = 0.605,
    val makeRateMidrange: Double = 0.37,
    val makeRateThree: Double = 0.32,
    /** Make-rate multiplier when the shooter is fouled on the attempt. */
    val andOneMakeFactor: Double = 0.5,
    // Blocks
    val blockRateInside: Double = 0.095,
    val blockRateMidrange: Double = 0.035,
    val blockRateThree: Double = 0.008,
    // Rebounding
    val offensiveReboundRate: Double = 0.29,
    val freeThrowOffensiveReboundFactor: Double = 0.55,
    // Assists (probability a made field goal is assisted)
    val assistRateInside: Double = 0.36,
    val assistRateMidrange: Double = 0.40,
    val assistRateThree: Double = 0.70,
    // Free throws: p = base + scale * (rating / 100)
    val freeThrowBase: Double = 0.32,
    val freeThrowScale: Double = 0.58,
    // Home court
    val homeMakeBonus: Double = 0.03,
    val homeTurnoverReduction: Double = 0.012,
    // Fatigue (energy is 0..100)
    val energyDrainPerSecond: Double = 0.05,
    val energyRecoveryPerSecond: Double = 0.12,
    val energyRecoveryBetweenPeriods: Double = 25.0,
    // Late game
    val intentionalFoulWindowSeconds: Int = 35,
    val intentionalFoulMaxDeficit: Int = 8,
    val garbageTimeMargin: Int = 20,
    val garbageTimeSeconds: Int = 240,
) {
    companion object {
        val COLLEGE = LeagueTuning()

        /** Starting point for professional play: better shooters, more threes, slightly lower turnover rate. */
        val PROFESSIONAL = LeagueTuning(
            attributeReference = 70.0,
            minPossessionSeconds = 4,
            baseTurnoverRate = 0.135,
            shotMixInside = 0.42,
            shotMixMidrange = 0.15,
            shotMixThree = 0.43,
            makeRateInside = 0.69,
            makeRateMidrange = 0.42,
            makeRateThree = 0.365,
            offensiveReboundRate = 0.25,
            assistRateInside = 0.55,
            assistRateMidrange = 0.55,
            assistRateThree = 0.85,
            freeThrowBase = 0.36,
            freeThrowScale = 0.52,
            homeMakeBonus = 0.008,
            homeTurnoverReduction = 0.004,
            energyDrainPerSecond = 0.06,
        )
    }
}
