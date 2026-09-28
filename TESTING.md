# Java 26.3 validation

Tested September 28, 2026: Lumen Java 1.0.0-beta.2, Minecraft 26.3,
Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Java 25, Windows x64.
Rendering backend used by the automated game test: OpenGL on NVIDIA hardware.

## Completed

- `gradlew build`: successful; ten JUnit tests pass with no failures.
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

## Iris regression

The original through-wall rendering used vanilla always-on-top gizmos. Iris
retains world depth for shader composition, which leaves those outlines hidden
by terrain. The upstream report is [Iris issue 3297](https://github.com/IrisShaders/Iris/issues/3297).
Beta 2 uses one GUI batch for through-wall outlines after world shader composition.
The batch belongs to the current frame and receives the exact world projection
during rendering, including zoom, camera bobbing and hurt/portal transformations.
Normal depth-tested outlines still use vanilla gizmos. There is no Iris dependency
or shader modification in the production mod.

The full client game test also passed with Iris **1.11.6+mc26.3**, Sodium
**0.9.3-alpha.1+mc26.3**, and **Complementary Reimagined r5.9.3** at default shader
settings. Fixtures were read from local files and copied only into the disposable
test instance; no personal Prism profile, mod or world was changed.

- The test checks the Iris API to confirm the shader pack is actually active.
- Screenshot pixels confirm all four marker colors behind the sealed stone wall.
- Xray off, the markers behind the camera, and through-walls off each produce
  no marker pixels in the same controlled scene.
- Disabling shaders and re-enabling them while in the world retains all four
  outlines. The active/inactive shader state is checked after each reload.
- Screenshots confirm the zoomed outline follows the enlarged world view and
  that the Lumen menu draws above the outlines.
- The existing filter, zoom release, lightmap restoration, mouse/menu, slider,
  persistence, block removal and world re-entry checks also pass with Iris.

The three projection tests cover viewport clipping, near-plane crossings,
geometry behind the camera, both graphics depth ranges, reversed depth,
non-finite input and optical zoom. Screenshot color checks account for the
vanilla HUD vignette and integer color rounding.

The scan/settings tests cover invalid and extreme configuration values, exact
block identifiers, individual filters, double-chest block positions, nearest
ordering, read/marker/radius limits, removal/replacement, teleport and reset.

## Boundaries

These are automated development-client tests plus visual inspection of the
captured game frames. User acceptance in a normal installed Fabric profile is
still pending for beta 2. Other shader packs, the rest of the user's Prism modpack
(including Distant Horizons and Freecam), Vulkan, non-Windows platforms and
multiplayer compatibility were not tested. The Java release remains a beta.
Fullbright's lightmap state restores correctly with Iris, but a shader pack's
own lighting can ignore that setting; shader Fullbright is not claimed here.
Personal Minecraft worlds and profiles were not modified.

Game-test logs can include the host's existing performance-counter warnings and
the development account's unavailable Realms authentication. Those did not
prevent the test world from loading or the checks from passing.

## User acceptance

Install the beta and matching Fabric API in a Fabric 26.3 profile. In a local
test world, check Xray with your preferred ore/chest filters, C hold/release,
B on/off, Insert/Escape and mouse controls. Restart Minecraft to confirm saved
filters. Report the Lumen version and that profile's `logs/latest.log` if a
problem occurs. Do not install the development test mod or source JAR.
