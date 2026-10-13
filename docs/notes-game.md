# Notes: game director and Mothra (modules `game`, `mothra`)

Facts found while building these two modules on Minecraft 26.3 / Fabric API 0.162. All were compiled and run in the flat client
unless marked "source only".

## Sound

* `level.playSound(null, x, y, z, event, source, volume, pitch)` only reaches players within `16 * volume` blocks for a variable
  range event (`SoundEvent.getRange`), however large `attenuation_distance` is in sounds.json. The 200 to 300 block sounds
  (Mothra, Godzilla) therefore never arrive. `GameAudio.at(player, event, source, pos, vol, pitch)` sends a
  `ClientboundSoundPacket` straight to the player (`new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ev),
  source, x, y, z, volume, pitch, seed)`); the client then applies the sound's own attenuation distance.
* A client `SoundInstance` CAN loop although a sound event cannot: `AbstractTickableSoundInstance` with `looping = true` loops a
  streamed ogg seamlessly (`SoundEngine` hands `isLooping && delay == 0` to the audio stream as a loop). Set `relative = true` and
  `attenuation = NONE` for music that must not care about the 16x scale, and override `canStartSilent()` to return true if the
  instance starts at volume 0 (fade in), otherwise the engine drops it. `Minecraft.getMusicManager().stopPlaying()` keeps vanilla
  music quiet. `SimpleSoundInstance.forUI(event, pitch, volume)` for one-shot interface sounds. `SoundEvent.createVariableRangeEvent(id)`
  makes a throw-away event for any id in sounds.json without registering it.
* The `ui.combo_up` variants are files of one event; a client cannot pick a variant by name, so the combo level changes the pitch.

## Entities

* `Entity` abstract methods in 26.3: `defineSynchedData(SynchedEntityData.Builder)` (`net.minecraft.network.syncher`),
  `hurtServer(ServerLevel, DamageSource, float)`, `readAdditionalSaveData(ValueInput)`, `addAdditionalSaveData(ValueOutput)`.
  `shouldBeSaved()` is overridable (return false so the entity is never written to the world).
* Smooth client movement of a plain entity: override `createInterpolationHandler()` with `LinearInterpolationHandler.create(this, 2)`
  and register the type with `.updateInterval(1)`.
* A player's velocity is changed from the server with `player.push(x, y, z); player.syncVelocity = true;` (`ServerEntity` then sends a
  `ClientboundSetEntityMotionPacket` to the player). `setInvulnerable` no longer exists.
* `EntityType.Builder.of(Factory, MobCategory).sized(w, h).clientTrackingRange(chunks).updateInterval(n).noSave().noSummon().build(key)`.
  An entity type needs a client renderer even if it draws nothing (see `GameClient.NothingRenderer`).
* Text displays (`EntityTypes.TEXT_DISPLAY`, class `Display.TextDisplay`) have public setters: `setText`, `setBillboardConstraints`,
  `setBrightnessOverride(new Brightness(15, 15))`, `setViewRange`, `setLineWidth`, `setBackgroundColor`, `setFlags` (1 shadow, 2 see
  through, 4 default background), `setTransformation(new Transformation(translation, leftRot, scale, rightRot))`,
  `setTransformationInterpolationDelay/Duration`. A text display line is 0.25 blocks tall at scale 1: the score popups use scale 15 to 48
  so they read from 100 blocks. A subclass may override `shouldBeSaved()` and still be created with `EntityTypes.TEXT_DISPLAY`.

## Rendering

* `EntityRenderer<T, S>`: `createRenderState()`, `extractRenderState(entity, state, pt)`, `submit(state, poseStack, SubmitNodeCollector,
  CameraRenderState)`. Override `getBoundingBoxForCulling` for entities that draw outside their hitbox (Mothra's wings).
* `PoseStack.mulPose(Quaternion)` is gone: use `poseStack.rotateAround(quaternion, 0, 0, 0)`.
* Arbitrary geometry: `collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(texture), (pose, vertexConsumer) -> ...)`
  with `vc.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(o).setLight(l).setNormal(pose, nx, ny, nz)`; the
  translucent render types do not cull, so a single quad shows from both sides. `RenderTypes.entityTranslucentEmissive` ignores light.
  Mothra's wings are drawn this way (bent surfaces), her body with a `ModelPart` model (`submitModel`).
  A model uses vanilla's frame (y down, front -z); `poseStack.scale(S, -S, -S)` maps it to y up, front +z without flipping winding.
* Hit flash: `OverlayTexture.pack(whiteness 0..1, false)`.
* `Hud` elements: `HudElementRegistry.addLast(id, (GuiGraphicsExtractor graphics, DeltaTracker dt) -> ...)`; `graphics.text`,
  `centeredText`, `fill`, `fillGradient`, `outline`, `pose().pushMatrix()/translate/scale/popMatrix()` (a `Matrix3x2fStack`).
  Text colour needs the alpha byte (0xFFRRGGBB).

## Vivecraft and the HUD (source only, `work\vivesrc`)

The whole vanilla `Hud` (every Fabric HUD element, boss bars, titles, hotbar) is drawn into one GUI texture that Vivecraft puts on a
quad in the world: in front of the head or on a hand, at `hudDistance` times the world scale (see `GuiHandler`). So anything a
`HudElement` draws is in the headset, scaled with the 16x world scale; the screen corners are far out in the periphery, which is
why `GameHud` keeps everything in a column at the top centre and the breath gauge at the bottom centre. A red screen flash drawn
in a HUD element only tints that quad, not the whole view: the real VR hit flash is `Fx.hitFlash` of the player module.

## A bug found in the city module

`CityImpl.rebuildColumn` called `chunk.markUnsaved()` from `IntStream.parallel()` workers. `ChunkMap.unsavedChunks` is a plain
`LongLinkedOpenHashSet`: concurrent adds corrupted it and the server crashed some seconds after a restamp with
`ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 4097` in `ChunkMap.setChunkUnsaved` (called from a grass
`randomTick`). Fixed in this branch by calling `markUnsaved()` for every chunk sequentially after the parallel loop.

## Game module

* All the numbers: `GameImpl` (combo thresholds, rank scores), `Mothra` constants (hit points 1000, damages), `ScaleCloud`.
* Everything the director sends is a payload in `GameNet` (Audio, Hud, Cue, Results); the HUD, music and popups are driven from them.
* `/godzilla phase <PHASE> [player]`, `/godzilla skip`, `/godzilla score <KIND> <points> [pos]`, `/godzilla hurt <n>`,
  `/godzilla game`, `/godzilla sign [restart]`, `/godzilla bonus <pos>`, `/godzilla results`,
  `/godzilla mothra spawn [now] | kill | attack gust|scales|dive | hp <n> | hit <CAUSE> | status`.
* Best score: `<world>/data/godzilla_best.json`.
