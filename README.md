# LeanFrame (Fabric, Minecraft Java 26.3)

A vanilla-look FPS helper. It does not touch rendering, so it works next to Sodium and Lithium.
Ready-to-use jar: `LeanFrame-0.3.0-mc26.3.jar` (no building needed).

## What it does

* **First start only:** applies the *Vanilla-look* preset. It does **not** change how the game looks:
  the only change is simulation distance 12 -> 8 (invisible CPU work). A short chat message explains it.
* **/leanframe doctor** checks your RAM, Java memory, settings and speed mods and says what to fix.
* **/leanframe bench** measures FPS for 20 seconds and saves it. **/leanframe compare** shows the change
  between your last two runs, for example before and after adding Sodium.
* Optional presets: **/leanframe lowend** (shorter distances, almost the same look),
  **/leanframe potato** (max FPS, simpler graphics), **/leanframe uncap** (no FPS limit, VSync off).
* **/leanframe restore** puts ALL your original settings back. A preset never makes a setting more expensive.

Commands need Fabric API. Without it the first-start preset still works; to undo it set
`restoreOnNextStart=true` in `config/leanframe.properties`.

## Where the big FPS gains really come from

LeanFrame cannot replace a new renderer. Real, look-neutral gains come from Sodium, Lithium, FerriteCore,
Entity Culling and ImmediatelyFast (all have Fabric builds for 26.3). The Windows "Fast Pack" installs them.

## Config (`config/leanframe.properties`)

| Key | Meaning |
|---|---|
| `firstRunPreset` | `vanilla` (default), `lowend`, `potato` or `off`. Applied once. |
| `firstRunDone` | Set to true automatically. |
| `restoreOnNextStart` | `true` = put the original settings back at the next start. |

## Honest notes

* Built without internet access and tested in a simulated game, not in a real Minecraft 26.3.
  Everything in Minecraft is reached by name at runtime, so if a name is different the matching item is
  skipped (shown in chat and in `logs/latest.log`) instead of crashing the game.
* It does not connect to the internet. It only changes Minecraft's own settings and writes files in `config/`.

## Build from source (optional)

Needs JDK 25 and Gradle 9.5.1: run `gradle build`. If a version is rejected, copy the current ones from
https://fabricmc.net/develop into `gradle.properties`. License: MIT.
