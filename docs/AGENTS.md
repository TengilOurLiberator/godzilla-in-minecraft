# Working on a module

Each gameplay module is built by its own agent in its own git worktree, then merged into `main`.

## Set up (once)

```
git -C D:\godzilla-vr worktree add D:\godzilla-vr-wt\<module> -b <module>
cd D:\godzilla-vr-wt\<module>
powershell tools\dev.ps1 instance <N>          # your instance number, given in your brief
```

From then on work only inside your worktree. `powershell tools\dev.ps1 up | rebuild | down | rcon "<cmd>" ... | shot <name> |
status | log` builds your code and runs your own server and flat test client (player `Tester<N>`); screenshots land in
`D:\godzilla-vr\work\inst<N>\shots`. See `docs\API-NOTES.md` section 1. Windows, PowerShell 5.1 (no `&&`). Python is
`D:\voice-assistant\.venv\Scripts\python.exe -I`.

## Rules

- Read `CLAUDE.md`, `docs\DESIGN.md`, `docs\API-NOTES.md` and everything in `mod\src\main\java\dev\godzilla\api` first.
  API-NOTES is verified knowledge about this Minecraft version; trust it over memory, and look classes up in the
  decompiled sources (`D:\godzilla-vr\work\mcsrc`, `work\fabricsrc`, `work\vivesrc`) before using them.
- Own only your packages: `dev.godzilla.<module>` (common/server, `mod\src\main`) and `dev.godzilla.client.<module>`
  (client, `mod\src\client`), plus resources in folders named for your module (`assets/godzilla/textures/<module>/`...).
  Start from the stub `<Module>Module.init()` and `<Module>Client.init()`; they are already called. Register your own
  entity types, payloads, key mappings and `/godzilla` subcommands (`GodzillaCommands.addSubcommands`) from there.
- Do not edit `dev.godzilla.api`, `dev.godzilla.core`, `GodzillaMod`, `GodzillaClient`, `build.gradle` or `tools\`. If you
  truly need an API change, make the smallest backward-compatible addition and list it first in your report.
- Shared files you may only append lines to: `assets/godzilla/lang/en_us.json` (prefer literal text over language keys),
  `fabric.mod.json` (mixin config or access widener entries; name your mixin config `godzilla.<module>.mixins.json`).
- Other modules are not in your worktree yet. Reach them only through `Gz.destruction()`, `Gz.city()`, `Gz.game()`,
  `Gz.fx()` and `GzEvents`; until they are merged these are simple stand-ins (`dev.godzilla.core.dev`), driven with
  `/godzilla dev phase RAMPAGE Tester<N>`, `/godzilla smash`, `/godzilla explode`. Never cache a service in a static field.
- Entities that can be hit implement `Smashable`. Discard everything of yours on `GzEvents.RESET`.
- Sounds are all registered in `GodzillaSounds`; durations and loop lengths are in `audio\out\manifest.json`.
- Textures and other generated assets are made by a Python script you keep under `tools\<module>\` and can re-run.
  Nothing is copied from the films or from other mods.
- The player is 16x scale: about 29 blocks tall, eyes near y+26, about 10 blocks wide. One block is one storey. Size,
  speed, sound range (set generous attenuation/volume) and effects must read from 30 to 150 blocks away and from above.
- Everything must work in the flat test client, driven over RCON, because that is the only way you can see it. Give
  every behaviour a command to trigger it. Look at your screenshots; iterate until it looks right, not until it compiles.
- VR-only paths cannot be run by you: write them carefully from API-NOTES section 9 and say what is unverified.
- Performance matters (VR at 90 fps, as much destruction as possible): measure server tick time (`/tick query`) and client
  frame rate under load and report the numbers.
- Memory is shared with other agents' servers and clients: run `dev.ps1 down` whenever you are not actively testing.
  Stop only your own instance. Leave no java processes behind.
- Commit to your branch as often as you like. When told that `main` has moved, `git merge main` in your worktree.
- Record API facts you had to discover in a new file `docs\notes-<module>.md` (not in API-NOTES.md, to avoid conflicts).

## Report

What works, with the screenshots that show it (paths); commands to try it; numbers measured; API additions; what is
unverified (especially VR); what you would do next.
