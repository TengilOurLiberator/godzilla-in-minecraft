# Notes: integration pass (all seven modules together)

## What was wired or changed

* **Services**: the four stand-ins (`core.dev.Dev*`) are gone. `Gz.destruction/city/game/fx()` throw if a module has not installed its
  service; the server logs `godzilla: services destruction=... city=... game=... fx=...` at start (one class each).
* **Run flow** (`/godzilla start <player>`, or smashing the sign): `GameImpl.start` fires RESET, restamps the city (0.4 to 1.8 s with the
  light cache, about 5 s the first time), teleports to `playerStart` (sea floor), phase RISE. The player module sees `phase != IDLE &&
  Gz.game().godzilla() == p` and applies scale 16 and the other modifiers in the next tick; the client plays `RisePath`; the director
  starts `music.rise`, the title is on the surface tick (240) and RAMPAGE begins at tick 384.
* **Lobby** (`game.Lobby`): a 25 x 25 pier at y=63 above `playerStart`, built (only blocks that differ) whenever the game goes idle and a
  player is there; a restamp removes it. Joining in IDLE teleports to it, 10 ticks later `SmashSign` appears 8 blocks ahead
  (fixed anchor). The sign starts a run on a HAND smash (or the attack click) after 1.5 s, or when a player walks within 3 blocks.
  Body, feet, explosions, debris and Mothra do not trigger it (the body ploughing into it at the results screen restarted runs).
* **Results sign** is 0.8 x scale ahead and 0.5 x scale below the eyes (clear of the results panel). VICTORY keeps the hands, feet and breath
  working (victory lap); DEFEAT leaves only the hands and the roar. `PlayerNet.StateS2C.flags` is a varint now, with `FALLEN` (256): the
  renderer topples the body backwards about the feet over 40 ticks.
* **Quay climb**: the bay's sea walls are vertical and up to 10 blocks (`terrain.py`, `top_q`), more than his 3 block step. While pushing against
  a wall below the street level the local player rises at 0.5 blocks/tick (`PloughDrag.climb`). Without it every run starting south of the
  quays stopped at the wall.
* **BreathGauge**: `client.game.BreathGauge.setSource(client.player.BreathGauge::value)` in `PlayerClient.init`.
* **Mothra**: only hands, tail and the beam hurt her (BODY and FOOT are ignored: a passive Godzilla was swatting her out of the sky by standing
  there); her gust only blows civilians (it exploded vehicles and killed units, and the score of all of that went to the player); max hit
  points 850; her damage to Godzilla is `GameImpl.DAMAGE_SCALE` (1.3) plus `DAMAGE_RAMP_PER_MINUTE` (0.3) per minute of the fight.
* **Score**: rank thresholds 0 / 40k / 120k / 260k / 480k / 780k. A scripted player that walks through the valuable buildings and swipes
  and breathes all the time makes 750k to 880k; an unskilled player should land in the middle ranks.
* **Budgets**: `core.GodzillaConfig` reads `config/godzilla.properties` (client and server folders each): debris cap, adaptive debris, pieces
  per tick, people, cars, parked, big vehicles, police, trains, `military.scale`. Defaults are the values that were measured below.
* **Sound/flash spam**: crowd screams refill at 2.8/s (was 6.4), squishes 5/s (was 10), military hits flash the view at most every 0.7 s and
  shake every 0.25 s.

## Testing

`tools\playtest.py` (needs the server and test client up; `GZ_MAXFPS=260` for an uncapped client (260 is "unlimited", larger values are
reset to 120), `GZ_CHATVIS=2` hides the "saved screenshot" lines): `--runs 2` plays two runs, `--passive` stands still in the fight,
`--rampage N` jumps to Mothra after N seconds. Output in `work\playtest\<tag>\` (log.txt, shots\). Do not call RCON from another shell while
it runs: the vanilla RCON console source is shared, replies get mixed.

* Rcon: `rotate <player> <yaw> <pitch>` sets the view (used to aim at waypoints and at Mothra).
* Client `godzilla fps` (RCON) logs `GODZILLA-PLAYER fps=... frame=[x ms cpu and y ms wall per frame]`.
* The flat window is 1280x720 on an RTX 5090: 330 to 870 fps uncapped through the whole run, 1.2 to 2.0 ms of render-thread CPU per frame.

## Known weak spots

See the final report of the integration pass; the short list is also at the end of `README.md` ("Not tested in a headset").
