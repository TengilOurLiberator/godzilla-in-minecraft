# Godzilla VR

A Minecraft VR arcade game. You are a 29-block Godzilla. You rise out of Tokyo Bay, wreck a miniature Tokyo with your hands, feet, tail
and atomic breath for three minutes while the army fights back (it cannot hurt you), then Mothra arrives for a boss fight. The run ends
with a score screen whether you win or lose. It is a Fabric mod for Minecraft 26.3 played on a local dedicated server, with Vivecraft
(Quest 3 through SteamVR). Everything (city, sounds, music, textures) is generated here; nothing is taken from the films.

## First-time setup (once)

1. Build once and prepare the VR client folder: `D:\voice-assistant\.venv\Scripts\python.exe -I tools\setup_client.py` (fills `game\`:
   fabric-api, Vivecraft, the mod jar, Patrix resource pack, options, a "Godzilla" server entry). Close the Minecraft launcher, then add
   the launcher profile "Godzilla VR" with the one command that touches the launcher:
   `D:\voice-assistant\.venv\Scripts\python.exe -I tools\setup_client.py --install-profile`
2. In SteamVR's controller bindings nothing needs changing: the defaults are used (below).

## Each time

1. Start SteamVR.
2. `.\start.ps1` (builds the mod if the jar is out of date, opens the server in its own window, waits until it answers).
   `-NewWorld` wipes the world, `-Offline` is only for the flat test client.
3. Minecraft launcher, profile "Godzilla VR", Play. Multiplayer, "Godzilla".

You arrive at normal size on a pier above the bay, facing Tokyo, with a "SMASH ME" sign hanging in front of you. Hit it with a hand,
pull the right trigger at it, or walk into it. The city is restored, you stand on the sea floor, and the 19 s rise begins: you surface with the title and a roar, then control is yours.

## Controls (Quest 3 default bindings)

| Control | Action |
|---|---|
| left stick | walk |
| right stick | turn |
| hands | swing through blocks, cars, tanks, Mothra: fast swings throw things, slow contact barely pushes |
| right trigger (hold) | atomic breath along your head direction (about 0.6 s wind-up, 6 s of fuel, 10 s to refill) |
| right A | roar (people panic, units flinch); also starts a new run on the results screen |
| walk into things | feet and body plough through buildings, the tail sweeps when you turn |

Score comes from blocks, buildings (they collapse and crush what is below), landmarks, vehicles, people, military units and Mothra hits,
with a combo multiplier that decays after a second of quiet. Results screen: breakdown, rank, best score (`server\godzilla-world\data\godzilla_best.json`).
After the results, smash the sign again to play again; the city is restored and everything is reset.

## Tuning

* `config\godzilla.properties` (created on first start, in the server folder and in the game folder): debris on screen (client),
  debris pieces per tick, number of people, cars, police, trains (server), military scale. Turn the debris cap up first if the headset
  has headroom. Restart after editing.
* In code: `PlayerConfig` (walk speed 10 blocks/s, breath fuel, hand thresholds, reach), `GameImpl` (rank scores, combo levels, Mothra damage
  scale), `Mothra` (hit points, damages, circle speed), `military.Director` (waves), `Lobby` (the pier).
* Server commands (need op or the console): `/godzilla start|stop|status|skip|phase|results`, `/godzilla mothra spawn|hp|kill`,
  `/godzilla destruction stats`, `/godzilla pop stats`, `/godzilla mil stats`. `/godzilla skip` jumps to just before Mothra.

## Not tested in a headset

Everything was driven and screenshotted in a flat desktop client. Check these first, in this order:

1. Comfort of the rise (a scripted 19 s carry), of walking at 16x scale, and of the quay climb (he heaves himself up the sea wall at the
   shore: a fast lift of about 10 blocks).
2. The hands: swing speed thresholds and the hand radius. Do swings throw debris the way your arm moves? Do you hit the start sign?
3. Controls: trigger = atomic breath, A = roar, and that the left stick walking speed feels right (`PlayerConfig.WALK_BLOCKS_PER_SECOND`).
4. Where the HUD sits (score and bars at top centre, breath gauge at bottom centre) and whether the score popups are readable.
5. Frame rate in the headset with a collapse and 20 tanks on screen; lower `debris.max` in the game folder's config if it dips.
6. The first-person body: you see chest and shoulders when you look down; the head is hidden.
7. Sound: roar, Mothra and the music were never listened to, only checked in logs; people and car sound rates are limited but not heard.

## Licence and what is not included

The mod's code, generated city, audio and textures are this project's. Minecraft, Fabric (loader and API), Vivecraft and the Patrix
resource pack are not included and not covered by this project's licence: get them from their authors (the tools expect the jars in
`downloads\`). Not affiliated with Mojang, Toho or any rights holder.
