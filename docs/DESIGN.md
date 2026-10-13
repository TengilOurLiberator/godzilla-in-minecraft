# Godzilla VR: design

A Minecraft VR arcade game. You are Godzilla. You rise out of Tokyo Bay, walk ashore and wreck the city with
your hands, feet, tail and atomic breath for about three minutes, then Mothra arrives for a boss fight that
ends the game, win or lose.

## Decisions (from the player)

| Area | Decision |
|---|---|
| Platform | Minecraft 26.3, Fabric loader 0.19.5, Fabric API 0.162.0+26.3, Vivecraft 26.3-1.3.17, Meta Quest 3 through SteamVR |
| Machine | Ryzen 7 9800X3D, RTX 5090, 32 GB. Push debris and entity counts as high as stays smooth in VR |
| Build | A Fabric mod (Java), not a datapack |
| Look | Patrix 26.3 32x basic resource pack; the city's block palette is chosen for how Patrix draws it |
| Tone | Gleeful arcade: score, combos, big explosions, bright daylight |
| Scale | To the player Tokyo is a miniature. Technically the player is scaled up (see Scale) |
| Body | The whole Godzilla is visible: arms and claws on the controllers, chest, legs, feet, tail, dorsal plates, shadow |
| Movement | Thumbstick walking |
| Hands | Swinging a hand through blocks sends them flying |
| Collapse | Buildings fall when they no longer have enough support |
| Atomic breath | Hold the trigger; the beam leaves the mouth along the head's look direction |
| People | Run, scream, and can die |
| Cars | Drive, and explode |
| Military | Tanks, jets, helicopters, warships, maser cannons. They cannot hurt you (flashes, shake, noise only) |
| Mothra | Arrives at about 3:00. A real boss fight; she is the only thing that can kill you |
| Ending | The game ends after the Mothra fight, either way, with the score |
| City | The same Tokyo every run: generated, with hand-designed landmarks |
| Sound | Music and roar. No narration, no announcer voice |

Nothing is sampled from the films: the roar, Mothra's cry and the score are original and synthesised.

## Scale

The player has the vanilla `scale` attribute set to about 16, so Godzilla is about 29 blocks tall with eyes near
26 blocks. One block is roughly one storey (about 3.5 m). Ordinary buildings are 5 to 25 blocks tall (ankle to
chest), a few towers reach head height, Tokyo Tower stands well above the head. Vivecraft follows the scale
attribute, so in the headset the world looks like a tabletop city. The exact value is a tunable (`GodzillaConfig`).

People are about 0.5 block tall, cars about 1.2 blocks long, tanks about 2, jets about 3, warships 12 to 20,
Mothra's wingspan about 40.

## World layout

- Sea surface: water fills up to and including y=62. City ground blocks top out at y=63 (you stand at y=64).
- Open bay floor: top solid block at y=28, so a 29-block Godzilla standing there is fully under water.
- The world is a flat world: bedrock at y=-64, stone y=-63..28, water y=29..62, everywhere. The city volume is
  stamped into it from the city file. Beyond the city the sea runs to the horizon.
- The player starts on the bay floor facing the city, about 120 blocks off the waterfront. The floor slopes up
  to the shore.
- Server port 25567, RCON 25577 (Genesis uses 25565, Angel Attack 25566).

## City file format

The city generator (Python, `city/`) writes two files that the mod loads. The mod stamps them into the world at
the start of every run, which is also how the city is reset.

`tokyo.gzc`, gzip-compressed, little-endian:

```
bytes  4   magic "GZC1"
int32  3   sizeX, sizeY, sizeZ
int32  3   originX, originY, originZ      world position of the cell (0,0,0)
uint16 1   paletteCount
repeat paletteCount: uint16 byteLength, UTF-8 block state string
                     e.g. "minecraft:air", "minecraft:quartz_stairs[facing=north,half=top]"
uint16 sizeX*sizeY*sizeZ   palette index per cell, order: y outermost, then z, then x innermost
```

Palette index 0 is always `minecraft:air`. Water inside the volume is written explicitly.

`tokyo.json`:

```
{
  "seaLevel": 62, "groundY": 63,
  "origin": [x,y,z], "size": [x,y,z],
  "playerStart": {"pos": [x,y,z], "yaw": deg},
  "buildings": [{"id": int, "kind": "office|apartment|shop|house|tower|landmark|industrial|station|bridge",
                 "name": "Tokyo Tower" (landmarks only), "min": [x,y,z], "max": [x,y,z] (inclusive),
                 "blocks": solid block count, "value": score}],
  "roads": {"nodes": [[x,y,z], ...], "edges": [[a,b,width], ...]},   surface driving graph
  "rails": [[[x,y,z], ...], ...],                                    polyline per line
  "sidewalkSpawns": [[x,y,z], ...],   where people appear
  "tankSpawns": [...], "maserSpawns": [...], "shipSpawns": [[x,y,z,yaw], ...],
  "airLanes": [[[x,y,z], ...], ...],  approach paths for jets and helicopters
  "mothraArrival": {"from": [x,y,z], "to": [x,y,z]}
}
```

Every solid city block above ground belongs to exactly one building box (boxes do not overlap), so the mod can
tell which building a broken block was part of.

## Architecture

One mod, `godzilla`, with a common (server) side and a client side, played on a local dedicated server like
Genesis and Angel Attack (so it can also be driven and screenshotted over RCON by a flat test client).
The server is authoritative for blocks, entities, score and the game timeline. The client does the body, debris
visuals, beam visuals, HUD and haptics.

Java packages under `dev.godzilla`:

| Package | Owns |
|---|---|
| `core` | Mod entrypoints, registries, config, networking payloads, the shared API below |
| `city` | Loading `tokyo.gzc`/`tokyo.json`, fast stamping and reset, building index |
| `destruction` | Smashing (hands, feet, body, tail, beam, explosions), debris, structural collapse, rubble |
| `player` | Scale and attributes, VR pose reading, hand and foot tracking, atomic breath, roar, intro rise, Godzilla health |
| `client` | Godzilla body model and renderer, debris and beam rendering, HUD, score popups, haptics |
| `population` | Civilians and civilian vehicles |
| `military` | Tanks, jets, helicopters, warships, maser cannons and their projectiles |
| `mothra` | Mothra entity, model, fight AI |
| `game` | Game director: phases, timers, spawning budgets, score and combos, win and lose, reset |

Desktop (non-VR) fallback, needed for automated testing: the head is the camera, the right hand swings on attack
click, the breath is a held key.

## Run timeline

1. **Rise** (about 15 s): black water, the player is carried up and forward until the head breaks the surface;
   Tokyo ahead; title; roar; warships open fire. Then control is given.
2. **Rampage** (to 3:00): walk ashore, wreck. Military presence escalates: ships, then tanks and helicopters,
   then jets, then masers.
3. **Mothra** (from 3:00): her cry, the music changes, she arrives. Both sides have health bars.
4. **End**: Mothra falls into the city, or Godzilla falls. Score breakdown. Then a restart resets the city.

## Destruction rules

- A hand is a sphere a few blocks wide following the controller. Blocks it passes through at speed are removed
  and thrown along the hand's velocity. Slow contact pushes less.
- Feet crush what they land on; the legs and torso plough through walls with some drag; the tail sweeps when
  the body turns.
- The beam bores a tunnel, sets things alight and detonates vehicles.
- Collapse: per building, after damage, (a) anything no longer connected to the ground falls, and (b) when the
  load-bearing cross-section of a storey drops below a threshold of its original, everything above comes down,
  crushing the storeys below in turn. Falling masses are animated, then become rubble mounds, dust and score.
- Debris is visual and client-side wherever possible so that thousands of pieces can fly.
