# Java 26.2 validation

Tested September 29, 2026: Lumen Java 1.0.0-beta.3-mc26.2, Minecraft 26.2,
Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, Java 25, Windows x64.
Rendering backend used by the automated game test: OpenGL on NVIDIA hardware.

## Version-specific adaptation

This separate branch targets Minecraft 26.2, retaining the 26.3 release on main.
Key bindings use the 26.2 GLFW `KEYSYM` type. Render pipelines and the projection
mixin use the 26.2 Blaze3D classes. The marker callbacks run during vanilla
world/HUD extraction; projection data is cleared each frame and collected only
when the world is rendered. Production gameplay features retain the beta 3 behavior.

## Completed

- `gradlew build`: successful; eleven JUnit tests pass with no failures.
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

## No Attack Cooldown

The client game test enables/disables the feature through the actual **Features**
screen. Damage is measured on the integrated server using an unenchanted iron axe
against fresh test pigs. These are live game entities in a disposable test world,
not mocked damage calculations.

| State | Measured damage (health points) |
| --- | ---: |
| Vanilla, fully charged | 9.0 |
| Vanilla, immediate uncharged attack | 1.8036451 |
| Option ON, first attack after resetting charge | 9.0 |
| Option ON, immediate repeat on a fresh target | 9.0 |
| Option OFF, uncharged attack restored | 1.8036451 |

- The client attack-strength indicator and server calculation both report full
  charge when enabled and return to the vanilla calculation immediately when disabled.
- Item minimum-attack-charge checks follow ON/OFF on both sides.
- Attack-speed and weapon-damage attributes retain their original values.
- A second immediate hit on the same target still respects target damage immunity.
- Another server-side player object retains normal charge calculations.
- The new setting defaults OFF for new, old and malformed config files; both
  values round-trip, and the real UI writes the enabled setting to disk.
- Disconnect invalidates the old player context. Reopening the test world binds
  the saved preference to the new player; the old client player remains inactive.
- The feature menu and ON/OFF screenshots were inspected at 1280x720 and 1280x900.

Production activation is limited to the current local player and that player's
private integrated-server session. The code checks the server identity and
multiplayer-publication state; remote servers and multiplayer/LAN worlds are not
supported. The tests did not open a network listener or connect to a public server.
No player timer or weapon attribute is overwritten, so disabling removes the
calculation override without changing persisted world data.

## Iris regression

The original through-wall rendering used vanilla always-on-top gizmos. Iris
retains world depth for shader composition, which leaves those outlines hidden
by terrain. The upstream report is [Iris issue 3297](https://github.com/IrisShaders/Iris/issues/3297).
Since beta 2, Lumen uses one GUI batch for through-wall outlines after world shader composition.
The batch belongs to the current frame and receives the exact world projection
during rendering, including zoom, camera bobbing and hurt/portal transformations.
Normal depth-tested outlines still use vanilla gizmos. There is no Iris dependency
or shader modification in the production mod.

The full client game test also passed with Iris **1.11.4+mc26.2**, Sodium
**0.9.2+mc26.2**, and **Complementary Reimagined r5.9.3** at default shader
settings. The official Iris/Sodium 26.2 downloads were SHA-512 verified against their
published Modrinth metadata. The shader was read from the local Prism profile
and copied only into the disposable test instance; no personal Prism profile, mod or world was changed.

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
- The beta 3 integrated-server combat damage and restoration checks also pass
  with this shader configuration.

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
still pending for this 26.2 backport. Other shader packs, the rest of the user's Prism modpack
(including Distant Horizons and Freecam), Vulkan, non-Windows platforms and
multiplayer compatibility were not tested. The Java release remains a beta.
Fullbright's lightmap state restores correctly with Iris, but a shader pack's
own lighting can ignore that setting; shader Fullbright is not claimed here.
Personal Minecraft worlds and profiles were not modified.

Game-test logs can include the host's existing performance-counter warnings and
the development account's unavailable Realms authentication. Those did not
prevent the test world from loading or the checks from passing.

## User acceptance

Install the beta and matching Fabric API in a Fabric 26.2 profile. In a local
test world, check Xray with your preferred ore/chest filters, C hold/release,
B on/off, Insert/Escape and mouse controls. Restart Minecraft to confirm saved
filters. Toggle **Features → No Attack Cooldown** in singleplayer and compare
rapid attacks on separate targets, then turn it off to restore normal charging.
Repeated hits on one target still respect its damage immunity.
Report the Lumen version and that profile's `logs/latest.log` if a
problem occurs. Do not install the development test mod or source JAR.
