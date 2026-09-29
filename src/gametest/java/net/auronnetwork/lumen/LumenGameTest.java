// SPDX-License-Identifier: GPL-3.0-only
package net.auronnetwork.lumen;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;

/** Runs only in Gradle's isolated test instance; never included in the shipped mod. */
public final class LumenGameTest implements FabricClientGameTest {
    private static void check(boolean value, String description) {
        if (!value) throw new AssertionError(description);
    }
    private static final BlockKind[] TEST_MARKERS = {BlockKind.DIAMOND, BlockKind.CHEST, BlockKind.TRAPPED_CHEST, BlockKind.ENDER_CHEST};
    private static void checkMarkerImage(java.nio.file.Path path, boolean visible) {
        try {
            var image = javax.imageio.ImageIO.read(path.toFile());
            for (var kind : TEST_MARKERS) {
                int count = 0;
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                    if (sameMarkerColor(image.getRGB(x, y), kind.color)) count++;
                check(visible ? count >= 100 : count == 0, path.getFileName() + ": " + kind + " rendered pixels=" + count);
            }
        } catch (java.io.IOException e) { throw new AssertionError("Cannot inspect screenshot", e); }
    }
    private static boolean sameMarkerColor(int pixel, int color) {
        // The vanilla HUD vignette dims peripheral markers after our overlay. Compare
        // normalized color, with a brightness floor and small quantization tolerance.
        int brightness = Math.max((pixel >>> 16) & 255, Math.max((pixel >>> 8) & 255, pixel & 255));
        if (brightness < 80) return false;
        for (int shift = 0; shift <= 16; shift += 8)
            if (Math.abs(((pixel >>> shift) & 255) * 255.0 / brightness - ((color >>> shift) & 255)) > 3) return false;
        return true;
    }
    private static void setShaders(ClientGameTestContext context, boolean enabled) {
        context.runOnClient(mc -> {
            try {
                Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
                Object config = iris.getMethod("getIrisConfig").invoke(null);
                config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, enabled);
                config.getClass().getMethod("save").invoke(config);
                iris.getMethod("reload").invoke(null);
            } catch (ReflectiveOperationException e) { throw new AssertionError("Cannot toggle test shaders", e); }
        });
        context.waitTicks(10);
        context.runOnClient(mc -> {
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Object iris = api.getMethod("getInstance").invoke(null);
                check((boolean) api.getMethod("isShaderPackInUse").invoke(iris) == enabled, "Shader toggle reached renderer");
            } catch (ReflectiveOperationException e) { throw new AssertionError("Iris test API unavailable", e); }
        });
    }
    @Override public void runTest(ClientGameTestContext context) {
        if (FabricLoader.getInstance().isModLoaded("iris")) {
            context.runOnClient(mc -> {
                try {
                    // The test runner clears its run directory. Install the read-only fixture
                    // here, then configure only this disposable test instance.
                    var pack = java.nio.file.Path.of(System.getProperty("lumen.test.shaderPack"));
                    var target = FabricLoader.getInstance().getGameDir().resolve("shaderpacks").resolve(pack.getFileName());
                    java.nio.file.Files.createDirectories(target.getParent());
                    java.nio.file.Files.copy(pack, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
                    Object config = iris.getMethod("getIrisConfig").invoke(null);
                    config.getClass().getMethod("setShaderPackName", String.class).invoke(config, pack.getFileName().toString());
                    config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, true);
                    config.getClass().getMethod("save").invoke(config);
                    iris.getMethod("reload").invoke(null);
                } catch (Exception e) { throw new AssertionError("Cannot configure isolated Iris fixture", e); }
            });
        }
        TestWorldSave save;
        net.minecraft.client.player.LocalPlayer previousPlayer;
        try (var world = context.worldBuilder().create()) {
            save = world.getWorldSave();
            var server = world.getServer();
            server.runCommand("fill -12 100 -12 12 107 12 minecraft:stone");
            server.runCommand("fill -11 101 -11 11 106 11 minecraft:air");
            server.runCommand("fill -7 101 3 7 105 3 minecraft:stone");
            server.runCommand("setblock -3 101 6 minecraft:chest");
            server.runCommand("setblock 3 101 6 minecraft:trapped_chest");
            server.runCommand("setblock 6 101 6 minecraft:ender_chest");
            server.runCommand("setblock 0 101 6 minecraft:diamond_ore");
            server.runCommand("tp @a 0.5 101 0.5 0 0");
            world.getConnection().waitForChunksRender();
            if (FabricLoader.getInstance().isModLoaded("iris")) context.runOnClient(mc -> {
                try {
                    Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    Object iris = api.getMethod("getInstance").invoke(null);
                    check((boolean) api.getMethod("isShaderPackInUse").invoke(iris), "Iris test requires an active shader pack");
                    LumenClient.LOG.info("LUMEN_IRIS_SHADER_ACTIVE");
                } catch (ReflectiveOperationException e) { throw new AssertionError("Iris test API unavailable", e); }
            });
            context.getInput().lookAt(new BlockPos(0, 102, 6));
            context.runOnClient(mc -> {
                LumenClient.settings = new LumenSettings();
                LumenClient.settings.filters = BlockKind.DIAMOND.bit() | BlockKind.CHEST.bit() | BlockKind.TRAPPED_CHEST.bit() | BlockKind.ENDER_CHEST.bit();
                LumenClient.settings.xray = true;
            });
            context.waitFor(mc -> LumenClient.markerCount() == 4, 300);
            context.waitTicks(5);
            checkMarkerImage(context.takeScreenshot("lumen-xray-through-walls"), true);
            context.getInput().pressKey(LumenClient.xrayKey);
            context.waitTicks(5);
            checkMarkerImage(context.takeScreenshot("lumen-xray-disabled"), false);
            context.getInput().pressKey(LumenClient.xrayKey);
            context.waitFor(mc -> LumenClient.markerCount() == 4, 300);
            context.getInput().lookAt(new BlockPos(0, 102, -6));
            context.waitTicks(5);
            checkMarkerImage(context.takeScreenshot("lumen-markers-behind-camera"), false);
            context.getInput().lookAt(new BlockPos(0, 102, 6));
            context.waitTicks(5);
            if (FabricLoader.getInstance().isModLoaded("iris")) {
                setShaders(context, false);
                checkMarkerImage(context.takeScreenshot("lumen-iris-shaders-off"), true);
                setShaders(context, true);
                checkMarkerImage(context.takeScreenshot("lumen-iris-shaders-reenabled"), true);
            }
            context.runOnClient(mc -> LumenClient.settings.throughWalls = false);
            context.waitTicks(5);
            checkMarkerImage(context.takeScreenshot("lumen-xray-depth-tested"), false);
            double gamma = context.computeOnClient(mc -> mc.options.gamma().get());
            int fov = context.computeOnClient(mc -> mc.options.fov().get());
            context.getInput().pressKey(LumenClient.brightnessKey);
            context.waitTicks(5);
            context.runOnClient(mc -> {
                check(LumenClient.settings.fullbright, "B toggles Fullbright");
                check(mc.gameRenderer.gameRenderState().lightmapRenderState.ambientColor.x() == 1f, "Fullbright reaches lightmap");
            });
            context.takeScreenshot("lumen-fullbright");
            context.getInput().pressKey(LumenClient.brightnessKey);
            context.waitTicks(5);
            context.runOnClient(mc -> {
                check(!LumenClient.settings.fullbright, "B restores Fullbright");
                check(mc.gameRenderer.gameRenderState().lightmapRenderState.ambientColor.x() < 1f, "Vanilla lightmap restored");
            });
            float normal = context.computeOnClient(mc -> mc.gameRenderer.mainCamera().getFov());
            context.runOnClient(mc -> LumenClient.settings.throughWalls = true);
            context.getInput().holdKey(LumenClient.zoomKey);
            context.waitTicks(5);
            context.runOnClient(mc -> check(mc.gameRenderer.mainCamera().getFov() < normal / 2, "C zooms camera"));
            context.takeScreenshot("lumen-xray-zoom");
            context.getInput().releaseKey(LumenClient.zoomKey);
            context.waitTicks(5);
            context.runOnClient(mc -> {
                check(Math.abs(mc.gameRenderer.mainCamera().getFov() - normal) < 0.01f, "C release restores FOV");
                check(mc.options.gamma().get() == gamma && mc.options.fov().get() == fov, "Original game settings unchanged");
            });
            context.getInput().pressKey(LumenClient.menuKey);
            context.waitForScreen(LumenScreen.class);
            context.runOnClient(mc -> {
                check(!mc.gui.screen().isPauseScreen(), "Menu does not pause");
                check(!mc.mouseHandler.isMouseGrabbed(), "Menu releases cursor");
            });
            context.getInput().resizeWindow(1280, 720);
            context.waitTicks(5);
            context.takeScreenshot("lumen-menu-1280x720");
            LumenCombatTest.run(context, server);
            double[] point = context.computeOnClient(mc -> {
                var slider = mc.gui.screen().children().stream().filter(w -> w instanceof AbstractSliderButton)
                        .map(w -> (AbstractSliderButton)w).findFirst().orElseThrow();
                double scale = mc.getWindow().getGuiScale();
                return new double[] {(slider.getX() + slider.getWidth() - 4) * scale, (slider.getY() + 8) * scale};
            });
            context.getInput().setCursorPos(point[0], point[1]);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(2);
            context.runOnClient(mc -> check(LumenClient.settings.radius == 24, "Mouse changes radius slider"));
            context.clickScreenButton("Ore Filters");
            context.getInput().resizeWindow(1280, 900);
            context.waitTicks(5);
            context.takeScreenshot("lumen-ore-filters-1280x900");
            context.clickScreenButton("Features");
            context.waitTicks(3);
            context.takeScreenshot("lumen-features-1280x900");
            context.clickScreenButton("Chest Filters");
            context.waitTicks(3);
            context.takeScreenshot("lumen-chest-filters");
            context.clickScreenButton("+ Chests");
            context.runOnClient(mc -> check(!LumenClient.settings.selected(BlockKind.CHEST), "Chest UI filter responds"));
            context.getInput().pressKey(LumenClient.menuKey);
            context.waitFor(mc -> mc.gui.screen() == null);
            context.getInput().pressKey(LumenClient.menuKey);
            context.waitForScreen(LumenScreen.class);
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitFor(mc -> mc.gui.screen() == null);
            context.runOnClient(mc -> {
                check(mc.mouseHandler.isMouseGrabbed(), "Escape restores cursor capture");
                LumenClient.save();
                try {
                    var restored = LumenSettings.load(FabricLoader.getInstance().getConfigDir().resolve("lumen.properties"));
                    check(restored.filters == LumenClient.settings.filters, "Filters persist to disk");
                    check(restored.noAttackCooldown, "Combat preference remains saved");
                } catch (java.io.IOException e) { throw new AssertionError("Settings reload failed", e); }
                LumenClient.settings.filters = BlockKind.CHEST.bit();
            });
            context.waitFor(mc -> LumenClient.markerCount() == 1, 300);
            server.runCommand("setblock -3 101 6 minecraft:air");
            context.waitFor(mc -> LumenClient.markerCount() == 0, 100);
            previousPlayer = context.computeOnClient(mc -> mc.player);
            context.runOnClient(mc -> check(AttackCooldown.appliesTo(mc.player), "Combat still enabled before disconnect"));
            context.runOnClient(mc -> LumenClient.settings.xray = false);
        }
        context.waitTicks(3);
        context.runOnClient(mc -> {
            check(LumenClient.markerCount() == 0, "Leaving world clears markers");
            check(!AttackCooldown.available(mc), "No combat context without a world");
            check(!AttackCooldown.appliesTo(previousPlayer), "Disconnect clears the previous player's combat context");
        });
        try (var reopened = save.open()) {
            reopened.getConnection().waitForChunksRender();
            context.waitFor(mc -> AttackCooldown.appliesTo(mc.player), 100);
            context.runOnClient(mc -> check(!AttackCooldown.appliesTo(previousPlayer), "New world does not reuse old client player"));
            reopened.getServer().runOnServer(s -> {
                var player = s.getPlayerList().getPlayers().getFirst();
                player.resetAttackStrengthTicker();
                check(player.getAttackStrengthScale(0) == 1, "Combat setting applies to reopened local world");
            });
            context.runOnClient(mc -> { LumenClient.settings.noAttackCooldown = false; LumenClient.save(); });
            context.runOnClient(mc -> { LumenClient.settings.xray = true; LumenClient.settings.filters = BlockKind.ENDER_CHEST.bit(); });
            context.waitFor(mc -> LumenClient.markerCount() == 1, 300);
            context.getInput().pressKey(LumenClient.xrayKey);
            context.waitFor(mc -> !LumenClient.settings.xray && LumenClient.markerCount() == 0, 100);
        }
        LumenClient.LOG.info("LUMEN_JAVA_GAME_TEST_OK: rendered marker pixels, camera clipping, scan, removed chest, filters, zoom restore, brightness restore, menu/cursor/slider, persistence, world re-entry");
    }
}
