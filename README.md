# College Dynasty

Android college basketball management game. **Milestone 1** delivers the simulation foundation only: a
pure Kotlin/JVM engine that simulates complete games with seeded, reproducible results. There is no
Android UI, persistence, recruiting or career mode yet.

## Quick start

Requires JDK 17.

```bash
./gradlew :sim-engine:test                     # unit tests + 1,000-game batch test
./gradlew :sim-engine:runSample -Pseed=2026    # print box scores and play-by-play for one game
./gradlew :sim-engine:runBatch -Pgames=1000 -Pseed=1 -Prules=ncaa-men   # also ncaa-women, nba
```

A captured sample game is in [`docs/sample-game.txt`](docs/sample-game.txt). Assumptions, calibration
results and known limitations are in [`docs/SIMULATION_REPORT.md`](docs/SIMULATION_REPORT.md).

## Using the engine

```kotlin
val game = Game(id = "g1", home = homeTeam, away = awayTeam, seed = 42L)   // rules default to NCAA men
val result: GameResult = GameSimulator().simulate(game)                    // instant simulation

result.homeScore; result.boxScore.home.players; result.events               // full result

val feed = LiveGameFeed(result)                                            // incremental reveal
feed.next()                    // next play-by-play event
feed.advanceTo(600)            // everything up to 10:00 of elapsed game time
feed.boxScoreSoFar()           // box score as of the last revealed event
```

`GameSimulator().simulate(game, random)` accepts any `RandomSource`, so tests or callers can inject
their own random numbers. Rules are swapped via `Game(rules = BasketballRules.NBA, tuning = LeagueTuning.PROFESSIONAL)`.

## Source tree

```
sim-engine/                                 pure Kotlin/JVM module (only dependency: kotlin-stdlib; kotlin-test for tests)
  src/main/kotlin/com/collegedynasty/sim/
    model/      Player, PlayerAttributes, Position, Team, TeamTactics, Lineup, Game, GameResult,
                PlayerGameStats, TeamGameStats, BoxScore, PlayByPlayEvent (+ EventType, ShotType, ...)
    rules/      BasketballRules  - rulebook: periods, clocks, foul limits, bonus, overtime (NCAA men/women, NBA)
                LeagueTuning     - statistical calibration (base rates) kept separate from the rulebook
    random/     RandomSource (injectable), SeededRandom, weightedPick
    engine/     GameSimulator    - public entry point
                GameEngine       - possession loop, clock, fouls/bonus, rebounds, overtime, event emission
                ProbabilityModel - bounded per-event probabilities
                RotationManager / LineupSelector - starters and dead-ball substitutions
                GameState        - mutable in-game player/team state (energy, fouls, on-court five)
                Narrator         - play-by-play text (own RNG stream, never affects outcomes)
    output/     BoxScoreBuilder  - derives every stat from the event stream (any prefix of it)
                GameResultAssembler, LiveGameFeed, TextReport
                GameResultValidator - independent consistency checks used by tests and the batch harness
    batch/      BatchSimulator   - runs N games, validates each, aggregates league statistics
    sample/     Fictional rosters, SampleGame and BatchRunner entry points (demo data, not game content)
  src/test/kotlin/com/collegedynasty/sim/  unit tests (see docs/SIMULATION_REPORT.md)
docs/           sample-game.txt, SIMULATION_REPORT.md
```

Simulation models are plain Kotlin data classes intended for the engine only; future database
entities and UI models should map to and from them rather than reuse them.
