# LeanFrame (Fabric, Minecraft Java 26.3)

A small FPS toolkit. It does **not** touch rendering, so Minecraft looks exactly like normal.

| Command | What it does |
|---|---|
| `/leanframe bench` | Measures your FPS for 20 seconds and saves the result to `config/leanframe-benchmarks.csv` |
| `/leanframe status` | Shows FPS, FPS limit, VSync, and which performance mods are installed |
| `/leanframe uncap` | Sets FPS limit to Unlimited and turns VSync off (your old values are saved) |
| `/leanframe restore` | Puts your old values back |

## What LeanFrame is NOT

It is not a new renderer. It will not give +200% FPS and it does not replace Sodium.
The big FPS gains come from **Sodium** (rendering) and **Lithium** (game logic). Install them too;
LeanFrame works next to them. Use `/leanframe bench` before and after to see the real difference
on *your* PC.

`uncap` only raises the number if your FPS was held back by the limit or by VSync.
Turning VSync off can cause screen tearing. Use `/leanframe restore` if you don't like it.

## Build it

Needs Java 25. This project has no Gradle wrapper file, so use one of these:

**A) Online, nothing to install**
1. Make a free GitHub account and a new repository.
2. Upload everything from this folder (keep the hidden `.github` folder).
3. Open the **Actions** tab, wait for the green check, open the run, download **leanframe-jar**.

**B) On your PC**
Install JDK 25 and Gradle 9.5.1, then run in this folder:

```
gradle build
```

The jar is `build/libs/leanframe-0.1.0.jar`.

## Install

1. Install Fabric Loader 0.19.5 or newer for Minecraft 26.3.
2. Put **Fabric API 0.161.0+26.3** (or newer) in the `mods` folder.
3. Put `leanframe-0.1.0.jar` in the `mods` folder (add Sodium and Lithium for 26.3 as well).

## If the build fails

Mojang and Fabric change names often. Open https://fabricmc.net/develop, choose 26.3, and copy the
current `loader_version`, `loom_version` and `fabric_version` into `gradle.properties`.
If it still fails, send the full error text and it can be fixed.

## Notes

- The code was checked on a test setup with stand-in classes, but not yet against the real 26.3 jars.
  The few Minecraft internals it needs (FPS limit, VSync, FPS counter) are looked up by name at
  runtime, so a Mojang rename shows a message in chat instead of crashing the game.
- License: MIT.
