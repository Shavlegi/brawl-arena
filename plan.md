# Brawl Arena — plan.md

Top-down arena battler Android app, Brawl Stars-inspired but fully original.
Offline, no ads, no accounts. Kotlin + Jetpack Compose, pure Canvas rendering.
Build: GitHub Actions (no Android SDK on the dev VM — the Actions build is the
verification step).

## Tech (mirrors the proven Fake GPS pipeline)
- Kotlin 2.0.21, AGP 8.5.2, Gradle 8.7, minSdk 26, targetSdk 34, compileSdk 34
- Compose BOM 2024.09.00, Material 3, DataStore Preferences
- Package `com.shavlegi.brawlarena`, repo https://github.com/Shavlegi/brawl-arena
- Dark theme: black `#0B0F19` / dark blue `#121A2B` / white, accent blue `#4C8DFF`
- Languages: Georgian (default) + EN + RU via Android string resources,
  switched at runtime with AppCompatDelegate (same LocaleHelper pattern as Fake GPS)
- Landscape orientation (arena game)

## Architecture
- `MainActivity.kt` — screen state machine: Menu → Game → Result; owns the
  `GameEngine` instance and trophy updates.
- `TrophyStore.kt` — DataStore: total trophies (Int), selected brawler (String),
  language (String).
- `LocaleHelper.kt` — `applyLanguage(code)` via AppCompatDelegate.
- `ui/theme/Theme.kt` — `BrawlArenaTheme`, dark color scheme.
- `game/Brawlers.kt` — 3 `BrawlerDef` records + trophy table.
- `game/GameEngine.kt` — pure-Kotlin game logic, no Android dependencies:
  world state, fixed-dt update, movement + wall collision, projectiles,
  cubes, bot AI, visibility (bushes + line of sight), super charge,
  poison endgame, win/lose detection.
- `game/GameScreen.kt` — Compose Canvas renderer + joystick + FIRE/SUPER
  buttons + HUD + the `withFrameNanos` game loop.
- `game/MenuScreen.kt` — title, trophies, brawler select, language, how-to.
- `game/ResultScreen.kt` — victory/defeat, placement, kills, trophy delta.

## Game rules (v1)
- Mode: Showdown — player + 5 bots, 6 fighters, last one standing wins.
- Arena: 1700 × 1100 world units. Camera follows the player.
- Walls block movement, bullets and line of sight. Lobber bombs fly OVER walls.
- Bushes hide a fighter inside: enemies cannot see or target him unless they
  are in the same bush or within 90 units.
- Power cubes: 6 at start, respawn up to 8 (one per 6 s). Pickup: +400 max HP
  (and +400 HP heal) and +8% damage per cube. On death a fighter drops half
  his cubes (rounded up) around the death spot.
- Super charge: 0–100, +1 per 25 damage dealt. SUPER button fires when full.
- Poison: after 150 s all fighters take damage (120/s, +4/s per second, cap 400/s).
- Trophies: #1 +10, #2 +6, #3 +3, #4 +0, #5 −2, #6 −4. Total never below 0.

## Brawler stats

| Brawler | HP | Speed | Range | Damage | Cooldown | Weapon | Super |
|---|---|---|---|---|---|---|---|
| გრომი (Shotgun) | 5200 | 250 | 280 | 430 × 5 pellets, 40° spread | 850 ms | short-range spread | 12 pellets × 500, 60° spread, range 320 |
| მონადირე (Rifle) | 3600 | 270 | 720 | 1150 single bullet | 1100 ms | long-range fast bullet | piercing bullet 2600, range 1000 |
| ყუმბარა (Lobber) | 4300 | 240 | 560 | 1300 AoE r=130, 550 ms flight | 1300 ms | arcing bomb over walls | big bomb 2600 AoE r=240, 700 ms flight |

- Projectile speeds: shotgun pellet 950 u/s, rifle bullet 1400 u/s, rifle super 1600 u/s.
- Fighter radius 26. Bullet radii: pellet 6, bullet 8, super bullet 12.

## Bot AI (decision tick every 150 ms)
- Find nearest VISIBLE enemy (bush + wall line-of-sight rules apply).
- HP < 30% and enemy within 350 → FLEE (move away from enemy).
- Enemy visible and in range → ATTACK: move to preferred distance
  (shotgun ~180, rifle ~550, lobber ~380), strafe sideways, fire when ready.
- Enemy visible but out of range → CHASE.
- No visible enemy and a cube within 500 → go pick it up.
- Otherwise WANDER to a random free point.
- Super: fire when charge is full and an enemy is visible within range × 1.2.
- Bots never see through bushes and never shoot invisible enemies.

## Controls
- Left side: floating virtual joystick (drag anywhere on the left 45%).
- Right side: FIRE button (hold to keep shooting, auto-aim at nearest visible
  enemy in range; otherwise shoots in facing direction).
- SUPER button above FIRE (enabled at 100% charge).
- HUD: fighters alive, match timer, cube count, player HP bar, super bar,
  poison warning after 150 s.

## Rendering
- Single `Canvas`, camera centered on the player, zoom fits ~1000 × 620 world
  units on screen. Draw order: ground grid → bushes → cubes → walls →
  projectiles → fighters (with HP bar + name) → lobber target markers.
- Game loop: `LaunchedEffect` + `withFrameNanos`, dt clamped to 50 ms,
  `mutableLongStateOf` frame counter drives recomposition. No allocations
  of note inside the loop (projectile lists reused via removeAll).

## Files
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`,
  `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `.gitignore`
- `app/build.gradle.kts`, `app/proguard-rules.pro`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/shavlegi/brawlarena/{MainActivity,LocaleHelper,TrophyStore}.kt`
- `app/src/main/java/com/shavlegi/brawlarena/ui/theme/Theme.kt`
- `app/src/main/java/com/shavlegi/brawlarena/game/{Brawlers,GameEngine,GameScreen,MenuScreen,ResultScreen}.kt`
- `app/src/main/res/values{,-en,-ru}/strings.xml`, `values/themes.xml`,
  `drawable/ic_launcher_{background,foreground}.xml`,
  `mipmap-anydpi-v26/ic_launcher{,_round}.xml`
- `.github/workflows/android.yml` — assembleDebug on push; Release with APK on tag `v*`
- `README.md`

## Out of scope for v1 (later)
- Online multiplayer / real-time server (needs a game server, ~$5/mo VPS).
- Sound effects (ToneGenerator beeps — trivial to add later, skipped to keep v1 build-safe).
- More brawlers / modes (Gem Grab, Bounty), skins, friends.
- Pause on app background (match just keeps running; matches last ~2–3 min).

## Verification notes
- No Android SDK on this VM: no local compile. Code verified by careful
  review: every referenced symbol / string resource / drawable exists,
  brace balance checked per file, Kotlin + Compose API usage matches the
  versions pinned in `app/build.gradle.kts`.
- First real verification = GitHub Actions run after push.
