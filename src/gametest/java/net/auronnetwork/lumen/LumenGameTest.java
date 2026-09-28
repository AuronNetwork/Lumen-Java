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
    @Override public void runTest(ClientGameTestContext context) {
        TestWorldSave save;
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
            context.getInput().lookAt(new BlockPos(0, 102, 6));
            context.runOnClient(mc -> {
                LumenClient.settings = new LumenSettings();
                LumenClient.settings.filters = BlockKind.DIAMOND.bit() | BlockKind.CHEST.bit() | BlockKind.TRAPPED_CHEST.bit() | BlockKind.ENDER_CHEST.bit();
                LumenClient.settings.xray = true;
            });
            context.waitFor(mc -> LumenClient.markerCount() == 4, 300);
            context.waitTicks(5);
            context.takeScreenshot("lumen-xray-through-walls");
            context.runOnClient(mc -> LumenClient.settings.throughWalls = false);
            context.waitTicks(5);
            context.takeScreenshot("lumen-xray-depth-tested");
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
            context.getInput().holdKey(LumenClient.zoomKey);
            context.waitTicks(5);
            context.runOnClient(mc -> check(mc.gameRenderer.mainCamera().getFov() < normal / 2, "C zooms camera"));
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
                } catch (java.io.IOException e) { throw new AssertionError("Settings reload failed", e); }
                LumenClient.settings.filters = BlockKind.CHEST.bit();
            });
            context.waitFor(mc -> LumenClient.markerCount() == 1, 300);
            server.runCommand("setblock -3 101 6 minecraft:air");
            context.waitFor(mc -> LumenClient.markerCount() == 0, 100);
            context.runOnClient(mc -> LumenClient.settings.xray = false);
        }
        context.waitTicks(3);
        context.runOnClient(mc -> check(LumenClient.markerCount() == 0, "Leaving world clears markers"));
        try (var reopened = save.open()) {
            reopened.getConnection().waitForChunksRender();
            context.runOnClient(mc -> { LumenClient.settings.xray = true; LumenClient.settings.filters = BlockKind.ENDER_CHEST.bit(); });
            context.waitFor(mc -> LumenClient.markerCount() == 1, 300);
            context.getInput().pressKey(LumenClient.xrayKey);
            context.waitFor(mc -> !LumenClient.settings.xray && LumenClient.markerCount() == 0, 100);
        }
        LumenClient.LOG.info("LUMEN_JAVA_GAME_TEST_OK: scan, removed chest, filters, zoom restore, brightness restore, menu/cursor/slider, persistence, world re-entry");
    }
}
