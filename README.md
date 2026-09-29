<img src="src/main/resources/assets/lumen/icon.png" width="80" height="80" alt="Lumen — Auron Network">

# Lumen for Minecraft Java 26.3

A client-only Fabric edition of Lumen by Auron Network. English interface, dark
translucent panels and yellow accents. Current Java test build:
**1.0.0-beta.3**; Java and Bedrock use separate binaries and version numbers.

[Download the Java beta](https://github.com/AuronNetwork/Lumen-Java/releases/tag/v1.0.0-beta.3)
 · [Report an issue](https://github.com/AuronNetwork/Lumen-Java/issues)
 · [Test coverage and limits](TESTING.md)
 · [Bedrock edition](https://github.com/AuronNetwork/Lumen)

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) **0.19.5 or newer**
   for **Minecraft Java 26.3** and select that profile in your launcher.
2. Place `lumen-java-1.0.0-beta.3.jar` and
   [Fabric API for 26.3](https://modrinth.com/mod/fabric-api/versions?g=26.3)
   in that profile's `mods` folder. This build uses Fabric API **0.161.0+26.3**.
   For the standard launcher the default folder is `%APPDATA%\.minecraft\mods`.
3. Start the Fabric profile, enter a world and press **Insert**.

Minecraft 26.3 requires Java 25; the official launcher supplies its game runtime.
Install this mod on the client only. No Lumen EXE, DLL, Latite or server mod is
required. Remove older Lumen Java JARs from the same profile before updating.

In Prism Launcher, right-click the instance, choose **Folder**, and use its
`minecraft/mods` folder. Close that instance before replacing the Lumen JAR.

### Iris shader compatibility

Beta 2 fixes through-wall markers disappearing with Iris shader packs. Ore and
chest outlines are drawn after shader composition, with the current world camera
projection, and remain below menus and the HUD. No extra compatibility mod or
shader setting is required. Turning **Through walls** off retains normal depth
occlusion. F1 hides the through-wall marker overlay together with the HUD.

Verified with Iris **1.11.6**, Sodium **0.9.3-alpha.1**, and Complementary Reimagined
**r5.9.3** on Minecraft 26.3, including toggling shaders off/on during play.
Other shader packs and rendering mods need separate testing. Shader packs can
provide their own lighting, so Fullbright's lightmap setting may not brighten
their rendered scene.

## Controls and features

| Default key | Action |
| --- | --- |
| Insert | Open or close Lumen |
| Escape in Lumen | Close the menu |
| X | Toggle Xray markers |
| Hold C | Zoom; release to restore normal view |
| B | Toggle Fullbright |

Keys are rebindable in Minecraft's Controls / Key Binds / Lumen category.
The menu releases the cursor, consumes its own clicks and does not pause the world.
The **Features**, **Ore Filters** and **Chest Filters** tabs provide:

- Xray with a 4-24 block radius and 32-512 nearest markers.
- Ten ore categories, including deepslate variants, nether gold, quartz and ancient debris.
- Normal chests, trapped chests and ender chests as independent filters.
- Optional visibility through walls. Double chests have two marked block positions.
- 2x-20x hold-to-zoom and local Fullbright.
- **No Attack Cooldown**: optional full melee attack strength in singleplayer.

Fresh settings start with Xray, Fullbright and No Attack Cooldown off, Zoom enabled, a 16-block radius,
192 markers, 4x zoom, selected valuable ores and all chest filters off.
Settings are saved in the profile's `config/lumen.properties`.
Bedrock's INI settings are not automatically imported.

Only already loaded client block data is inspected. The mod does not load extra
chunks, send commands, request operator permissions or reveal container contents.
Server-hidden or substituted block data cannot be recovered by this mod.
Zoom and Fullbright modify the rendered view, without overwriting Minecraft's
FOV/brightness options or adding potion effects. Modpack compatibility and
additional rendering backends require separate testing.

### No Attack Cooldown

Open **Insert → Features → No Attack Cooldown** to turn the feature on or off.
While enabled in a private singleplayer world, your melee attacks use full
recharge strength immediately, including on the integrated server that calculates
damage. Item minimum-attack-charge checks also respect the setting. This is a
gameplay change, not just a hidden cooldown indicator.

The setting is saved and defaults to **OFF**, including when upgrading an older
configuration. It affects your own player only and is automatically inactive on
remote servers and worlds opened to multiplayer/LAN. It cannot remove the attack
cooldown enforced by another server.

Turning it off immediately restores vanilla charge calculations. Weapon damage
and attack-speed attributes, player timers and world save data are not overwritten.
Target damage immunity, the missed-swing click delay, item-use cooldowns and attack
animations retain their normal behavior. Rapidly hitting the same target can still
be limited by that target's normal damage immunity.

## Build and test

Use a JDK 25 installation and the included Gradle wrapper:

```powershell
.\gradlew.bat build
.\gradlew.bat runClientGameTest
```

`build` compiles the mod, runs the scanner/settings unit tests and produces the
mod JAR plus source JAR in `build/libs`. The second command launches a separate
development client and creates a disposable local test world under `build/run`.
It is not part of the distributed mod. Do not point its run directory at a
personal Minecraft instance. The tests cover scanning, UI controls, saved filters,
zoom restoration, Fullbright, actual singleplayer melee damage and cooldown
restoration, and capture screenshots for visual inspection.

To run the optional Iris compatibility test, supply local Iris/Sodium JARs and
a shader ZIP (these are test fixtures, not bundled or downloaded by Lumen):

```powershell
.\gradlew.bat runClientGameTest "-PshaderTestMods=C:\Fixtures\mods" "-PshaderTestPack=C:\Fixtures\ComplementaryReimagined_r5.9.3.zip"
```

The test copies the shader into its disposable instance. It checks actual marker
pixels with the shader active, disabled and re-enabled, and verifies that hidden
markers and markers behind the camera disappear.

The build pins Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3,
Fabric Loom 1.17.21 and Gradle 9.6.0. The Gradle distribution is SHA-256 checked.

Download Java updates from this repository's Releases page and replace the JAR
while Minecraft is closed. There is no in-game automatic updater.

GitHub Actions builds and runs the unit tests on pushes and pull requests.
The workflow retains the JAR, complete source ZIP and checksums as build artifacts;
public versions are published separately on the Releases page.

License: GPL-3.0-only. See `LICENSE` and `NOTICE.md`.
