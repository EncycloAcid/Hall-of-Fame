package com.collegedynasty.sim.engine

import com.collegedynasty.sim.model.EventType
import com.collegedynasty.sim.model.FoulType
import com.collegedynasty.sim.model.Game
import com.collegedynasty.sim.model.PlayByPlayEvent
import com.collegedynasty.sim.model.Position
import com.collegedynasty.sim.model.ShotType
import com.collegedynasty.sim.model.TurnoverType
import com.collegedynasty.sim.random.RandomSource
import com.collegedynasty.sim.random.weightedPick
import com.collegedynasty.sim.rules.BonusAward
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Possession-based game loop. Produces the chronological event log; every statistic is later
 * derived from that log, which guarantees that scores, box scores and play-by-play reconcile.
 *
 * Termination: every iteration of the possession loop consumes at least one second of game clock
 * (or the period is already over), each period has a finite clock, and the number of overtimes is
 * capped by [com.collegedynasty.sim.rules.BasketballRules.maxOvertimes].
 */
internal class GameEngine(private val game: Game, private val random: RandomSource) {
    private val rules = game.rules
    private val tuning = game.tuning
    private val home = TeamState(game.home, isHome = true)
    private val away = TeamState(game.away, isHome = false)
    private val rotation = RotationManager(rules, tuning)
    private val narrator = Narrator(game.seed)
    private val events = mutableListOf<PlayByPlayEvent>()

    private var period = 0
    private var clock = 0
    private var elapsed = 0
    private var officialTimeoutDue = false

    private enum class PlayResult { POSSESSION_OVER, MISSED_FIELD_GOAL, MISSED_FREE_THROW }
    private enum class FreeThrowResult { NONE, LAST_MADE, LAST_MISSED }

    fun run(): List<PlayByPlayEvent> {
        check(events.isEmpty()) { "GameEngine instances are single-use" }
        home.onCourt += rotation.startingFive(home)
        away.onCourt += rotation.startingFive(away)

        val lastAllowedPeriod = rules.regulationPeriods + rules.maxOvertimes
        lateinit var openingTipWinner: TeamState
        while (true) {
            startPeriod()
            var offense = when {
                period == 1 -> jumpBall().also { openingTipWinner = it }
                rules.isOvertime(period) -> jumpBall()
                period % 2 == 1 -> openingTipWinner
                else -> opponent(openingTipWinner)
            }
            while (clock > 0) {
                offense = playPossession(offense)
            }
            emit(EventType.PERIOD_END, description = "End of ${rules.periodName(period)}")
            val regulationOver = period >= rules.regulationPeriods
            if (regulationOver && (home.score != away.score || period >= lastAllowedPeriod)) break
        }
        val result = if (home.score == away.score) "ends in a tie (overtime limit reached)" else "Final"
        emit(EventType.GAME_END, description = "$result: ${game.home.name} ${home.score}, ${game.away.name} ${away.score}")
        return events
    }

    // ---------------------------------------------------------------- period / clock management

    private fun startPeriod() {
        period++
        clock = rules.periodLength(period)
        officialTimeoutDue = false
        if (rules.resetsTeamFouls(period)) {
            home.teamFouls = 0
            away.teamFouls = 0
        }
        if (period > 1) {
            (home.players + away.players).forEach {
                it.energy = (it.energy + tuning.energyRecoveryBetweenPeriods).coerceAtMost(100.0)
            }
        }
        emit(
            EventType.PERIOD_START,
            lineups = true,
            description = "Start of ${rules.periodName(period)}",
        )
        if (period > 1) deadBallSubstitutions()
    }

    /** Runs [seconds] off the game clock (never past 0:00) and applies fatigue and recovery. */
    private fun advance(seconds: Int) {
        val dt = seconds.coerceIn(0, clock)
        if (dt == 0) return
        val before = clock
        clock -= dt
        elapsed += dt
        for (team in listOf(home, away)) {
            for (p in team.players) {
                p.energy = if (p in team.onCourt) {
                    val drain = tuning.energyDrainPerSecond * (1.25 - p.attrs.athleticism / 200.0)
                    (p.energy - drain * dt).coerceAtLeast(0.0)
                } else {
                    (p.energy + tuning.energyRecoveryPerSecond * dt).coerceAtMost(100.0)
                }
            }
        }
        rules.officialTimeoutIntervalSeconds?.let { interval ->
            val length = rules.periodLength(period)
            if (clock > 0 && (length - clock) / interval > (length - before) / interval) officialTimeoutDue = true
        }
    }

    private fun opponent(team: TeamState) = if (team === home) away else home

    private fun isFinalPeriodOrLater() = period >= rules.regulationPeriods

    // ---------------------------------------------------------------- possessions

    private fun playPossession(off: TeamState): TeamState {
        val def = opponent(off)
        if (officialTimeoutDue) officialTimeout()

        var shotClock = rules.shotClockSeconds
        var offensiveRebounder: PlayerState? = null
        var sideOuts = 0

        while (clock > 0) {
            if (shouldFoulIntentionally(off, def)) {
                advance(random.nextInt(1, min(clock, 4) + 1))
                val fouled = random.weightedPick(off.onCourt) { (100.0 - it.attrs.freeThrows).pow(2) }
                val fouler = pickFouler(def)
                when (commonFoul(off, def, fouler, fouled, FoulType.INTENTIONAL)) {
                    FreeThrowResult.NONE -> continue
                    FreeThrowResult.LAST_MADE -> return def
                    FreeThrowResult.LAST_MISSED -> {
                        offensiveRebounder = rebound(off, def, afterFreeThrow = true) ?: return def
                        shotClock = rules.shotClockResetSeconds
                        continue
                    }
                }
            }

            val time = possessionTime(off, def, shotClock, offensiveRebounder != null)
            val handler = random.weightedPick(off.onCourt) { (0.5 * it.eff(it.attrs.playmaking) + 0.5 * it.eff(it.attrs.ballHandling)).pow(4) }

            val ballSecurity = 0.45 * handler.eff(handler.attrs.ballHandling) + 0.30 * handler.eff(handler.attrs.playmaking) +
                0.25 * off.courtAverage { it.eff(it.attrs.basketballIq) }
            val pressure = def.courtAverage {
                0.6 * it.eff(it.attrs.perimeterDefense) + 0.25 * it.eff(it.attrs.athleticism) + 0.15 * it.eff(it.attrs.basketballIq)
            }
            if (random.chance(ProbabilityModel.turnover(tuning, ballSecurity, pressure, homeFlag(off)))) {
                turnover(off, def, handler, time, shotClock)
                return def
            }

            if (offensiveRebounder == null && sideOuts < 2) {
                val aggression = 0.5 * handler.eff(handler.attrs.athleticism) + 0.5 * handler.eff(handler.attrs.ballHandling)
                val discipline = def.courtAverage { 0.6 * it.eff(it.attrs.basketballIq) + 0.4 * it.eff(it.attrs.perimeterDefense) }
                if (random.chance(ProbabilityModel.nonShootingFoul(tuning, aggression, discipline))) {
                    val t = max(1, (time * random.nextDouble()).roundToInt())
                    advance(t)
                    val fouled = if (random.chance(0.6)) handler else random.weightedPick(off.onCourt) { 1.0 }
                    when (commonFoul(off, def, pickFouler(def), fouled, FoulType.PERSONAL)) {
                        FreeThrowResult.NONE -> {
                            shotClock = max(shotClock - t, rules.shotClockResetSeconds)
                            sideOuts++
                            continue
                        }
                        FreeThrowResult.LAST_MADE -> return def
                        FreeThrowResult.LAST_MISSED -> {
                            offensiveRebounder = rebound(off, def, afterFreeThrow = true) ?: return def
                            shotClock = rules.shotClockResetSeconds
                            continue
                        }
                    }
                }
            }

            advance(time)
            when (attemptShot(off, def, offensiveRebounder)) {
                PlayResult.POSSESSION_OVER -> return def
                PlayResult.MISSED_FIELD_GOAL -> {
                    offensiveRebounder = rebound(off, def, afterFreeThrow = false) ?: return def
                    shotClock = rules.shotClockResetSeconds
                }
                PlayResult.MISSED_FREE_THROW -> {
                    offensiveRebounder = rebound(off, def, afterFreeThrow = true) ?: return def
                    shotClock = rules.shotClockResetSeconds
                }
            }
        }
        return def
    }

    /** Seconds of game clock this offensive action uses; always within 1..min(shotClock, clock). */
    private fun possessionTime(off: TeamState, def: TeamState, shotClock: Int, secondChance: Boolean): Int {
        val maxTime = min(shotClock, clock)
        if (maxTime <= 1) return 1
        val margin = off.score - def.score
        val late = isFinalPeriodOrLater() && clock <= 120

        // Final possession of a period: hold for the last shot unless trailing late in the game.
        if (clock <= shotClock && !(isFinalPeriodOrLater() && margin < 0)) {
            return (clock - random.nextInt(0, min(4, clock))).coerceIn(1, maxTime)
        }
        if (secondChance && random.chance(0.5)) return random.nextInt(1, min(4, maxTime) + 1)
        if (late && margin > 0) return (shotClock - random.nextInt(1, 6)).coerceIn(1, maxTime)

        val pace = 0.6 * off.team.tactics.pace + 0.4 * def.team.tactics.pace
        val exponent = tuning.paceExponent * (1 + (pace - 50) / 100.0 * 0.6)
        val u = (random.nextDouble() + random.nextDouble()) / 2
        val minTime = min(tuning.minPossessionSeconds, shotClock)
        var t = minTime + (shotClock - minTime) * u.pow(exponent)
        if (late && margin < 0) t *= 0.55
        return t.roundToInt().coerceIn(1, maxTime)
    }

    private fun shouldFoulIntentionally(off: TeamState, def: TeamState): Boolean {
        if (!isFinalPeriodOrLater() || clock <= 0 || clock > tuning.intentionalFoulWindowSeconds) return false
        val deficit = off.score - def.score
        if (deficit !in 1..tuning.intentionalFoulMaxDeficit) return false
        // With a one-possession deficit and more time than the shot clock, play defense instead.
        return !(deficit <= 2 && clock > rules.shotClockSeconds)
    }

    private fun turnover(off: TeamState, def: TeamState, handler: PlayerState, time: Int, shotClock: Int) {
        val roll = random.nextDouble()
        val stealCut = tuning.stealShareOfTurnovers
        val offensiveFoulCut = stealCut + tuning.offensiveFoulShareOfTurnovers
        val shotClockCut = offensiveFoulCut + tuning.shotClockViolationShareOfTurnovers
        val committer = if (random.chance(0.65)) handler else random.weightedPick(off.onCourt) { usage(it).pow(3) }
        when {
            roll < stealCut -> {
                advance(max(1, (time * 0.7).roundToInt()))
                val stealer = random.weightedPick(def.onCourt) {
                    (0.6 * it.eff(it.attrs.perimeterDefense) + 0.25 * it.eff(it.attrs.athleticism) + 0.15 * it.eff(it.attrs.basketballIq)).pow(3)
                }
                emit(
                    EventType.TURNOVER, off, committer, stealer, turnoverType = TurnoverType.STEAL,
                    description = narrator.turnover(committer.player, TurnoverType.STEAL, stealer.player),
                )
            }
            roll < offensiveFoulCut -> {
                advance(max(1, (time * 0.6).roundToInt()))
                val charger = random.weightedPick(off.onCourt) { (0.5 * it.attrs.athleticism + 0.5 * it.attrs.insideScoring).pow(3) }
                recordFoul(off, charger, null, FoulType.OFFENSIVE)
                emit(
                    EventType.TURNOVER, off, charger, turnoverType = TurnoverType.OFFENSIVE_FOUL,
                    description = narrator.turnover(charger.player, TurnoverType.OFFENSIVE_FOUL, null),
                )
                deadBallSubstitutions()
            }
            roll < shotClockCut && clock > shotClock -> {
                advance(shotClock)
                emit(
                    EventType.TURNOVER, off, handler, turnoverType = TurnoverType.SHOT_CLOCK,
                    description = "${narrator.turnover(handler.player, TurnoverType.SHOT_CLOCK, null)} on ${off.team.abbreviation}",
                )
                deadBallSubstitutions()
            }
            else -> {
                advance(max(1, (time * 0.6).roundToInt()))
                val type = listOf(TurnoverType.BAD_PASS, TurnoverType.TRAVEL, TurnoverType.OUT_OF_BOUNDS)[random.nextInt(0, 3)]
                emit(
                    EventType.TURNOVER, off, committer, turnoverType = type,
                    description = narrator.turnover(committer.player, type, null),
                )
                deadBallSubstitutions()
            }
        }
    }

    private fun attemptShot(off: TeamState, def: TeamState, offensiveRebounder: PlayerState?): PlayResult {
        val putback = offensiveRebounder != null && offensiveRebounder in off.onCourt && random.chance(0.6)
        val shooter = if (putback) offensiveRebounder!! else random.weightedPick(off.onCourt) { (usage(it) / tuning.attributeReference).pow(5) }
        val type = if (putback) ShotType.INSIDE else chooseShotType(off, def, shooter)
        val defender = matchup(def, shooter)

        val shooterSkill = shooterSkill(shooter, type)
        val defenderSkill = 0.5 * defensiveSkill(defender, type) + 0.5 * def.courtAverage { defensiveSkill(it, type) }

        val protector = def.onCourt.maxOf { 0.7 * it.eff(it.attrs.interiorDefense) + 0.3 * it.eff(it.attrs.athleticism) }
        val protectorSkill = if (type == ShotType.INSIDE) 0.5 * protector + 0.5 * defensiveSkill(defender, type) else defensiveSkill(defender, type)
        if (random.chance(ProbabilityModel.block(tuning, type, protectorSkill, shooterSkill))) {
            val blocker = if (type == ShotType.INSIDE && !random.chance(0.4)) {
                random.weightedPick(def.onCourt) { (0.7 * it.eff(it.attrs.interiorDefense) + 0.3 * it.eff(it.attrs.athleticism)).pow(4) }
            } else {
                defender
            }
            emit(
                EventType.SHOT_MISSED, off, shooter, blocker, shotType = type,
                description = narrator.shot(shooter.player, type, made = false, putback = putback, assister = null, blocker = blocker.player),
            )
            return PlayResult.MISSED_FIELD_GOAL
        }

        val aggression = when (type) {
            ShotType.INSIDE -> 0.6 * shooter.eff(shooter.attrs.athleticism) + 0.4 * shooter.eff(shooter.attrs.insideScoring)
            else -> 0.5 * shooter.eff(shooter.attrs.ballHandling) + 0.5 * shooter.eff(shooter.attrs.basketballIq)
        }
        val discipline = 0.5 * defender.eff(defender.attrs.basketballIq) + 0.5 * defensiveSkill(defender, type)
        val fouled = random.chance(ProbabilityModel.shootingFoul(tuning, type, aggression, discipline))

        var pMake = ProbabilityModel.make(tuning, type, shooterSkill, defenderSkill, homeFlag(off))
        if (putback) pMake = (pMake + 0.05).coerceAtMost(0.85)
        if (fouled) pMake *= tuning.andOneMakeFactor
        val made = random.chance(pMake)
        val fouler = if (fouled) shootingFouler(def, defender, type) else null

        if (made) {
            val teammates = off.onCourt.filter { it !== shooter }
            val bestPlaymaking = teammates.maxOf { it.eff(it.attrs.playmaking) }
            val assister = if (!putback && random.chance(ProbabilityModel.assist(tuning, type, bestPlaymaking))) {
                random.weightedPick(teammates) { it.eff(it.attrs.playmaking).pow(4) }
            } else {
                null
            }
            off.score += type.points
            emit(
                EventType.SHOT_MADE, off, shooter, assister, points = type.points, shotType = type,
                description = narrator.shot(shooter.player, type, made = true, putback = putback, assister = assister?.player, blocker = null),
            )
            if (fouler == null) return PlayResult.POSSESSION_OVER
            recordFoul(def, fouler, shooter, FoulType.SHOOTING)
            deadBallSubstitutions(setOf(shooter.id))
            return freeThrows(off, shooter, 1, oneAndOne = false).toPlayResult()
        }
        if (fouler != null) {
            recordFoul(def, fouler, shooter, FoulType.SHOOTING)
            deadBallSubstitutions(setOf(shooter.id))
            return freeThrows(off, shooter, type.points, oneAndOne = false).toPlayResult()
        }
        emit(
            EventType.SHOT_MISSED, off, shooter, shotType = type,
            description = narrator.shot(shooter.player, type, made = false, putback = putback, assister = null, blocker = null),
        )
        return PlayResult.MISSED_FIELD_GOAL
    }

    private fun FreeThrowResult.toPlayResult() =
        if (this == FreeThrowResult.LAST_MISSED) PlayResult.MISSED_FREE_THROW else PlayResult.POSSESSION_OVER

    private fun chooseShotType(off: TeamState, def: TeamState, shooter: PlayerState): ShotType {
        val deficit = def.score - off.score
        if (isFinalPeriodOrLater() && clock <= 30 && deficit == 3 && random.chance(0.85)) return ShotType.THREE_POINTER
        val weights = ProbabilityModel.shotTypeWeights(
            tuning,
            insideSkill = shooterSkill(shooter, ShotType.INSIDE),
            midrangeSkill = shooterSkill(shooter, ShotType.MIDRANGE),
            threeSkill = shooterSkill(shooter, ShotType.THREE_POINTER),
            threePointEmphasis = off.team.tactics.threePointEmphasis,
        ).toMutableMap()
        if (isFinalPeriodOrLater() && clock <= 90 && deficit >= 4) {
            weights[ShotType.THREE_POINTER] = weights.getValue(ShotType.THREE_POINTER) * 2.0
        }
        return random.weightedPick(ShotType.entries) { weights.getValue(it) }
    }

    // ---------------------------------------------------------------- fouls and free throws

    /** A defensive non-shooting foul; awards bonus free throws when the fouling team is over the limit. */
    private fun commonFoul(off: TeamState, def: TeamState, fouler: PlayerState, fouled: PlayerState, type: FoulType): FreeThrowResult {
        recordFoul(def, fouler, fouled, type)
        val award = rules.bonusAward(def.teamFouls, period)
        deadBallSubstitutions(setOf(fouled.id))
        return when (award) {
            BonusAward.NONE -> FreeThrowResult.NONE
            BonusAward.ONE_AND_ONE -> freeThrows(off, fouled, 2, oneAndOne = true)
            BonusAward.TWO_SHOTS -> freeThrows(off, fouled, 2, oneAndOne = false)
        }
    }

    private fun recordFoul(team: TeamState, fouler: PlayerState, fouled: PlayerState?, type: FoulType) {
        fouler.fouls++
        team.teamFouls++
        emit(
            EventType.FOUL, team, fouler, fouled, foulType = type,
            description = narrator.foul(fouler.player, fouled?.player, type, fouler.fouls),
        )
        if (fouler.fouls >= rules.personalFoulLimit && !fouler.disqualified) {
            fouler.disqualified = true
            emit(EventType.FOUL_OUT, team, fouler, description = narrator.foulOut(fouler.player))
        }
    }

    private fun freeThrows(off: TeamState, shooter: PlayerState, count: Int, oneAndOne: Boolean): FreeThrowResult {
        val p = ProbabilityModel.freeThrow(tuning, shooter.eff(shooter.attrs.freeThrows))
        var lastMade = false
        for (n in 1..count) {
            lastMade = random.chance(p)
            if (lastMade) off.score += 1
            emit(
                if (lastMade) EventType.FREE_THROW_MADE else EventType.FREE_THROW_MISSED,
                off, shooter, points = if (lastMade) 1 else 0, freeThrowNumber = n, freeThrowTotal = count,
                description = narrator.freeThrow(shooter.player, lastMade, n, count, oneAndOne),
            )
            if (oneAndOne && n == 1 && !lastMade) break
        }
        return if (lastMade) FreeThrowResult.LAST_MADE else FreeThrowResult.LAST_MISSED
    }

    private fun pickFouler(def: TeamState): PlayerState = random.weightedPick(def.onCourt) {
        val trouble = if (it.fouls >= rules.personalFoulLimit - 1) 0.35 else 1.0
        (110.0 - it.attrs.basketballIq) * trouble
    }

    private fun shootingFouler(def: TeamState, defender: PlayerState, type: ShotType): PlayerState =
        if (type == ShotType.INSIDE && random.chance(0.4)) {
            random.weightedPick(def.onCourt) { if (it.player.position.isBig) 2.0 else 1.0 }
        } else {
            defender
        }

    // ---------------------------------------------------------------- rebounds

    /** Resolves a live missed shot. Returns the offensive rebounder, or null if the defense secured it. */
    private fun rebound(off: TeamState, def: TeamState, afterFreeThrow: Boolean): PlayerState? {
        if (clock <= 0) return null
        val strength = { t: TeamState -> t.onCourt.sumOf { 0.75 * it.eff(it.attrs.rebounding) + 0.25 * it.eff(it.attrs.athleticism) } }
        val offensive = random.chance(ProbabilityModel.offensiveRebound(tuning, strength(off), strength(def), afterFreeThrow))
        val team = if (offensive) off else def
        val rebounder = random.weightedPick(team.onCourt) {
            (0.8 * it.eff(it.attrs.rebounding) + 0.2 * it.eff(it.attrs.athleticism)).pow(4) * reboundPositionFactor(it.player.position)
        }
        emit(
            if (offensive) EventType.OFFENSIVE_REBOUND else EventType.DEFENSIVE_REBOUND,
            team, rebounder, description = narrator.rebound(rebounder.player, offensive),
        )
        return if (offensive) rebounder else null
    }

    private fun reboundPositionFactor(position: Position) = when (position) {
        Position.CENTER -> 1.3
        Position.POWER_FORWARD -> 1.15
        Position.SMALL_FORWARD -> 1.0
        Position.SHOOTING_GUARD -> 0.85
        Position.POINT_GUARD -> 0.75
    }

    // ---------------------------------------------------------------- dead balls, substitutions, tip-offs

    private fun officialTimeout() {
        officialTimeoutDue = false
        emit(EventType.TIMEOUT, description = "Official timeout")
        deadBallSubstitutions()
    }

    private fun deadBallSubstitutions(protectedIds: Set<String> = emptySet()) {
        for (team in listOf(home, away)) {
            val situation = RotationSituation(period, clock, elapsed, team.score - opponent(team).score)
            for ((out, inn) in rotation.substitutions(team, situation, protectedIds)) {
                team.onCourt[team.onCourt.indexOf(out)] = inn
                emit(
                    EventType.SUBSTITUTION, team, inn, out,
                    description = narrator.substitution(team.team.abbreviation, inn.player, out.player),
                )
            }
        }
    }

    private fun jumpBall(): TeamState {
        val jumper = { t: TeamState ->
            t.onCourt.maxBy { 0.5 * it.attrs.athleticism + 0.3 * it.attrs.rebounding + if (it.player.position == Position.CENTER) 10.0 else 0.0 }
        }
        val homeJumper = jumper(home)
        val awayJumper = jumper(away)
        val skill = { p: PlayerState -> 0.6 * p.attrs.athleticism + 0.4 * p.attrs.rebounding }
        val homeWins = random.chance(ProbabilityModel.jumpBall(skill(homeJumper), skill(awayJumper)))
        val (winner, winnerJumper) = if (homeWins) home to homeJumper else away to awayJumper
        emit(EventType.JUMP_BALL, winner, winnerJumper, description = narrator.jumpBall(winnerJumper.player, winner.team.name))
        return winner
    }

    // ---------------------------------------------------------------- player composites

    private fun usage(p: PlayerState): Double {
        val a = p.attrs
        val best = maxOf(p.eff(a.insideScoring), p.eff(a.midrangeShooting), p.eff(a.threePointShooting))
        val avg = (p.eff(a.insideScoring) + p.eff(a.midrangeShooting) + p.eff(a.threePointShooting)) / 3
        return 0.45 * best + 0.20 * avg + 0.15 * p.eff(a.athleticism) + 0.20 * p.eff(a.ballHandling)
    }

    private fun shooterSkill(p: PlayerState, type: ShotType): Double = when (type) {
        ShotType.INSIDE -> 0.75 * p.eff(p.attrs.insideScoring) + 0.25 * p.eff(p.attrs.athleticism)
        ShotType.MIDRANGE -> 0.85 * p.eff(p.attrs.midrangeShooting) + 0.15 * p.eff(p.attrs.basketballIq)
        ShotType.THREE_POINTER -> 0.9 * p.eff(p.attrs.threePointShooting) + 0.1 * p.eff(p.attrs.basketballIq)
    }

    private fun defensiveSkill(p: PlayerState, type: ShotType): Double = when (type) {
        ShotType.INSIDE -> 0.7 * p.eff(p.attrs.interiorDefense) + 0.3 * p.eff(p.attrs.athleticism)
        else -> 0.7 * p.eff(p.attrs.perimeterDefense) + 0.3 * p.eff(p.attrs.athleticism)
    }

    private fun matchup(def: TeamState, shooter: PlayerState): PlayerState =
        def.onCourt.minBy { abs(it.player.position.ordinal - shooter.player.position.ordinal) }

    private fun homeFlag(off: TeamState): Boolean? = if (game.neutralSite) null else off.isHome

    // ---------------------------------------------------------------- event log

    private fun emit(
        type: EventType,
        team: TeamState? = null,
        player: PlayerState? = null,
        secondary: PlayerState? = null,
        points: Int = 0,
        shotType: ShotType? = null,
        foulType: FoulType? = null,
        turnoverType: TurnoverType? = null,
        freeThrowNumber: Int? = null,
        freeThrowTotal: Int? = null,
        lineups: Boolean = false,
        description: String,
    ) {
        check(events.size < MAX_EVENTS) { "Event limit exceeded; the simulation failed to terminate" }
        events += PlayByPlayEvent(
            sequence = events.size + 1,
            period = period,
            clockSeconds = clock,
            elapsedSeconds = elapsed,
            type = type,
            teamId = team?.id,
            playerId = player?.id,
            secondaryPlayerId = secondary?.id,
            points = points,
            shotType = shotType,
            foulType = foulType,
            turnoverType = turnoverType,
            freeThrowNumber = freeThrowNumber,
            freeThrowTotal = freeThrowTotal,
            homeScore = home.score,
            awayScore = away.score,
            homeLineup = if (lineups) home.onCourt.map { it.id } else null,
            awayLineup = if (lineups) away.onCourt.map { it.id } else null,
            description = description,
        )
    }

    private companion object {
        const val MAX_EVENTS = 100_000
    }
}
