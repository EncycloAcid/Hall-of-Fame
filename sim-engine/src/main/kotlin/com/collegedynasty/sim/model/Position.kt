package com.collegedynasty.sim.model

/** Nominal roster position. Used for lineup balance and rebounding/defensive assignments. */
enum class Position(val abbreviation: String) {
    POINT_GUARD("PG"),
    SHOOTING_GUARD("SG"),
    SMALL_FORWARD("SF"),
    POWER_FORWARD("PF"),
    CENTER("C");

    val isGuard: Boolean get() = this == POINT_GUARD || this == SHOOTING_GUARD
    val isBig: Boolean get() = this == POWER_FORWARD || this == CENTER
}
