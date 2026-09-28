# Java 26.3 validation

Tested September 28, 2026: Lumen Java 1.0.0-beta.1, Minecraft 26.3,
Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Java 25, Windows x64.
Rendering backend used by the automated game test: OpenGL on NVIDIA hardware.

## Completed

- `gradlew build`: successful; seven JUnit tests pass with no failures.
- `gradlew runClientGameTest`: successful in a separate Fabric development
  instance. Its own test world was created, closed and reopened.
- All production mixins loaded in the actual game without missing-target errors.
- The game scan detected diamond ore, a chest, a trapped chest and an ender chest
  behind a stone wall. Captured images visibly show their four colored outlines.
- Disabling through-wall rendering hid those occluded outlines in the captured image.
- Fullbright visibly brightened a sealed dark stone room. Its lightmap state
  reverted on deactivation; Minecraft's stored brightness option was unchanged.
- Holding C reduced camera FOV; releasing it restored the baseline FOV. The
  stored FOV option remained unchanged.
- Insert opened, closed and reopened the menu; Escape returned to gameplay.
  Mouse capture was released/restored correctly, and the screen did not pause.
- A simulated mouse click changed the radius slider to 24. A chest filter
  changed via the UI, and the settings file reloaded with the same filter mask.
- Removing the known chest removed its marker. Leaving the world cleared
  markers; reopening the saved test world allowed ender-chest scanning again.
  X then disabled scanning and cleared markers.
- Actual game screenshots of the menu/filter tabs were inspected at 1280x720
  and 1280x900. Controls and labels fit without clipping.

The scan/settings tests cover invalid and extreme configuration values, exact
block identifiers, individual filters, double-chest block positions, nearest
ordering, read/marker/radius limits, removal/replacement, teleport and reset.

## Boundaries

These are automated development-client tests plus visual inspection of the
captured game frames. User acceptance in a normal installed Fabric profile is
still pending. Shader packs, Sodium/Iris, Vulkan, non-Windows platforms and
multiplayer compatibility were not tested. The first Java release remains a beta
pending installed-profile acceptance. Existing personal Minecraft worlds and
profiles were not modified.

The standalone Java repository preserves the tested gameplay source from Lumen
commit `08c46e9753654aee3eda1ab31c541b9eba610baa`. Repository links, documentation
and build automation were adapted for this repository; gameplay behavior did not change.

Game-test logs can include the host's existing performance-counter warnings and
the development account's unavailable Realms authentication. Those did not
prevent the test world from loading or the checks from passing.

## User acceptance

Install the beta and matching Fabric API in a Fabric 26.3 profile. In a local
test world, check Xray with your preferred ore/chest filters, C hold/release,
B on/off, Insert/Escape and mouse controls. Restart Minecraft to confirm saved
filters. Report the Lumen version and that profile's `logs/latest.log` if a
problem occurs. Do not install the development test mod or source JAR.
