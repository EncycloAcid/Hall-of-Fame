package com.collegedynasty.sim.engine

import com.collegedynasty.sim.model.ShotType
import com.collegedynasty.sim.rules.LeagueTuning
import kotlin.math.exp
import kotlin.math.pow

/**
 * Pure, bounded probability functions for individual possession events.
 *
 * Every function starts from a league base rate in [LeagueTuning], shifts it by the difference
 * between the relevant (fatigue-adjusted) attribute composites and [LeagueTuning.attributeReference],
 * then clamps the result into a realistic band. Talent therefore tilts each event modestly; it never
 * decides a game outright, and there is no win-probability-by-rating formula anywhere in the engine.
 */
object ProbabilityModel {

    fun turnover(t: LeagueTuning, ballSecurity: Double, defensivePressure: Double, homeOffense: Boolean?): Double {
        val ref = t.attributeReference
        val home = when (homeOffense) {
            true -> -t.homeTurnoverReduction / 2
            false -> t.homeTurnoverReduction / 2
            null -> 0.0
        }
        val p = t.baseTurnoverRate - 0.0013 * (ballSecurity - ref) + 0.0011 * (defensivePressure - ref) + home
        return p.coerceIn(0.07, 0.30)
    }

    fun nonShootingFoul(t: LeagueTuning, offensiveAggression: Double, defensiveDiscipline: Double): Double {
        val ref = t.attributeReference
        val p = t.nonShootingFoulRate * (1 + 0.012 * (offensiveAggression - ref) - 0.012 * (defensiveDiscipline - ref))
        return p.coerceIn(t.nonShootingFoulRate * 0.4, t.nonShootingFoulRate * 1.8)
    }

    /** Relative shot-type preferences for one shooter; returned weights sum to 1. */
    fun shotTypeWeights(
        t: LeagueTuning,
        insideSkill: Double,
        midrangeSkill: Double,
        threeSkill: Double,
        threePointEmphasis: Int,
    ): Map<ShotType, Double> {
        val ref = t.attributeReference
        val emphasis = 0.6 + 0.8 * threePointEmphasis / 100.0
        val inside = t.shotMixInside * exp((insideSkill - ref) / 22.0)
        val mid = t.shotMixMidrange * exp((midrangeSkill - ref) / 22.0)
        val three = t.shotMixThree * emphasis * exp((threeSkill - ref) / 14.0)
        val total = inside + mid + three
        return mapOf(
            ShotType.INSIDE to inside / total,
            ShotType.MIDRANGE to mid / total,
            ShotType.THREE_POINTER to three / total,
        )
    }

    /** Probability an unblocked attempt goes in. */
    fun make(t: LeagueTuning, type: ShotType, shooterSkill: Double, defenderSkill: Double, homeOffense: Boolean?): Double {
        val ref = t.attributeReference
        val home = when (homeOffense) {
            true -> t.homeMakeBonus / 2
            false -> -t.homeMakeBonus / 2
            null -> 0.0
        }
        return when (type) {
            ShotType.INSIDE ->
                (t.makeRateInside + 0.0024 * (shooterSkill - ref) - 0.0018 * (defenderSkill - ref) + home)
                    .coerceIn(0.35, 0.85)
            ShotType.MIDRANGE ->
                (t.makeRateMidrange + 0.0020 * (shooterSkill - ref) - 0.0013 * (defenderSkill - ref) + home)
                    .coerceIn(0.20, 0.60)
            ShotType.THREE_POINTER ->
                (t.makeRateThree + 0.0020 * (shooterSkill - ref) - 0.0011 * (defenderSkill - ref) + home)
                    .coerceIn(0.15, 0.50)
        }
    }

    fun block(t: LeagueTuning, type: ShotType, protectorSkill: Double, shooterSkill: Double): Double {
        val ref = t.attributeReference
        return when (type) {
            ShotType.INSIDE ->
                (t.blockRateInside + 0.0025 * (protectorSkill - ref) - 0.0012 * (shooterSkill - ref)).coerceIn(0.02, 0.20)
            ShotType.MIDRANGE ->
                (t.blockRateMidrange + 0.0010 * (protectorSkill - ref) - 0.0006 * (shooterSkill - ref)).coerceIn(0.005, 0.07)
            ShotType.THREE_POINTER ->
                (t.blockRateThree + 0.0003 * (protectorSkill - ref)).coerceIn(0.0, 0.025)
        }
    }

    fun shootingFoul(t: LeagueTuning, type: ShotType, shooterAggression: Double, defenderDiscipline: Double): Double {
        val ref = t.attributeReference
        val base = when (type) {
            ShotType.INSIDE -> t.shootingFoulRateInside
            ShotType.MIDRANGE -> t.shootingFoulRateMidrange
            ShotType.THREE_POINTER -> t.shootingFoulRateThree
        }
        val p = base * (1 + 0.012 * (shooterAggression - ref) - 0.010 * (defenderDiscipline - ref))
        return p.coerceIn(base * 0.4, base * 2.0)
    }

    fun offensiveRebound(t: LeagueTuning, offenseStrength: Double, defenseStrength: Double, afterFreeThrow: Boolean): Double {
        val ratio = if (defenseStrength <= 0) 1.0 else offenseStrength / defenseStrength
        val p = (t.offensiveReboundRate * ratio.pow(1.0)).coerceIn(0.14, 0.45)
        return if (afterFreeThrow) p * t.freeThrowOffensiveReboundFactor else p
    }

    fun assist(t: LeagueTuning, type: ShotType, bestTeammatePlaymaking: Double): Double {
        val base = when (type) {
            ShotType.INSIDE -> t.assistRateInside
            ShotType.MIDRANGE -> t.assistRateMidrange
            ShotType.THREE_POINTER -> t.assistRateThree
        }
        return (base * (1 + 0.008 * (bestTeammatePlaymaking - t.attributeReference))).coerceIn(0.20, 0.95)
    }

    fun freeThrow(t: LeagueTuning, freeThrowSkill: Double): Double =
        (t.freeThrowBase + t.freeThrowScale * freeThrowSkill / 100.0).coerceIn(0.35, 0.95)

    fun jumpBall(skillA: Double, skillB: Double): Double = (0.5 + 0.01 * (skillA - skillB)).coerceIn(0.3, 0.7)
}
