# Simulation Report — Milestone 1

## How a game is simulated

The engine is **possession-based**. Each possession runs as a sequence of bounded random events:

1. **Time.** The possession uses a random number of seconds bounded by the shot clock and game clock,
   scaled by both teams' `TeamTactics.pace`. Offensive rebounds reset the shot clock to the rule's
   reset value (20 s NCAA, 14 s NBA). Teams hold for the last shot at the end of a period unless they
   are trailing late.
2. **Turnover check** (ball handling/playmaking/IQ vs. perimeter defense/athleticism). Turnovers are
   split into steals, offensive fouls, bad passes/travels and shot-clock violations.
3. **Non-shooting foul check.** Awards bonus free throws (one-and-one / two shots) when the
   defense is over the team-foul threshold, otherwise a side-out.
4. **Shot.** A shooter is chosen by usage, a shot type (inside / midrange / three) by the shooter's
   skills and team three-point emphasis, then block, shooting foul (and-ones), and make probabilities
   are resolved against the matched defender. Made shots may be assisted.
5. **Rebound.** Misses go to the offense with a probability based on the ratio of on-court rebounding
   strength, then to an individual weighted by rebounding and position.

Late game, trailing teams foul intentionally within the final ~35 s when down by 1–8. Official
timeouts occur at the rule's media-timeout interval. Substitutions happen only at dead balls and
consider rating, fatigue (energy drains on court, recovers on the bench), foul trouble, foul-outs and
garbage time. A tied game after regulation plays overtime periods until there is a winner (or the
configurable `maxOvertimes` cap is reached, which is the only way a game can end tied).

## Assumptions

- **Ratings tilt events, they do not decide games.** Every probability in `ProbabilityModel` starts at a
  league base rate and moves linearly with the attribute difference from `LeagueTuning.attributeReference`
  (60 for college), then is clamped to a realistic band (e.g. turnovers 7–30 %, inside makes 35–85 %,
  threes 15–50 %, free throws 35–95 %). There is no win-probability or score-by-rating formula.
- `Player.overall` is used only for rotation ordering, not for outcomes.
- Free-throw percentage depends only on the free-throw rating (no pressure/fatigue effect).
- Home court is a small make-rate (+1.5 / −1.5 pp for home / away) and turnover-rate nudge; neutral sites remove it.
- Fatigue is a single energy value that scales effective attributes; there is no long-term fatigue or injury.
- Jump balls start the game and each overtime; other regulation periods alternate possession based on
  the opening tip (a simplification of the NCAA arrow and NBA quarter rule). Held balls during play are not modeled.
- One shot attempt per possession phase; offensive rebounds start a new phase on the same possession.
- If a team runs out of eligible players, a disqualified player on the floor stays in rather than the
  team playing short-handed.
- Narration uses a separate random stream derived from the seed, so editing commentary never changes results.
- Determinism: the same `Game` (rosters, rules, tuning, seed) always produces an identical `GameResult`,
  on any JVM, as long as the engine code is unchanged.

## Reconciliation guarantees

The box score is **derived from the event stream** (`BoxScoreBuilder`), so team points, player points
and event points cannot drift apart. `GameResultValidator` independently re-checks every game in the
tests and batch harness: event order and clocks, score deltas vs. event points, `PTS = 2·FGM + 3PM + FTM`,
team totals = sum of players, 5 players × game length = team minutes, makes ≤ attempts, assists/steals/
blocks tied to the correct events, foul limits and no re-entry after fouling out, period totals, and
termination (final event is `GAME_END`, clock fully elapsed, no tie unless the overtime cap is hit).

## Calibration (1,000 games, `./gradlew :sim-engine:runBatch`)

| Stat (per team)   | NCAA men sim | NCAA D1 men reference (approx.) |
|-------------------|-------------:|---------------------------------|
| Points            | 69.4 | ~70–72 |
| Possessions       | 68.6 | ~67–69 |
| FG%               | 44.4 | ~44 |
| 3P%               | 33.6 | ~34 |
| 3PA / FGA         | 39.8 % | ~38–40 % |
| FT%               | 69.9 | ~71 |
| Turnovers         | 13.9 | ~12 |
| Offensive reb %   | 28.2 | ~29 |
| Assists           | 12.0 | ~13 |
| Steals / Blocks   | 7.3 / 3.7 | ~6.5 / ~3.3 |
| Fouls             | 18.3 | ~17 |
| Overtime games    | 3.3 % | ~5–6 % |
| Home win %        | 59.6 | ~60–65 |
| Higher-rated team win % | 74.0 | — |
| Validation failures | 0 | — |

Reference values are approximate recent NCAA D1 averages used as targets, not exact data. The batch
test asserts broad ranges around them so future tuning cannot silently break realism. The NCAA women
preset produces similar numbers; the NBA preset (`LeagueTuning.PROFESSIONAL`) currently yields ~120
points, ~105 possessions and ~28 fouls per team with college-strength sample rosters — usable for
smoke-testing the rulebook but not calibrated.

## Tests

| File | Covers |
|------|--------|
| `ScoringAndStatTotalsTest` | scoring arithmetic, team = Σ players, score = Σ events = Σ periods, minutes, counting stats vs. events |
| `OvertimeTest` | tied regulation → OT, OT length, intermediate OT ties, `maxOvertimes` cap |
| `ReproducibilityTest` | identical results per seed, different seeds differ, injected `RandomSource` drives outcomes |
| `TerminationTest` | 100 games × 3 rulebooks end on time with a winner; constant/degenerate RNGs still terminate |
| `RulesTest` | bonus thresholds, foul carry-over into OT, period lengths/names |
| `ProbabilityModelTest` | all probabilities bounded at extreme ratings; monotonic in skill; home edge small |
| `LiveGameFeedTest` | incremental reveal reaches the final result; partial box score matches partial score |
| `BatchSimulationTest` | 1,000 games: zero validation failures, statistics inside realistic ranges |

## Known limitations

- **No tactics beyond pace and three-point emphasis.** No defenses (zone/press), set plays, pick-and-roll,
  fast breaks, or matchup-specific game plans. Coaches do not call timeouts (only official timeouts).
- **Simplified fouls.** No technical, flagrant, or loose-ball fouls; no free throws for offensive fouls;
  no player foul-out strategy beyond sitting players in foul trouble. Intentional fouling is a simple heuristic.
- **Clock.** Possession times are drawn from a distribution rather than modeled play by play; no
  backcourt/5-second violations, no inbound or free-throw time, and no timeout/replay clock strategy.
- **Rebounding/defense.** Defender matchups are approximated (position-based, best available), and
  help defense is folded into team composites.
- **Player model.** No height/wingspan, handedness, hot streaks, injuries, morale, or chemistry. Usage
  is derived from attributes rather than a separate usage/role rating.
- **Margins are somewhat wide** (≈15 points average in the sample pool) because the demo league pool
  spans a broad talent range; real-roster calibration will need tuning once actual rosters exist.
- **Professional preset is a starting point** only (see calibration above); the rulebook mechanics are
  supported, but tuning and rosters are not.
- Sample rosters/names are generated demo data, not recruiting or player-generation logic.
