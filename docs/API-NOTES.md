# API notes: Minecraft 26.3, Fabric API 0.162.0, Vivecraft 1.3.17

Written while building the skeleton. "Verified" means it compiled and ran in this repo (server and flat client, see the
checks at the end of each section). "Source only" means read in the decompiled sources but not exercised. Look things up
in the sources (section 2) before writing calls from memory: 26.x differs from most documentation online.

## 1. Toolchain and scripts

| Thing | Value |
|---|---|
| JDK | Eclipse Temurin 25.0.4.1+1 in `D:\godzilla-vr\jdk` (downloaded from api.adoptium.net, sha256 checked; zip kept in `downloads\jdk25.zip`). The Minecraft launcher's Java 25 runtime is a JRE without javac, so it cannot build. Nothing changes PATH or JAVA_HOME globally; scripts set `JAVA_HOME` for their own process, and `mod\gradle.properties` has `org.gradle.java.home=D:/godzilla-vr/jdk` |
| Gradle | 9.7.1 (wrapper, `-bin`); the example mod for 26.3 uses it and Loom 1.18 needs it. The 9.3.1 in the wrapper cache is not used |
| Loom | `net.fabricmc.fabric-loom` version `1.18.4` (the example-mod branch `26.3` says `1.18-SNAPSHOT`; 1.18.4 is the release of that line). NOTE the plugin id: `net.fabricmc.fabric-loom`, not `fabric-loom` |
| Fabric Loader | 0.19.5, Fabric API `0.162.0+26.3`, Minecraft `26.3` |
| Differences from older guides | No `mappings` line, no `modImplementation`, no `remapJar`: the game is unobfuscated, use `implementation`. `loom { splitEnvironmentSourceSets() }` gives `src/main` (common) and `src/client` (client only). `options.release = 25` |
| Vivecraft | `compileOnly` and `clientCompileOnly` of `downloads\vivecraft-26.3-1.3.17-fabric.jar` (a plain `files(...)`), `suggests` (not `depends`) in `fabric.mod.json` |
| Generated content | `processResources` copies `city\out\tokyo.gzc` + `tokyo.json` to `data/godzilla/city/` and `audio\out\**` (except `*.wav`) to `assets/godzilla/` (`sounds.json` ends up at `assets/godzilla/sounds.json`, ogg files under `assets/godzilla/sounds/`). A missing folder is fine (`from()` of a missing directory is empty). Do NOT add `inputs.dir(...)` for them: that fails when the folder is absent |

### Instances and worktrees (several agents at once)

Every script works on one **instance** (0..9) of one **checkout** (the main checkout `D:\godzilla-vr` or a git worktree of it).
The checkout is the folder the script lives in: its `mod\src` is compiled and its `mod\build\libs` jar is deployed.
The shared untracked things always come from the main checkout, whatever the checkout: `jdk`, `downloads` (Vivecraft jar, Patrix,
fabric-api), the template `server`, `work`, `game`. `GZ_MAIN` overrides the main checkout path (not needed).

| | instance 0 (main checkout, classic) | instance N = 1..9 (one per worktree) |
|---|---|---|
| server folder | `D:\godzilla-vr\server` | `D:\godzilla-vr\work\inst<N>\server` (created on first use from a copy of `server\` without world/logs/godzilla jar) |
| server / RCON port | 25567 / 25577 | 25567+N / 25577+N |
| world | `server\godzilla-world` | `work\inst<N>\server\godzilla-world` |
| test client folder, screenshots | `work\testgame`, `work\shots` | `work\inst<N>\testgame`, `work\inst<N>\shots` |
| player name | `Tester` | `Tester<N>` (use it in every RCON command) |
| memory | server 4-10G, client 4G | server `-Xmx3G`, client `-Xmx3G`, Gradle JVM `-Xmx2G` |
| jar deployed by build.ps1 | `server\mods`, `work\testgame\mods`, `game\mods` (the VR client) | the instance's `server\mods` and `testgame\mods` |

The instance comes from `$env:GZ_INSTANCE`, else from the file `<worktree>\.gz_instance` (git-ignored), else 0. Because each tool call
may be a fresh shell, write the file once per worktree instead of setting the variable: `powershell -File tools\dev.ps1 instance 2`.
Worktree agents must never use instance 0 (it is the main checkout's server). Do not use instance numbers above 9 (the ports would
collide with instance 0's RCON port).

**One-command loop** (from the worktree root; add `-Instance N` to any verb to override for that call):
```
powershell -NoProfile -File tools\dev.ps1 instance 2                  # once per worktree
powershell -NoProfile -File tools\dev.ps1 up                          # build + new offline world + server + test client joined (about 45 s)
powershell -NoProfile -File tools\dev.ps1 rcon "gamemode spectator Tester2" "tp Tester2 8 66 12 0 8" "summon godzilla:test_dummy 8 64 20 {NoAI:1b,NoGravity:1b}"
powershell -NoProfile -File tools\dev.ps1 shot a                      # then read D:\godzilla-vr\work\inst2\shots\a.png
powershell -NoProfile -File tools\dev.ps1 rebuild                     # after code changes: stop both, build, start both, KEEP the world
powershell -NoProfile -File tools\dev.ps1 down                        # stop this instance's client and server (nothing else)
powershell -NoProfile -File tools\dev.ps1 status                      # also: log [N] for the client log tail
```
`up` first stops this instance's leftovers, `rebuild` does not delete the world, `down` only touches the processes recorded for this
instance (the server's pid file plus a `-Dgodzilla.instance=N` marker in its command line, the client's pid plus its game folder in the
command line), so another instance is never stopped, even if a pid was reused. Server log: `<server folder>\logs\latest.log`.

Individual scripts (all honour the instance; Python is `D:\voice-assistant\.venv\Scripts\python.exe -I`):

| Script | Use |
|---|---|
| `powershell tools\build.ps1 [-Clean] [-Sources]` | Gradle build of this checkout with the project JDK (`--no-daemon`, so no java process stays behind), then copies the jar as in the table above, replacing older `godzilla-*.jar`. On Windows a running server/game holds its jar open: stop it first (the script warns instead of failing). `-Sources` runs `tools\sources.ps1` |
| `powershell tools\server.ps1 [-NewWorld] [-Offline] [-Background]` / `-Stop` | Starts the instance's server. `-Offline` = online-mode and enforce-secure-profile false (needed for the test client). `-Background` runs hidden and returns when RCON answers (created through WMI so it holds none of the caller's pipe handles, otherwise a tool waiting for the pipes to close hangs). Before starting it copies this checkout's built jar into the server's `mods`, and re-applies the Vivecraft server config rules (below). In a brand-new world it also runs `forceload add 0 0` and `setworldspawn 0 64 0`: without that the world spawns the player at y=-63 inside the stone and he suffocates a minute after joining. `-Stop` sends RCON `stop` |
| `python tools\rcon.py "cmd1" "cmd2"` | RCON of the instance; port and password come from its `server.properties`. As a module: `from rcon import Rcon` |
| `python tools\testclient.py start [--vivecraft] [--no-wait]` / `shot <name>` / `status` / `log [N]` / `stop` | Flat silent offline Fabric client (fabric-api + this checkout's jar + Patrix, no Vivecraft unless `--vivecraft`, which still stays flat because `vrEnabled=false`). Auto-joins the instance's port as the instance's player name. `start` returns when the S2C ping round trip shows in the client log. `shot` finds the window by the launched process id (never by title) and writes `<shots folder>\<name>.png` (1280x720) with PrintWindow (works here, the window need not be in front; a uniform picture would trigger a fallback to the game's own F2 screenshot, which needs the window in front, untested). The process is detached and lives until `stop` |
| `python tools\setup_client.py [--install-profile]` | Prepares the shared `game\` for the real VR client (section 8); main checkout paths whatever the checkout. Without the flag it only prints the launcher profile it would add |
| `powershell tools\sources.ps1 [-Force]` | Makes the sources browsable (section 2). Always writes to the main checkout's `work\`; treat the result as read-only, do not run it from a worktree unless it is missing |
| `python tools\make_test_texture.py` | Regenerates the test entity's texture in this checkout |

Build details that matter in a worktree:
* `mod\build.gradle` takes `downloads\vivecraft-...jar` from the checkout if present there, otherwise from the main checkout; `city\out` and `audio\out` likewise (a worktree that tracks them uses its own).
* Each worktree gets its own `mod\.gradle` (Loom cache) on the first build; Minecraft itself comes from the shared `~\.gradle` cache. Measured here: first build in a fresh worktree 11 s (Gradle's own time), `dev.ps1 up` from nothing about 45 s including copying the server template, starting the server and the client joining. Later builds are about 5 s. `genSources` (decompiling) is NOT part of a normal build and is not needed in a worktree.
* Several builds at once are fine (shared Gradle caches are lock-protected). Nothing in `mod\.gradle`, `mod\build` or `.gz_instance` is tracked.
* Vivecraft server config: `server.ps1` sets `[teleport] enabled = false` and `[climbey] enabled = false` and keeps `[worldScale] limitRange = false` in every instance's `config\vivecraft-server-config.toml` (and in the template). The file is only created by Vivecraft on a server's first start, so the very first start of a server that never ran has the defaults until its next start.
* Chat messages show in the test client's screenshots; use `gamemode spectator` to fly. Kill nothing but the processes the scripts started: other agents run java processes too.

## 2. Browsing the sources

`powershell tools\sources.ps1` (idempotent) produces, all under `work\` (not in git):

* `work\mcsrc\common\net\minecraft\...` decompiled Minecraft 26.3 shared/server code (5038 files), `work\mcsrc\client\net\minecraft\client\...` client-only code (2265 files). Made by Loom's `genSources` (Vineflower, ~75 s the first time; output is the `*-sources.jar` files in `mod\.gradle\loom-cache\minecraftMaven\net\minecraft\minecraft-common-*\26.3\` and `minecraft-clientOnly-*`). Because the game is unobfuscated these are the real names, with parameter names.
* `work\fabricsrc\<module>\...` Fabric API sources, e.g. `fabric-networking-api-v1`, `fabric-rendering-v1`, `fabric-object-builder-api-v1`, `fabric-command-api-v2`, `fabric-key-mapping-api-v1`, `fabric-lifecycle-events-v1`.
* `work\vivesrc\...` Vivecraft decompiled. Public API: `org\vivecraft\api\`. Internals worth reading: `client_vr\gameplay\VRPlayer.java`, `server\ServerVivePlayer.java`, `common\network\VrPlayerState.java`, `client_vr\provider\openvr_lwjgl\MCOpenVR.java`.

Grep, don't guess: `Grep pattern "class EntityRenderers" path work\mcsrc`.

## 3. 26.3 naming changes that bite

* `net.minecraft.resources.Identifier` replaces `ResourceLocation`: `Identifier.fromNamespaceAndPath(ns, path)`, `Identifier.withDefaultNamespace(path)`.
* Vanilla registry constants live in `EntityTypes` (not `EntityType`), `Items`, `SoundEvents`, etc.; `EntityType.Builder.of(factory, MobCategory)` is still on `EntityType`. `EntityType.Builder.build(ResourceKey<EntityType<?>>)` needs the key.
* `GameProfile` is a record: `player.getGameProfile().name()`. `ServerGamePacketListenerImpl.getPlayer()`.
* Permissions: `.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))` (also `LEVEL_ALL`, `LEVEL_MODERATORS`, `LEVEL_ADMINS`, `LEVEL_OWNERS`). RCON and the console pass.
* Game rules are typed objects: `server.getGameRules().set(GameRules.SPAWN_MOBS, false, server)`; the rule names are `spawn_mobs`, `advance_time`, `advance_weather`, ...
* Time is clock/time-marker based (`ServerClockManager`, `ClockTimeMarkers.NOON`); `time query daytime` no longer exists. The skeleton runs the vanilla command `time set noon` through `server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), ...)`.
* Rendering is `submit(state, poseStack, SubmitNodeCollector, CameraRenderState)` over render states, not `render(entity, ...)`.
* Entity teleport: `entity.snapTo(x, y, z, yRot, xRot)`.

## 4. Registering things (all verified unless noted)

Registration code lives in `dev.godzilla.core`; each registry class has a static initialiser that does the work and an empty `register()` that `GodzillaMod.onInitialize` calls to force class loading.

* **Entity types** (`GodzillaEntities`): `Registry.register(BuiltInRegistries.ENTITY_TYPE, key, FabricEntityType.Builder.createMob(Factory::new, MobCategory.MISC, mob -> mob.defaultAttributes(Mob::createMobAttributes)).sized(w, h).eyeHeight(e).clientTrackingRange(16).build(key))`. `createMob`/`createLiving` also register the default attributes (otherwise `FabricDefaultAttributeRegistry.register(type, builder)`). `/summon godzilla:test_dummy x y z {NoAI:1b,NoGravity:1b}` works, also in peaceful (types are allowed in peaceful by default; `allowedInPeaceful` is a builder flag). Set `setPersistenceRequired()` in a Mob's constructor or it despawns.
* **Attributes** (`GodzillaAttributes`): `Registry.registerForHolder(BuiltInRegistries.ATTRIBUTE, id, new RangedAttribute(descriptionKey, default, min, max).setSyncable(true))`. To add it to players: `FabricDefaultAttributeRegistry.MODIFY.register(ctx -> ctx.modify(EntityTypes.PLAYER, (type, builder) -> builder.add(HOLDER)))`. Checked at runtime: `/attribute Tester godzilla:atomic_power get` returns 1.0.
* **Sound events** (`GodzillaSounds`): `Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id))`. The event id must match a key of `assets/godzilla/sounds.json` (the audio generator writes keys like `godzilla.roar`, which is the event `godzilla:godzilla.roar`). Compiles and registers; playback not listened to. Server side `level.playSound(null, pos, EVENT, SoundSource.HOSTILE, volume, pitch)`; with `/playsound godzilla:godzilla.roar master @s` from RCON.
* **Commands** (`GodzillaCommands`): `CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> ...)` (Fabric `fabric-command-api-v2`), Brigadier as usual. `EntityArgument.players()` / `getPlayers`, `BlockPosArgument.blockPos()` / `getLoadedBlockPos`, `BlockStateArgument.block(buildContext)` / `getBlock(c, "x").getState()`, `source.getPlayerOrException()`, `source.sendSuccess(() -> Component, broadcast)`, `source.sendFailure(Component)`. Commands that need a player fail from RCON with "A player is required": give them an optional `targets` argument.
* **Payloads** (`core.net`): a record implementing `CustomPacketPayload` with `TYPE = new CustomPacketPayload.Type<>(id)` and a `StreamCodec` (`StreamCodec.composite(ByteBufCodecs.VAR_LONG, Rec::a, ..., Rec::new)`). Register in the common initialiser on BOTH sides: `PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC)` (S2C) and `serverboundPlay()` (C2S); the buffer type is `RegistryFriendlyByteBuf`. Server receiver: `ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, ctx) -> ...)` with `ctx.player()`, `ctx.server()`; client receiver: `ClientPlayNetworking.registerGlobalReceiver(TYPE, (payload, ctx) -> ...)` with `ctx.client()`, `ctx.player()`. Both handlers run on the game thread (no `execute()` needed). Send: `ServerPlayNetworking.send(serverPlayer, payload)`, `ClientPlayNetworking.send(payload)`. `registerLarge` exists for big payloads. Round trip is logged as `GODZILLA-NET server sent ping`, `client got ping`, `server got pong` (also sent automatically on join and by `/godzilla ping`; the key G sends an unsolicited pong with id 0).
* **Events**: `ServerLifecycleEvents.SERVER_STARTED`, `ServerPlayConnectionEvents.JOIN/DISCONNECT`, `ServerTickEvents.START_SERVER_TICK/END_SERVER_TICK/START_LEVEL_TICK/END_LEVEL_TICK` (END_SERVER_TICK takes `MinecraftServer`, the level variants take `ServerLevel`), `ClientTickEvents.END_CLIENT_TICK` (takes `Minecraft`; used in `GodzillaClient`). Also in the sources: `ServerEntityEvents`, `ServerChunkEvents`, `ClientPlayConnectionEvents`, `HudElementRegistry`, `LevelRenderEvents` (source only).
* **Key mappings** (`GodzillaClient`): `KeyMapping.Category cat = KeyMapping.Category.register(id)` (a record around an `Identifier`; the language key is `key.category.<ns>.<path>`), then `KeyMappingHelper.registerKeyMapping(new KeyMapping("key.godzilla.ping", InputConstants.KEY_G, cat))`, poll with `while (key.consumeClick())` in a client tick. Names go in `assets/godzilla/lang/en_us.json`. It registers and the game starts; the key press itself was not sent by the harness.
* **Mixins/access wideners**: none used yet. Loom wants `accessWidener` in `loom { }` and `fabric.mod.json`; Fabric ships transitive wideners so most things used by mods are already public (for example `EntityRenderers.register`).

## 5. Entity rendering (verified with a screenshot)

Pattern in `dev.godzilla.client`: `TestDummyModel` (geometry + `setupAnim(state)`), `TestDummyRenderer`, wiring in `GodzillaClient`.

1. `ModelLayerRegistry.registerModelLayer(new ModelLayerLocation(id, "main"), TestDummyModel::createBodyLayer)` (Fabric).
2. `EntityRenderers.register(entityType, TestDummyRenderer::new)` (vanilla method; Fabric's `EntityRendererRegistry` is deprecated).
3. Model: `class M extends EntityModel<S extends EntityRenderState>`; constructor takes the baked root `ModelPart` and calls `super(root)`; `createBodyLayer()` builds a `MeshDefinition`: `root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d), PartPose.offset(...))`, `LayerDefinition.create(mesh, texW, texH)`. Units are 1/16 block, **y points down, 24 is the ground line**. `setupAnim(state)` sets `xRot/yRot/zRot` of parts from state values (`state.ageInTicks`, `state.walkAnimationPos/Speed`, `state.xRot/yRot` are on `LivingEntityRenderState`).
4. Renderer: `class R extends MobRenderer<Entity, State, Model>` (or `LivingEntityRenderer`, or `EntityRenderer` for non-living) with `super(context, new Model(context.bakeLayer(LAYER)), shadowRadius)`, `getTextureLocation(state)` returning the texture `Identifier`, `createRenderState()` returning a `new State()`. Custom data: subclass `LivingEntityRenderState`, add public fields, fill them in `extractRenderState(entity, state, partialTicks)` (call `super` first). The renderer must not read the entity while drawing.
5. Texture at `assets/godzilla/textures/entity/<name>.png`; the test one is generated by `tools\make_test_texture.py`. Entities render with the standard offset `EntityModel.MODEL_Y_OFFSET = -1.501`.
6. Scaling (source only): `LivingEntityRenderer.extractRenderState` sets `state.scale = entity.getScale()` and `submit` applies `poseStack.scale(scale, scale, scale)`, so a living entity's model follows its scale attribute (and its hitbox does too). For anything else scale the pose stack in your own `submit`.

## 6. Blocks: setting them fast (verified, with timings)

`dev.godzilla.core.FastBlocks` and `/godzilla fill <mode> <from> <to> <block>` (prints blocks and time). Measured on the server thread on this machine (Ryzen 9800X3D), 1,048,576 blocks of air<->stone above the sea, one player tracking:

| Method | Cost |
|---|---|
| `level.setBlock(pos, state, Block.UPDATE_ALL)` (=3) | about 580-610 ns/block (0.16 s per 262k) |
| `level.setBlock(pos, state, Block.UPDATE_CLIENTS \| Block.UPDATE_SKIP_ALL_SIDEEFFECTS, 0)` (= 2 \| 816) | about 375-415 ns/block (0.4 s per 1M) |
| `LevelChunkSection.setBlockState(x, y, z, state, false)` straight into the sections (`chunk.getSection(chunk.getSectionIndexFromSectionY(sy))`, `acquire()`/`release()` around the loops), `Heightmap.primeHeightmaps`, `chunk.markUnsaved()`, then resend each chunk with `new ClientboundLevelChunkWithLightPacket(chunk, lightEngine, null, null)` to `level.getChunkSource().chunkMap.getPlayers(chunkPos, false)` | about 30-45 ns/block (35-45 ms per 1M) |
| the same plus `lightEngine.checkBlock(pos)` for every block | about 106-118 ns/block (queues the light work; the lighting itself is processed afterwards by the light thread) |

Flag bits (`net.minecraft.world.level.block.Block`): `UPDATE_NEIGHBORS 1`, `UPDATE_CLIENTS 2`, `UPDATE_INVISIBLE 4`, `UPDATE_IMMEDIATE 8`, `UPDATE_KNOWN_SHAPE 16` (skip shape updates), `UPDATE_SUPPRESS_DROPS 32`, `UPDATE_MOVE_BY_PISTON 64`, `UPDATE_SKIP_SHAPE_UPDATE_ON_WIRE 128`, `UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS 256`, `UPDATE_SKIP_ON_PLACE 512`; combos `UPDATE_NONE 260`, `UPDATE_ALL 3`, `UPDATE_ALL_IMMEDIATE 11`, `UPDATE_SKIP_ALL_SIDEEFFECTS 816`. The 4-argument `Level.setBlock(pos, state, flags, updateLimit)` is the real method (the 3-argument one is the default wrapper); an update limit of 0 stops the recursive neighbour shape updates.

Caveats: the section path does not run any block logic, does not fix the light, and block entities are not created (state only). Chunks must be loaded (`forceload add x1 z1 x2 z2` from RCON to test). For a city that is stamped once per run, the section writes plus one resend are far cheaper than anything per block. The lighting for the sections path was only checked by eye (a 81x27x81 white box looked right), not measured.
`ServerLevel.sendBlockUpdated` batches changes per chunk and broadcasts them at the end of the tick (`ServerChunkCache.blockChanged`, `ChunkHolder.broadcastChanges`).

## 7. The scale attribute

* `Attributes.SCALE` = `RangedAttribute("attribute.name.scale", 1.0, 0.0625, 16.0)`, syncable. **16 is the maximum**, so a player is at most 1.8 x 16 = 28.8 blocks tall with eyes at 1.62 x 16 = 25.9 (the design's "about 16" is the ceiling; there is no head-room). `AttributeInstance.setBaseValue` does NOT clamp the stored base value (`getValue()` and `LivingEntity.getScale()` do), so clamp yourself, as `/godzilla scale` does. Modifiers cannot push it past 16 either.
* `/godzilla scale <v> [targets]` sets it; from the console it needs targets. Verified over RCON: `godzilla scale 16 Tester` gives `getScale()` 16.0 (height 28.8, eye 25.92 expected); before the clamp was added, `setBaseValue(40)` reported a base value of 40.0.
* What it changes in vanilla: dimensions (hitbox, eye height, via `refreshDimensions()` when the attribute changes: the bounding box updates on the next tick, not in the same tick) and the passenger attachment. Source search shows it does NOT change walking speed, step height, jump strength, fall damage, or reach: those are separate attributes (defaults: `movement_speed` 0.7 on this version [range 0..1024], `step_height` 0.6 [0..10], `jump_strength` 0.42 [0..32], `gravity` 0.08 [-1..1], `safe_fall_distance` 3 [-1024..1024], `fall_damage_multiplier` 1 [0..100], `block_interaction_range` 4.5 and `entity_interaction_range` 3 [0..64]). A 29-block Godzilla needs these raised by modifiers (for example `AttributeModifier` with `ADD_MULTIPLIED_BASE`) or his legs get stuck on 1-block kerbs and his reach is 4.5 blocks.
* Vivecraft: see section 9 (it multiplies its world scale by `entity.getScale()`).
* The flat test client and RCON can set it on `Tester`; `godzilla pose` then shows camera-derived hand offsets that grow with the scale.

## 8. The real VR client folder (`game\`)

`python tools\setup_client.py` builds `D:\godzilla-vr\game` and never writes to `D:\genesis-vr`:
`mods\` = fabric-api, vivecraft, our jar (the jar is refreshed by `tools\build.ps1`); `resourcepacks\Patrix_26.3_32x_basic.zip`, enabled in `options.txt` (`resourcePacks:["vanilla","file/Patrix_26.3_32x_basic.zip"]`; view distance 24, simulation 16); `servers.dat` entry "Godzilla" at `localhost:25567`; `config\` and `openvr\` copied from Genesis's game folder only where missing (Vivecraft client config; `vrEnabled` is `"false"` there, VR is switched on from Vivecraft's own menu in the game, as in Genesis).
`--install-profile` adds the launcher profile "Godzilla VR" (key `godzilla-vr`, Fabric loader 0.19.5-26.3, gameDir `D:\godzilla-vr\game`, the JVM args of the "Genesis VR" profile, backup `launcher_profiles.json.before-godzilla`). Not run by the agent.
Server side, `server\config\vivecraft-server-config.toml` is created on first start with defaults. `tools\server.ps1` now sets `[teleport] enabled = false` (the Quest left trigger is teleport) and `[climbey] enabled = false` and keeps `[worldScale] limitRange = false` (see section 9); `vrFun = false` is still a suggestion.

## 9. Vivecraft 26.3-1.3.17

### Public API (`org.vivecraft.api`, safe to compile against)

| Class | What |
|---|---|
| `org.vivecraft.api.VRAPI` (`VRAPI.instance()`) | common side: `boolean isVRPlayer(Player)`, `VRPose getVRPose(Player)` (null if not VR), `VRPoseHistory getHistoricalVRPoses(Player)`. Works for a `ServerPlayer` and on the client (local player: pre-tick world pose) |
| `org.vivecraft.api.server.VRServerAPI` | `hasVivecraft(ServerPlayer)`, `getVivecraftVersion`, `sendHapticPulse(ServerPlayer, VRBodyPart, duration, frequency, amplitude, delay)` (7-argument overload: `duration` only, defaults 160 Hz, amplitude 1, delay 0; does nothing for non-VR players) |
| `org.vivecraft.api.client.VRClientAPI` | `isVRInitialized()`, `isVRActive()`, `getLatestRoomPose()`, `getPostTickRoomPose()`, `getPreTickWorldPose()`, `getPostTickWorldPose()`, `getWorldRenderPose()`, `getHistoricalVRPoses()`, `triggerHapticPulse(VRBodyPart, duration, frequency, amplitude, delay)` (amplitude must be 0..1 or it throws), `isSeated()`, `isLeftHanded()`, `getFBTMode()`, `float getWorldScale()` |
| `org.vivecraft.api.client.VRRenderingAPI` | `getHandRenderPos(InteractionHand)`, `setupRenderingAtHand(hand, PoseStack/Matrix4f)`, `getWorldRenderPose(Player)`, current render pass queries |
| `org.vivecraft.api.data.VRPose` | `getHead()`, `getMainHand()`, `getOffHand()`, `getHand(InteractionHand)`, `getBodyPartData(VRBodyPart)`, `isSeated()`, `isLeftHanded()`, `getFBTMode()` |
| `VRBodyPart` | `MAIN_HAND, OFF_HAND, RIGHT_FOOT, LEFT_FOOT, WAIST, RIGHT_KNEE, LEFT_KNEE, RIGHT_ELBOW, LEFT_ELBOW, HEAD`. Feet, waist etc. are only non-null with trackers (`FBTMode` ARMS_ONLY default) |
| `VRBodyPartData` | `Vec3 getPos()`, `Vec3 getDir()` (unit pointing direction = orientation x (0,0,-1)), `getPitch/Yaw/Roll()`, `Quaternionfc getRotation()` |
| `VRPoseHistory` | up to 200 ticks: `ticksOfHistory()`, `getHistoricalData(ticksBack)`, `netMovement(part, maxTicks)`, `averageVelocity(part, maxTicks)`, `averageSpeed`, `averagePosition` (all in world blocks per tick). This is the right tool for "the hand moved through blocks fast enough" |
| `org.vivecraft.api.client.event.VivecraftClientRegistrationEvent`, `InteractModule`, `Tracker` | client-side extension points (custom interaction modules/trackers); not used |
| `org.vivecraft.api_beta.*` | deprecated since 1.3.0; do not use |

Isolation used here: `dev.godzilla.vr.VivecraftBridge` (server/common) and `dev.godzilla.client.vr.VivecraftClientBridge` (client) are the only classes that import `org.vivecraft`; they implement `VrBackend` / `ClientVrBackend` (no Vivecraft types), are loaded with `Class.forName` only after `FabricLoader.isModLoaded("vivecraft")`, and are used through the facades `dev.godzilla.vr.Vr` and `dev.godzilla.client.vr.ClientVr` and the value type `PoseSnapshot` (head, main hand, off hand: position and direction, plus seated/left-handed). Verified three ways: server with Vivecraft (bridge instantiated), flat client without Vivecraft (starts, `vr=false`), flat client with Vivecraft (bridge calls `isVRActive()` and `getWorldScale()` = 1.0 without errors). Not verified: a real headset.

### Poses on the server

* The server only gets poses if it runs Vivecraft too (`SERVER_WANTS_DATA`): keep `vivecraft-*.jar` in `server\mods`.
* Each client tick the client sends a `VrPlayerState` with head and hand positions as offsets from the player's position; `ServerVivePlayer` adds the player position back. `VRAPI.instance().getVRPose(serverPlayer).getMainHand().getPos()` is therefore a **world-space position at 20 Hz** (one pose per client tick, a tick old by the time it is read; use the history for velocity). `Vr.pose(player)` in this repo returns that for VR players and a camera-derived approximation otherwise (hands at shoulder width, pointing along the look direction).
* `ServerVivePlayer` (internal, `ServerVRPlayers.getVivePlayer(player)`) also carries `worldScale`, `heightScale`, `crawling`, aim overrides; internal API, avoid.
* Main hand is the dominant hand (`isLeftHanded()` swaps it); `MAIN_HAND` = controller 0.
* Verified here only for the non-VR path: `/godzilla pose Tester` printed `flat(camera-derived)` poses with 16x-scaled offsets (in the very tick of a scale change the eye height is still the old one, because `refreshDimensions()` runs afterwards; read it a tick later).

### Poses and haptics on the client

* Tick logic: `VRClientAPI.instance().getPostTickWorldPose()` (poses after this tick's controller update, world space, scaled) or `getPreTickWorldPose()`.
* Per-frame rendering (debris, beam, the Godzilla body following the controllers): `getWorldRenderPose()` (interpolated for the current render frame) and `VRRenderingAPI.getHandRenderPos(hand)`. `getLatestRoomPose()` is room space, unscaled, relative to the play-space origin.
* `ClientVr.pose()` here returns the post-tick world pose in VR, otherwise the camera-derived one.
* Haptics: client `ClientVr.haptic(mainHand, seconds, frequency, amplitude)` (`VRClientAPI.triggerHapticPulse`; ignored when seated or not VR), server `Vr.haptic(player, ...)` (`VRServerAPI.sendHapticPulse`, an S2C packet). 160 Hz, amplitude 1 is the default feel.

### World scale: how Vivecraft follows the scale attribute (source: `client_vr/gameplay/VRPlayer.java`, `client/utils/ScaleHelper.java`)

Each tick `worldScale = Mth.clamp(userWorldScaleSetting * player.getScale(), 0.025, 100)`; `player.getScale()` is the vanilla scale attribute, so setting `Attributes.SCALE` to 16 makes Vivecraft's world scale 16 with the default setting 1.0 (`vivecraft-client-config.json`: `"worldScale": "1.0"`). Room-space positions are multiplied by it, so in the headset the world looks 16 times smaller and the controllers reach 16 times as far; `VRPlayer.postTick` moves the room origin so the head stays put while the scale changes. The server learns the scale through `WorldScalePayloadC2S`. Consequences:

* Everything the API returns in world coordinates is already scaled; do not multiply by the scale again. `VRClientAPI.getWorldScale()` gives the current factor.
* The user's own Vivecraft world-scale setting multiplies on top (a player who set 0.5 sees an 8x scale). Setting it from the mod is not exposed by the API.
* `[worldScale] limitRange` in the server config must stay `false` (default): when it is on and the actual scale is outside min..max the client disconnects the player.
* `applyPlayerWorldscale` (client setting, default false) only decides whether OTHER players' Vivecraft models are drawn at their world scale; the local player's own model is not drawn in first person, so the Godzilla body has to be our own renderer.
* The scale attribute max (16) is below Vivecraft's clamp (100), so the attribute is the limit.
* Not verified in a headset: comfort, the exact eye position at scale 16, and how Vivecraft's locomotion speed behaves at that scale (free-move uses the walk multiplier and the entity's movement speed; raise the `movement_speed` attribute and/or `walkMultiplier`).

### Controller input and key mappings

* Every `KeyMapping` in `Minecraft.options.keyMappings`, including ones registered through Fabric's `KeyMappingHelper`, becomes a Vivecraft/SteamVR action (`VRInputAction`) named `/actions/<set>/in/<key name>`. Vivecraft's own bindings go to the `ingame`/`gui`/`contextual`/... sets; **a mod's key mapping goes to the `/actions/mod` set** (`VRInputActionSet.fromKeyBinding`, `VivecraftVRMod.isModBinding`: not a vanilla binding and not `keyUse`). The action manifest is rewritten at every VR start (`openvr/input/action_manifest.json`).
* The default binding files (`openvr/input/oculus_defaults.json`, ...) contain **no entries for `/actions/mod`**. So a mod key has no controller button until the player binds it in SteamVR's binding UI (Vivecraft menu -> Controller bindings; SteamVR stores bindings per application key, `org.jrbudda.vivecraft.steamvrinput`, shared with Genesis and Angel Attack). Plan on the vanilla keys below for anything essential, and treat extra mod keys as optional or tell the player to bind them once.
* Default Quest/Touch bindings (`assets/vivecraft/input/oculus_defaults.json`, right-handed; `_reversed` for left-handed):

| Control | Action |
|---|---|
| right trigger click | `key.attack` (vanilla attack/mine) |
| right A click | `key.use` (vanilla use) |
| left trigger click | `vivecraft.key.teleport` (pull: `teleportfallback`) |
| left stick position | `vivecraft.key.freemovestrafe` (walking), left stick click: `key.jump` |
| right stick position | `vivecraft.key.rotateaxis` (turning), right stick click (toggle): `key.sneak` |
| left grip / right grip | `hotbarprev` / `hotbarnext` |
| right B | `radialmenu`; left Y click: in-game menu button (long: keyboard); left X: `key.inventory` |

  So in code: **trigger = `Minecraft.getInstance().options.keyAttack.isDown()`** and A = `keyUse`; grips are taken by the hotbar actions unless rebound. In VR the attack key is also driven by Vivecraft's swing tracking for melee; reading `isDown()` for the breath is fine.
* Verified: only that the mod's key mapping registers and the client starts. Binding behaviour in SteamVR is read from source.
