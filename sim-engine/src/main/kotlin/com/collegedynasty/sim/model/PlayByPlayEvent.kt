package com.collegedynasty.sim.model

enum class EventType {
    PERIOD_START,
    JUMP_BALL,
    SHOT_MADE,
    SHOT_MISSED,
    FREE_THROW_MADE,
    FREE_THROW_MISSED,
    OFFENSIVE_REBOUND,
    DEFENSIVE_REBOUND,
    TURNOVER,
    FOUL,
    FOUL_OUT,
    SUBSTITUTION,
    TIMEOUT,
    PERIOD_END,
    GAME_END,
}

enum class ShotType(val points: Int) {
    INSIDE(2),
    MIDRANGE(2),
    THREE_POINTER(3),
}

enum class FoulType { SHOOTING, PERSONAL, OFFENSIVE, INTENTIONAL }

enum class TurnoverType { STEAL, BAD_PASS, TRAVEL, OUT_OF_BOUNDS, OFFENSIVE_FOUL, SHOT_CLOCK }

/**
 * One chronological play-by-play entry. The structured fields are the source of truth for every
 * box-score statistic (see [com.collegedynasty.sim.output.BoxScoreBuilder]); [description] is display text.
 *
 * Role of [playerId] / [secondaryPlayerId] by [type]:
 * - SHOT_MADE: shooter / assister
 * - SHOT_MISSED: shooter / blocker
 * - FREE_THROW_*: shooter / -
 * - *_REBOUND: rebounder / -
 * - TURNOVER: player committing it / stealer (only for [TurnoverType.STEAL])
 * - FOUL: player committing it / player fouled
 * - FOUL_OUT: player disqualified / -
 * - SUBSTITUTION: player entering / player leaving
 * - JUMP_BALL: player winning the tip / -
 *
 * [homeLineup] / [awayLineup] are populated on PERIOD_START so on-court time can be reconstructed
 * from the event stream alone.
 */
data class PlayByPlayEvent(
    val sequence: Int,
    val period: Int,
    val clockSeconds: Int,
    val elapsedSeconds: Int,
    val type: EventType,
    val teamId: String? = null,
    val playerId: String? = null,
    val secondaryPlayerId: String? = null,
    val points: Int = 0,
    val shotType: ShotType? = null,
    val foulType: FoulType? = null,
    val turnoverType: TurnoverType? = null,
    val freeThrowNumber: Int? = null,
    val freeThrowTotal: Int? = null,
    val homeScore: Int,
    val awayScore: Int,
    val homeLineup: List<String>? = null,
    val awayLineup: List<String>? = null,
    val description: String,
)
