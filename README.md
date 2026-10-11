# Canyon Salvo

A turn-based missile duel built with [libGDX](https://libgdx.com/). You and the enemy face each other across a canyon.
Each turn, both sides place a missile launcher and a flare dispenser, then fire at the same time. Guess where the
enemy will strike, and don't be predictable: the enemy learns your habits.

Runs on **desktop** (Windows, macOS, Linux) and in the **browser**, with an **Android** build as well.
Licensed under the [Apache License 2.0](#license).

## How to play

1. Tap a launch pad to place your **missile launcher**. Your missile flies straight up that column.
2. Tap a launch pad to place your **flare dispenser**. It guards that column.
3. Both sides fire at once:
   - If a missile comes down a column guarded by flares, the dispenser releases its flares and the missile is
     decoyed and destroyed.
   - Otherwise the missile hits the base for **20 damage**.
4. Both sides start with **100 health**, shown in the bars at the top. The first side to run out loses. If both run
   out on the same turn, it's a draw.

The enemy plays fair. It never sees your choices for the current turn, but it remembers where you placed your
launcher and flares in earlier turns, so it guards the columns you favour and fires where you rarely defend.

| Action                 | Desktop / Web          | Android     |
|------------------------|------------------------|-------------|
| Place launcher / flares | Click a launch pad     | Tap a pad   |
| Back to main menu      | `Esc`                  | Back button |
| Quit                   | **Quit** in the main menu | Back from the main menu |

## Requirements

- **JDK 17 or newer** to run Gradle and the desktop build.
- **Android SDK** (platform 36) only if you build the Android app. Point Gradle at it with a `local.properties`
  file in the project root containing `sdk.dir=/path/to/Android/Sdk`, or set `ANDROID_HOME`.

The Gradle wrapper downloads Gradle 9.8.1 on first use, so no separate Gradle install is needed. On Windows,
use `gradlew.bat` instead of `./gradlew`.

## Building and running

| Platform | Command | Output |
|----------|---------|--------|
| Desktop: run | `./gradlew desktop:run` | Opens the game window |
| Desktop: runnable JAR | `./gradlew desktop:jar` | `desktop/build/libs/CanyonSalvo-1.0.0.jar` |
| Web: build | `./gradlew html:dist` | `html/build/dist/` |
| Web: live development | `./gradlew html:superDev` | Starts a dev server on port 8080 that recompiles on browser refresh |
| Android: debug APK | `./gradlew android:assembleDebug` | `android/build/outputs/apk/debug/` |
| Android: launch on device | `./gradlew android:installDebug android:run` | Installs and starts the app |

Run the JAR with `java -jar CanyonSalvo-1.0.0.jar`.

The web build in `html/build/dist/` is a static site: serve it with any HTTP server. Opening `index.html`
directly from disk won't work, because browsers block the asset loading.

## Project structure

```
core/       Game code shared by every platform (package com.solaraslight)
desktop/    LWJGL3 desktop launcher
html/       GWT web launcher and web page (html/webapp)
android/    Android launcher, manifest and resources
assets/     Runtime assets used by all platforms
raw/        Source files that are not shipped: fonts, original artwork, retired sprites
```

The main classes in `core/src/main/java/com/solaraslight/`:

| Class | Purpose |
|-------|---------|
| `CanyonSalvo` | Entry point. Loads assets and particle pools and provides shared drawing helpers. |
| `MainMenuScreen` | Play, How to Play and Quit; also shows the result of the last game. |
| `HowToPlayScreen` | The rules. |
| `GameScreen` | Turns, missiles, flares, explosions and the health bars. |
| `EnemyAI` | Chooses the enemy's columns by learning from the player's earlier turns. |
| `MenuButton` | A clickable button drawn with the game's button skin. |
| `Constants` | Board size, damage, starting health and baked font sizes. |

## Assets

- **Sprites:** `launcher.png` and `missile.png` are cut from `raw/sprites/missile_system_sheet.png`. The enemy uses
  the same sprites flipped to face down.
- **Background:** `battlefield.jpg` is a compressed crop of `raw/sprites/battlefield_source_v3.png`, zoomed so the
  canyon fills the middle of the screen. The game scales it to cover any screen size without distortion.
- **Particle effects** (`assets/particles/*.p`):
  - `flares.p`: the flare burst when a missile is intercepted.
  - `exhaust.p`: the missile's flame and smoke trail.
  - `explosion.p`: impacts.

  These are standard libGDX particle files. Open them in the libGDX Particle Editor to tweak them. They were designed
  for a 600 px tall screen and are scaled at runtime.
- **Fonts:** FreeType isn't available in the browser, so the game uses pre-rendered bitmap fonts with a black
  outline, generated from the TTFs in `raw/fonts/`:
  - `title_font.fnt`: [Luckiest Guy](https://github.com/google/fonts/tree/main/apache/luckiestguy) at 96 px,
    letters, digits and basic punctuation only.
  - `ui_font.fnt`: [Russo One](https://github.com/google/fonts/tree/main/ofl/russoone) at 32 px.

  If you regenerate them at a different size, update `FONT_BAKED_SIZE` and `TITLE_FONT_BAKED_SIZE` in `Constants`.
  The fonts' license texts are in `assets/licenses/`, so they ship inside every build. Keep them there.

## Tuning

- **Damage, health, number of columns:** `Constants`.
- **Enemy difficulty:** the `MEMORY` and `CONFIDENCE` constants at the top of `EnemyAI`. Lower `MEMORY` makes it
  adapt faster; higher `CONFIDENCE` makes it trust what it has learned more, and so play harder.
- **Missile speed, interception distance and flare timing:** the constants at the top of `GameScreen`.

## Troubleshooting

- **`PKIX path building failed` when Gradle downloads dependencies:** antivirus HTTPS scanning (for example Avast
  Web Shield) or a corporate proxy is re-signing traffic with a certificate Java doesn't trust. On Windows, tell Java
  to use the Windows certificate store:

  ```bat
  set JAVA_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT
  gradlew.bat desktop:run "-Dorg.gradle.jvmargs=-Xmx2G -Djavax.net.ssl.trustStoreType=Windows-ROOT"
  ```

- **Window size:** the desktop window and the web canvas are a fixed 480×600, because the board layout is
  calculated once at startup.

## Tech stack

libGDX 1.14.2 · LWJGL3 3.4.3 · GWT 2.11.0 · Gradle 9.8.1 · Android Gradle Plugin 9.4.1 (compile and target SDK 36,
min SDK 21) · Java 17 for desktop, Java 11 for shared and web code.

## License

Copyright 2021-2026 Solaras Light

Licensed under the **Apache License, Version 2.0**. You may not use this project except in compliance with the
License. You may obtain a copy of the License in [LICENSE](LICENSE) or at
<https://www.apache.org/licenses/LICENSE-2.0>.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
an **"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND**, either express or implied. See the License for
the specific language governing permissions and limitations under the License.

If you redistribute the game or a modified version, keep the [NOTICE](NOTICE) file with it, as section 4(d) of the
License requires.

```
SPDX-License-Identifier: Apache-2.0
```

### Third-party components

| Component | License | Notes |
|-----------|---------|-------|
| [libGDX](https://github.com/libgdx/libgdx), [GWT](https://www.gwtproject.org/) | Apache 2.0 | |
| [LWJGL](https://www.lwjgl.org/) | BSD-3-Clause | |
| Luckiest Guy font (title), © 2010 Brian J. Bonislawsky DBA Astigmatic (AOETI) | Apache 2.0 | "Luckiest Guy" is a trademark of Astigmatic. |
| Russo One font (interface), © 2011-2012 Jovanny Lemonad, Reserved Font Name "Russo" | SIL Open Font License 1.1 | The bitmap version (`ui_font`) is a Modified Version under the OFL: it stays under the OFL, is not sold on its own, and doesn't use the reserved name. |

Both fonts' license texts and notices are in [`assets/licenses/`](assets/licenses/FONTS.txt), which is packaged with
every build.

Unless you state otherwise, any contribution you submit is licensed under the Apache License 2.0, as described in
section 5 of the License.
