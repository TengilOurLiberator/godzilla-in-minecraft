# Notes: destruction module (facts found while building it)

## Drawing thousands of blocks (client, verified)

* Fastest way found: `LevelRenderEvents.COLLECT_SUBMITS` (Fabric) and, inside it,
  `ctx.submitNodeCollector().submitCustomGeometry(new PoseStack(), RenderTypes.cutoutMovingBlock(), (pose, buffer) -> ...)`
  (and `RenderTypes.translucentMovingBlock()` for glass). The callback runs while the frame's submits are built, so everything it
  reads must stay valid for the frame. Coordinates are written camera-relative (identity pose; the view matrix is applied by the
  pipeline). The buffer is a `BufferBuilder`: `addVertex(x,y,z,color,u,v,overlay,light,nx,ny,nz)` is the fast 11-argument form.
* The block look is the real one: `Minecraft.getModelManager().getBlockStateModelSet().get(state)` ->
  `BlockStateModel.collectParts(random, parts)` -> `BlockStateModelPart.getQuads(Direction|null)` -> `BakedQuad`
  (`position(i)`, `packedUV(i)` with `UVPair.unpackU/V`, `direction()`, `materialInfo().tintIndex()/layer()/lightEmission()`).
  `MeshCache` flattens these per state id; Patrix therefore shows on the pieces.
* Camera: `ctx.levelState().cameraRenderState` (`pos`, `orientation`, `cullFrustum`), `worldPartialTicks`;
  `mc.gameRenderer.mainCamera()` (not `getMainCamera`) for `position()`, `xRot()`, `yRot()`.
* Vanilla `FallingBlockRenderer` / `BlockDisplay` go through `SubmitNodeCollector.submitMovingBlock / submitBlockModel` and the
  per-entity render-state extraction: that is what makes thousands of display entities slow (see numbers in the report).
* Light for a piece: `LightCoordsUtil.pack(block, sky)`; sample `level.getBrightness(LightLayer.X, pos)`.
* Client tick of this module is skipped unless `level.tickRateManager().runsNormally()`, so `/tick freeze` + `/tick step N`
  freeze and step the debris, masses, puffs and fires too (the way screenshots of a collapse in progress were taken).
* The test client caps itself at `maxFps:60` (rewritten by `tools\testclient.py` on every start): the client command
  `godzilla destruction client maxfps 1000` lifts the cap (the monitor/vsync still ends up at 120 here).

## Particles (client, verified)

* Custom particle types: `FabricParticleTypes.simple(true)` registered in common init (`Registry.register(BuiltInRegistries.PARTICLE_TYPE, ...)`),
  `ParticleProviderRegistry.getInstance().register(type, spriteSet -> provider)` on the client, sprite list in
  `assets/godzilla/particles/<name>.json` (`{"textures":["minecraft:generic_4", ...]}`). `GzPuff extends SingleQuadParticle`
  is created directly (`particleEngine.add(...)`) with the `SpriteSet` kept from the provider registration, so size, drag,
  buoyancy, colour ramp and glow are all ours. `setAlpha` is protected: set it from the subclass. `Particle.scale()` only
  scales the box and the quad size.
* Sprites that work as dust, smoke and fire in Patrix: `generic_4..7` (white puffs), `big_smoke_0..11`, `explosion_0..15`
  (flash, fireball, smoke: pick by age with `setSpriteFromAge`).
* `createParticle(ParticleTypes.FLAME, ...)` returns the particle; `.scale(k)` makes the flame bigger.

## Server side (verified)

* `Blocks.CONCRETE.pick(DyeColor.WHITE)` / `Blocks.STAINED_GLASS.pick(DyeColor.X)` (colour collections; no `WHITE_CONCRETE` field).
* `level.getRandom()` (the field `random` is protected). `level.getChunkSource().getChunkNow(cx, cz)` is the cheap chunk lookup;
  `chunk.getBlockState(pos)`, `chunk.getSection(chunk.getSectionIndexFromSectionY(sy)).getBlockState(x&15,y&15,z&15)` for scans.
* `Level.setBlock(pos, AIR, UPDATE_CLIENTS | UPDATE_SKIP_ALL_SIDEEFFECTS, 0)` also queues the light update (the chunk calls
  `lightEngine.checkBlock` when the light properties change), so the lighting ends up right on its own. Measured 0.6 to 2 us per
  block here, the lighting is done by the light thread afterwards.
* `ChunkPos.asLong(int,int)` does not exist any more in this version (the module uses its own packed key).
* `Heightmap.Types.MOTION_BLOCKING` via `level.getHeight(type, x, z)` is the cheap "first free y above the surface" lookup; it
  is kept up to date by `setBlock`.
* Building ids in the city file: the `name` of a non-landmark building (e.g. "Fuel Tank") comes through `CityData.Building.name()`.
* Seen once: the dedicated server crashed in vanilla `ChunkMap.saveChunksEagerly` (`LongLinkedOpenHashSet.fixPointers`,
  index -1) right after several `/godzilla stamp` and a forceload change; not reproduced, not in this module's code.

## Design in one paragraph

Server: every removal goes through `DestructionService` (cheap flags, structure bookkeeping, debris burst, score, sound).
`Structures` keeps one record per building and per ad-hoc lump (outside any building box), with a per-layer baseline of
load-bearing blocks taken before the first damage. Damaged structures are snapshotted (one per tick, about 1 ms) and analysed on
a worker thread (`Analysis`): support by connectivity (glass and bars hang on, never carry), then per-storey cross-section.
Plans become falling masses (`Collapses`): removed from the world in 2000-block slices, sent to the clients as one `Mass`
packet, landed after the animation time (crush what is under it, hit `Smashable`s, heap the rubble mound as real blocks).
Client: `Debris` (arrays, simulation, one tight draw loop), `Masses` (rigid clusters, outer faces only), `FxClient`/`GzPuff`.

## Test commands

`/godzilla collapse <id> [lean]`, `/godzilla debris <n> <pos> [radial|up|dir]`, `/godzilla ignite <pos> <r>`,
`/godzilla build ground|tower|lattice ...` (test structures on the flat world), `/godzilla destruction stats|resetstats|reset|set|bench <calls> <radius>`,
`/godzilla destruction client <stats|max N|adaptive on|off|log on|off|clear|cull D|maxfps N|bench N fall|float|benchent N>`.
