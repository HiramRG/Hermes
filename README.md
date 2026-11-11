<h1 align="center">Hermes</h1>

<p align="center"><b>A virtual Transfer Pak for Pokémon Stadium on the AYN Thor.</b></p>

Hermes puts your Pokémon Red, Blue or Yellow save into Pokémon Stadium, the way the N64 Transfer Pak did. Pick a cartridge on the top screen and press Play. Hermes starts Stadium in **M64Plus FZ** with that game "plugged in" and shows your trainer card on the bottom screen. When you exit, Hermes copies anything Stadium changed back into your Game Boy save. You don't need to know anything about emulator settings.

## What it does

* **Finds your games.** Point it at the folder(s) with your Game Boy ROMs and saves (`.sav` or `.srm`, e.g. RetroArch's saves folder). It recognises Red, Blue and Yellow (including the European translations) from the cartridge header and pairs each one with its save.
* **Trainer cards.** It reads each save and shows the trainer name, ID, badges, Pokédex, play time and party.
* **One-press play.** It inserts the cartridge into the Transfer Pak and starts Stadium on the top screen, always from a fresh boot, so Stadium sees the cartridge you picked.
* **Saves come home.** When Stadium is exited, the updated save is copied back to the original file (an emulator footer, if any, is kept). If the Game Boy game was also played elsewhere meanwhile, Hermes asks which version to keep.
* **Backups.** It keeps a copy before every session and before every write (the newest 15 per game), and can restore any of them.
* **Bottom screen.** It shows the setup steps while you're in M64Plus FZ, then the inserted cartridge and an "I'm done" button while you play. The bottom screen never takes the controller.
* **Safety checks.** It won't insert an empty or damaged save, never writes a damaged save over your original, and warns about region mismatches (Japanese carts only work with the Japanese Stadium).

## Install

1. Download **Hermes.apk** from the [latest release](../../releases/tag/latest) and install it on your Thor.
2. Install **M64Plus FZ** (free or Pro) from Google Play.
3. Open Hermes and follow the five steps. The last step is the only manual one: in M64Plus FZ, open Pokémon Stadium's **Settings → Controller**, set **Controller Paks → Player 1** to **Transfer Pak**, and pick Hermes' two files as **Player 1 Gameboy ROM** and **Player 1 Gameboy RAM**. The bottom screen walks you through it, and **Test it** confirms it works.

## How it works

| What | How |
|---|---|
| The Transfer Pak | M64Plus FZ already emulates it. Per game it stores a "Gameboy ROM" and "Gameboy RAM" file, copies them in when the game starts, and writes the RAM back when the game is exited (`CoreService` → `CoreInterface.writeGbRamData`). |
| Inserting a cartridge | M64Plus FZ keeps its per-game settings private, so Hermes uses one fixed pair of files (`hermes-cartridge.gb` / `.sav`) that M64Plus FZ is pointed at once. Hermes copies the chosen ROM and save into them. |
| Launching | A `VIEW` intent with the Stadium ROM to M64Plus FZ's `SplashActivity`, started on the top display. Games started this way boot fresh (no auto-resume), so Stadium reads the new cartridge. |
| Knowing it worked | When the slot save's modification time moves after a session, M64Plus FZ handed it back, which proves the link. |
| Copying back | Hermes compares hashes of the slot and original saves with what they were at insert time: nothing new, copy back, or conflict. Saves are only written if their Gen 1 checksum is valid. |
| Reading saves | Offsets from Bulbapedia's *Save data structure (Generation I)* (international layout). |
| File access | The Storage Access Framework (folder pickers). No storage permission or root. |

## Build

You need Android Studio (Ladybug or newer) or JDK 17 + Gradle 8.9+.

```
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

Every push builds the APKs on GitHub Actions; pushes to `main` publish them to the `latest` release. Signing: see [signing/README.md](signing/README.md).

## Project layout

```
core/       pure Kotlin (unit-tested): GB and N64 header readers, Gen 1 save reader, species table, sync decisions
platform/   Android glue: folder access, library scan, the Transfer Pak slot, backups, launching M64Plus FZ
ui/         Jetpack Compose: walkthrough, cartridge shelf, backups, settings, bottom screen
```

---

Hermes is an independent project, not affiliated with Nintendo, Game Freak, The Pokémon Company, AYN or the M64Plus FZ developers. Use backups of games you own.
