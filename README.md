# Brawl Arena

Top-down arena battler for Android — Brawl Stars-inspired, fully original.
Offline, no ads, no accounts. Showdown mode: you + 5 bots, last one standing wins.

- **Package:** `com.shavlegi.brawlarena`
- **Stack:** Kotlin 2.0.21 + Jetpack Compose, pure Canvas rendering
- **Languages:** Georgian (default), English, Russian
- **Theme:** black / dark blue / white

## How to play

- **Left joystick** (drag anywhere on the left side) — move.
- **FIRE button** (hold) — shoot. Aiming is automatic: it targets the nearest
  visible enemy in range. If nobody is visible, you shoot in the direction
  you are facing.
- **SUPER button** — fires when the charge is full (100%). Charge builds as
  you deal damage.
- **Blue cubes** — pick them up: +400 max HP and +8% damage each.
- **Bushes** — hide inside: enemies cannot see or target you (unless they are
  in the same bush or very close).
- **Walls** — block movement, bullets and sight. The Grenadier's bombs fly
  OVER walls.
- **Poison** — after 2:30 the whole arena takes damage, so matches always end.

## Brawlers

| Brawler | HP | Weapon | Super |
|---|---|---|---|
| **გრომი / Grom** | 5200 | Shotgun — 5 pellets, short range, huge up close | 12-pellet blast |
| **მონადირე / Hunter** | 3600 | Rifle — one fast, heavy bullet, long range | Piercing sniper shot |
| **ყუმბარა / Grenadier** | 4300 | Lobber — arcing bombs over walls, area damage | Big area bomb |

## Trophies

Saved locally on the device: #1 +10, #2 +6, #3 +3, #4 +0, #5 −2, #6 −4.
Total never goes below 0.

## Build

Every push to `main` builds a debug APK in GitHub Actions
(`Build debug APK` workflow). Pushing a tag like `v1.0` also creates a
GitHub Release with the APK attached.

Local build (needs Android SDK 34 + JDK 17):

```sh
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Project layout

- `app/src/main/java/com/shavlegi/brawlarena/` — `MainActivity`, `TrophyStore`
  (DataStore), `LocaleHelper`
- `app/src/main/java/com/shavlegi/brawlarena/game/` — `Brawlers` (stats),
  `GameEngine` (pure-Kotlin game logic + bot AI), `GameScreen` (Canvas
  renderer + controls), `MenuScreen`, `ResultScreen`
- `app/src/main/res/values{,-en,-ru}/strings.xml` — KA / EN / RU texts
- `plan.md` — architecture, stats table, bot AI rules

## v1 limits (honest)

- Bots only — no online multiplayer (that needs a game server).
- No sound effects yet (planned for v1.1).
- One mode (Showdown), 3 brawlers.
