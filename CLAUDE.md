# Godzilla VR

A Minecraft 26.3 Fabric mod: a VR arcade game where the player is Godzilla wrecking a miniature Tokyo, ending in
a Mothra boss fight. The design and the file formats are in `docs/DESIGN.md`; read it before changing anything.

## Layout

| Path | What it is |
|---|---|
| `mod/` | The Fabric mod (Gradle, Java 25). Packages under `dev.godzilla`, one owner area each (see the design) |
| `city/` | Python city generator; writes `city/out/tokyo.gzc` and `city/out/tokyo.json` |
| `audio/` | Python sound and music synthesis; writes `audio/out/` (ogg files and a `sounds.json`) |
| `tools/` | Build, deploy, server, RCON and test-client scripts |
| `docs/` | Design, and notes on the 26.3 and Vivecraft APIs as they were found in the jars |
| `server/`, `game/`, `downloads/`, `jdk/`, `work/` | Local, not in git |

## Rules

- Minecraft 26.3 is unobfuscated and newer than most documentation. Do not write API calls from memory: look the
  class up in the Minecraft, Fabric API or Vivecraft jars first. Record what you learn in `docs/API-NOTES.md`.
- Python is `D:\voice-assistant\.venv\Scripts\python.exe` (run it with `-I`). There is no `python` on PATH.
- `D:\genesis-vr` and `D:\angel-attack` are sibling projects to learn from. Read them; never write to them.
- Nothing may be sampled or copied from the Godzilla films: sounds, music and textures are made here.
- The player cannot be tested in VR by an agent. Everything must also work in a flat desktop client so it can
  be driven over RCON and screenshotted.
