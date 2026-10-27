package com.collegedynasty.sim.engine

import com.collegedynasty.sim.model.Game
import com.collegedynasty.sim.model.GameResult
import com.collegedynasty.sim.output.GameResultAssembler
import com.collegedynasty.sim.random.RandomSource
import com.collegedynasty.sim.random.SeededRandom

/**
 * Public entry point. Simulation is instant; the returned [GameResult.events] list can be revealed
 * incrementally (see [com.collegedynasty.sim.output.LiveGameFeed]) by a future live text viewer.
 */
class GameSimulator {
    /** Simulates [game] using its own seed. Same game + same seed always yields an identical result. */
    fun simulate(game: Game): GameResult = simulate(game, SeededRandom(game.seed))

    /** Simulates [game] with an injected random source (e.g. a scripted source in tests). */
    fun simulate(game: Game, random: RandomSource): GameResult {
        val events = GameEngine(game, random).run()
        return GameResultAssembler.assemble(game, events)
    }
}
