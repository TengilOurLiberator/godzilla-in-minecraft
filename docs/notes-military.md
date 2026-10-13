# Notes: military module (facts found while building it)

Code: `dev.godzilla.military` (server) and `dev.godzilla.client.military`; assets by `tools\military\make_assets.py`.

## How it is built

* **Entities** (`MilEntities`): `mil_tank`, `mil_heli`, `mil_jet`, `mil_ship`, `mil_maser`, `mil_shot`. Plain `Entity` subclasses, no mob AI,
  `noSave`, `fireImmune`. Builder: `EntityType.Builder.of(factory, MobCategory.MISC).sized(w,h).clientTrackingRange(chunks).updateInterval(n).noSave().build(key)`.
  The server moves them with `setPos` each tick (no physics, no `baseTick`: `tick()` is overridden without `super`, it is `commonTick()` that does
  `setOldPosAndRot`, interpolation and `tickCount++`). Client smoothing: `createInterpolationHandler()` returning `LinearInterpolationHandler.create(this, 3)`;
  without it positions step at the packet rate.
* `shouldRenderAtSqrDistance` must be overridden (default is 64 x box size: a 2-block tank would vanish at 150 blocks).
  `entity-broadcast-range-percentage=500` and `view-distance=24` are in the test server's `server.properties`; the tracking range of the types is 14..24 chunks.
* **Projectiles are entities** (`MilShot`): the server places them along a precomputed arc (`from`, `to`, flight ticks, arc height, weave), so a shot costs one
  `setPos` per tick. The client draws a glowing streak (`GlowModel`, emissive translucent box) and spawns the smoke trail in its own `tick()`. At the end of the
  arc the server decides hit (inside Godzilla's torso cylinder), splash (water), or small city explosion (`Cause.PROJECTILE`, rate-limited by `Mil.takeBlast`).
* **Particles beyond 32 blocks**: `ClientLevel.addParticle(opt, x,y,z,...)` silently drops them. Use `addParticle(opt, true, true, ...)`
  (overrideLimiter, alwaysShow) on the client and `ServerLevel.sendParticles(opt, true, true, x,y,z, count, dx,dy,dz, speed)` on the server.
* **Models**: geometry and uv come from `assets/godzilla/mil/<name>.json` written by the Python script (parts, boxes in the 26.3 model convention, y down,
  front = -z); `MilModels` turns them into a `LayerDefinition`, `MilModel` animates parts by name (`turret*` yaw, `barrel*` pitch, `rotor`, `tail_rotor`).
  Ships are modelled at 4 px per block and scaled x4 by the renderer. Entity render transform: `rotateDegrees(Axis.YP, 180 - yaw)`, roll/pitch, `scale(-s,-s,s)`,
  `translate(0,-1.501,0)`, then `submitModel(model, state, pose, texture, light, overlay, outline)`; `model.setupAnim(state)` runs at draw time.
* `PoseStack` in 26.3 has no `mulPose(Quaternionf)`: use `pose.rotateDegrees(Axis.YP, deg)` / `pose.rotate(Axis.XP, rad)`.
* `Level.random` is protected: use `level.getRandom()`. `Entity.xo/yo/zo` are `xOld/yOld/zOld`.
* Sounds: `level.playSound(null, x,y,z, event, SoundSource.HOSTILE, volume, pitch)`; the packet only reaches players within `16 x volume` blocks, so
  `Mil.sound(level, at, event, rangeBlocks, pitch)` uses volume = range / 16 (the client falloff scales with it too).
* Directly written files: PowerShell 5.1 `Set-Content -Encoding utf8` adds a BOM which javac rejects ("illegal character ﻿"): use `[IO.File]::WriteAllText(path, text, new UTF8Encoding($false))`.

## Director numbers (`Director`)

Clock t = max(`runTicks`, wave floor) in seconds. Ships 3 from the rise (4 from 1:00, 5 from 2:00); tanks from 0:20: 4 + (t-20)/5, max 20 (13 spawn points,
so about 13 at once at the start, more as they drive off); helicopters from 0:20: 1 + (t-20)/15, max 8; jets from 1:15 in pairs, 2 + 2(t-75)/50 alive, max 6, a pair
every 22 s shrinking to 8 s; maser cannons from 2:00: 1 + (t-120)/30, max 4. Replacement delays: ship 30 s, tank 2 s, heli 4 s, maser 10 s, jet by the pair interval.
By t = 2:00: about 20 tanks, 7 helicopters, 2-4 jets, 5 ships, 1 maser, 15+ shots in the air.
Scores: tank 300, helicopter 600, jet 900, maser 1000, ship 2500.

## Test aids (commands, all `/godzilla mil ...`)

`spawn <tank|heli|jet|ship|maser> [n]` (jet = a pair), `wave <n>` (clock = n x 30 s and fill to the caps), `stats`, `clear`,
`smash <type> <cause>` (hit all of a type as `Cause`), `focus <x y z>|off` (everything shoots at a virtual Godzilla standing there, so the camera can move away;
the player's scale still sets the size), `cam <type> [dist]` (moves the player to look at a unit).
Needs a Godzilla: `/godzilla dev phase RAMPAGE Tester4` (stand-in game). Client: a file `mil_uncap` in the test client's game folder lifts the 60 fps cap that
`tools/testclient.py` writes; the client logs `GODZILLA-MIL client fps=.. units=.. shots=..` every 5 s while units are around.
