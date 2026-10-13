# Notes: population module (people, vehicles, trains)

Facts found while building `dev.godzilla.population` / `dev.godzilla.client.population`. Verified = ran in this repo.

## Cheap entities (verified)

* A plain `Entity` subclass (not `Mob`/`LivingEntity`) is enough: abstract members are `defineSynchedData`, `readAdditionalSaveData`,
  `addAdditionalSaveData`, `hurtServer` (return false). Register with `EntityType.Builder.of(factory, MobCategory.MISC).sized(w, h)
  .clientTrackingRange(chunks).updateInterval(n).noSave().fireImmune().build(key)`; no attributes needed. Also override
  `shouldBeSaved()` to false.
* The server moves them with `setPos` from one manager tick (no `move()`, no collision code): about 0.3 to 0.5 ms per server tick for
  470 people and 110 vehicles, 0.75 ms for 1590 people and 190 vehicles (`/godzilla pop stats`).
* On the client the default `InterpolationHandler` of an `Entity` is `NO_OP` (the entity would jump with every move packet). Override
  `createInterpolationHandler()` to `LinearInterpolationHandler.create(this, steps)` with `steps` = the type's `updateInterval`.
  `Entity.tick()` must call `commonTick()` (public final: it does `setOldPosAndRot`, the client interpolation and `tickCount++`);
  `baseTick()` is much heavier and not needed.
* `Entity.shouldRenderAtSqrDistance` culls by bounding box size (`size * 64 * viewScale`), i.e. a 0.4 block person vanishes beyond about
  25 blocks. Override it (this module: 150 blocks for people, 260 for vehicles) or the crowd disappears from Godzilla's eyes.
* Server and client have `Entity.random` (protected) and `getRandom()`; `level().isClientSide()` splits the two sides of one class.
* `ServerLevel.getAllEntities()` iterates every entity (used once on RESET to sweep leftovers).

## Rendering a non-living entity (verified)

`EntityRenderer<T, S extends EntityRenderState>`: `createRenderState()`, `extractRenderState(entity, state, partialTicks)` (call super: it
fills x/y/z/lightCoords/ageInTicks), `submit(state, poseStack, SubmitNodeCollector, CameraRenderState)`. The model is an
`EntityModel<S>` (ModelPart tree, `setupAnim(state)`; S can be any `EntityRenderState` subclass, not only a living one). Draw with
`collector.submitModel(model, state, poseStack, textureIdentifier, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor)`.
The pose stack: `rotateDegrees(Axis.YP, 180 - yRot)`, then `scale(-1, -1, 1)` and `translate(0, -1.501, 0)` as the living renderers do;
model units are 1/16 block, y down, 24 = ground, model front is -Z. Several models can live in one renderer (this module's
`VehicleRenderer` keeps four and picks one per state). Set `shadowRadius = 0` to skip shadow pieces. A full-bright draw is
`lightCoords = 15728880`. Box UV layout is the standard one (top, bottom, then right, front, left, back strips); the Python generator
`tools/population/make_textures.py` paints each box with that layout.

## Sound (verified in the log, not listened to)

* `Level.playSound` from the server reaches players within `SoundEvent.getRange(volume)` = `16 * volume` when `volume > 1`, else 16
  blocks, measured from the player's feet. Godzilla's feet may be 40 blocks from a scream, so crowd sounds use volume 2.6, horns 3.2,
  car explosions 6, the city siren 4; on the client volume above 1 is clamped but multiplies the attenuation distance from sounds.json.
* There is no server-side looping. `people.crowd_panic_loop` (120 ticks) and `siren.city_loop` (200 ticks) are replayed by the server on
  their exact length (the clip lengths are whole ticks), so no client sound instance is needed.

## Misc

* Spectator mode with `godzilla scale 16`: `tp` moves the feet, the camera is 25.9 blocks above. In closeup tests use `godzilla scale 1`.
* `/godzilla smash` (stand-in destruction) really removes blocks, rails and bridge deck included: after smashing near a train the
  `Train` "track is gone" check correctly derails every new train. Restamp (or start a new world) before testing trains again.
* BUG IN `dev.godzilla.city` (not fixed here): `CityImpl.rebuildColumn` calls `chunk.markUnsaved()` from parallel worker threads;
  `ChunkMap.setChunkUnsaved` uses a non thread-safe `LongLinkedOpenHashSet`, and after a few `/godzilla stamp` runs the server crashed in
  a random tick (`ArrayIndexOutOfBoundsException: Index -1 ... LongLinkedOpenHashSet.add`). Mark the chunks on the server thread.

## Test helpers (tools/population)

* `make_textures.py` regenerates `mod/src/client/resources/assets/godzilla/textures/population/*.png` (60 files).
* `look.py "<selector>" dx dy dz name [crop x0 y0 x1 y1 scale]` puts the camera at an offset from an entity and shoots it.
* `fastclient.py` starts the test client without the 60 fps cap (the client then runs at about 120; `GODZILLA-POP client fps=...`
  lines in the client log every 5 s while a crowd is visible, with the CPU time spent in this module's renderers).
