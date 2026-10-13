# Notes: player module (facts found while building it)

Everything here was exercised in the flat client against the real city unless marked "source only".

## Who is Godzilla
- `GodzillaState.isGodzilla(entity)` = the entity has the attribute modifier `godzilla:godzilla_scale` on `Attributes.SCALE`.
  Attribute modifiers are synced to clients, so client, server and mixins agree with no extra packet. `GodzillaServer.tick`
  (END_SERVER_TICK) re-evaluates every player every tick: `/godzilla become` (forced) or `phase != IDLE && Gz.game().godzilla() == p`.
- Transient modifiers used: scale +15 (16), movement_speed x2.317 (ADD_MULTIPLIED_TOTAL; measured 9.9 blocks/s), step_height +2.4 (3.0),
  jump_strength -1 (0), safe_fall_distance +1000, camera_distance 3.4 (vanilla camera distance is attribute x scale = 54 blocks).
- `ServerPlayer.setGameMode` does nothing when the mode is unchanged: after restoring ADVENTURE the abilities we changed stay.
  Call `GameType.updatePlayerAbilities(abilities)` yourself.
- `Level.getBlockCollisions` goes through `CollisionContext.getCollisionShape(state, getter, pos)` of `EntityCollisionContext`. Mixing
  into that one method (empty shape for blocks above the ground line when the entity is Godzilla) ignores buildings for movement,
  suffocation pushes and the server's "moved wrongly" re-simulation alike. `ClipContext` does NOT use it (it calls
  `state.getCollisionShape` directly), so beam raycasts still hit buildings. Use `CollisionContext.empty()` for rays anyway.
- Server accepts client positions but re-simulates `move()`: never put client-only drag inside `Entity.move`. Drag is applied to
  the velocity at the end of `LivingEntity.travel` on the client only.
- Water: `Player.isAffectedByFluids()` false -> `LivingEntity.travel` takes `travelInAir` even in water (gravity, ground friction):
  he stands and walks on the sea floor, no bobbing. `Entity.updateSwimming` and `Player.updatePlayerPose` are cancelled so no
  swimming/crouching hitbox. Camera fog is decided by the camera position, so it is still underwater fog.

## Rendering the player (26.3)
- `EntityRenderDispatcher.submit(state, camera, x, y, z, poseStack, collector)` is the one place to replace a player body: x,y,z are
  the entity origin relative to the camera. A mixin at HEAD that cancels it for Godzilla's `AvatarRenderState.id` replaces vanilla
  and Vivecraft's player renderers, shadow and held items.
- The local player is only extracted for rendering when `camera.isDetached()` (third person) or Vivecraft's `shouldRenderSelf`;
  `LevelExtractor.extractVisibleEntities` is patched with `@ModifyExpressionValue` on that `isDetached()` call.
- Custom geometry: `collector.submitCustomGeometry(poseStack, RenderType, (pose, vertexConsumer) -> ...)`; entity vertex format
  = position, color, uv, overlay, light, normal. `RenderTypes.entityCutout(tex)` (culled, lit) for the body,
  `RenderTypes.entityTranslucentEmissive(tex)` (translucent, no depth write, no cull, fullbright) for glow, beam, eyes.
- Entity textures repeat (`AbstractTexture` sampler is REPEAT), so tileable textures + world-density planar UVs need no UV layout.
- Vanilla entity shadows fade out beyond 16 blocks from the camera: useless for a 29-block body. The renderer draws its own blobs
  (`textures/misc/shadow.png`, `RenderTypes.entityShadow`) projected on the first collidable block below torso, feet, head, hands, tail.
- HUD: Fabric `HudElementRegistry.replaceElement(VanillaHudElements.X, orig -> (g, dt) -> ...)` to hide hotbar, crosshair, bars.

## Vivecraft (source only, not run in a headset)
- Hand models: `org.vivecraft.client_vr.render.helpers.VRArmHelper.renderVRHands(...)` returns the render `order`; a `@Pseudo` mixin
  cancels it for Godzilla (class referenced by name, ignored when Vivecraft is absent).
- Per-frame pose with controller orientation: `VRClientAPI.getWorldRenderPose()`; `VRBodyPartData.getRotation()` gives the roll.
- The server pose (`Vr.pose`) is the 20 Hz post-tick pose, one tick old.

## Numbers
- Server tick while walking/ploughing/firing the beam with the stand-in destruction: avg 1.7 to 5 ms (`/tick query`).
- Client with beam + body in the city: 50 to 58 fps (flat, vsync capped at 60), renderer CPU 0.1 to 0.2 ms per frame
  (`/godzilla fps` logs `GODZILLA-PLAYER fps=... rendererCpuMs=...`). Walking through towers dropped to a few fps for a moment
  (chunk section rebuilds after the carve), not the body.
