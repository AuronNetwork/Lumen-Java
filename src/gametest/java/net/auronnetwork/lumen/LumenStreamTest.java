// SPDX-License-Identifier: GPL-3.0-only
package net.auronnetwork.lumen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.Memory;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HDC;
import com.sun.jna.platform.win32.WinGDI.BITMAPINFO;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Windows/OBS integration checks in the disposable development instance only. */
final class LumenStreamTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message + " (" + StreamOverlay.status() + ")");
    }
    static void prepareWindow(ClientGameTestContext context) {
        if (!Platform.isWindows()) return;
        context.runOnClient(mc -> {
            String version = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();
            mc.getWindow().setTitle("Lumen Capture Test " + version);
            var window = new HWND(Pointer.createConstant(StreamMode.windowHandle(mc)));
            var user = User32.INSTANCE;
            int foregroundThread = user.GetWindowThreadProcessId(user.GetForegroundWindow(), null);
            int thisThread = Kernel32.INSTANCE.GetCurrentThreadId();
            boolean attached = foregroundThread != thisThread && foregroundThread != 0
                    && user.AttachThreadInput(new DWORD(thisThread), new DWORD(foregroundThread), true);
            try {
                user.ShowWindow(window, 9); // Restore only this disposable test window.
                user.BringWindowToTop(window);
                user.SetForegroundWindow(window);
                user.SetFocus(window);
            } finally {
                if (attached) user.AttachThreadInput(new DWORD(thisThread), new DWORD(foregroundThread), false);
            }
        });
    }
    static void run(ClientGameTestContext context) {
        if (!Platform.isWindows()) return;
        checkpoint(context, "normal-baseline", true);
        context.getInput().pressKey(LumenClient.menuKey);
        context.waitForScreen(LumenScreen.class);
        context.clickScreenButton("Streaming");
        context.waitTicks(2);
        context.clickScreenButton("Stream mode  OFF");
        context.runOnClient(mc -> {
            check(LumenClient.settings.streamMode, "Actual Streaming toggle enables stream mode");
            try {
                var config = FabricLoader.getInstance().getConfigDir().resolve("lumen.properties");
                check(LumenSettings.load(config).streamMode, "Stream mode persists");
            } catch (java.io.IOException e) { throw new AssertionError(e); }
        });
        context.waitTicks(3);
        context.takeScreenshot("lumen-streaming-menu");
        context.getInput().pressKey(LumenClient.menuKey);
        context.waitFor(mc -> mc.gui.screen() == null);
        prepareWindow(context);
        try {
            context.waitFor(mc -> StreamOverlay.ready() && StreamOverlay.visible() && StreamOverlay.lineCount() >= 48, 300);
        } catch (AssertionError error) {
            context.runOnClient(mc -> LumenClient.LOG.error("Stream test state: draw={}, target={}, foreground={}, overlay={}, ready={}, visible={}, lines={}, status={}",
                    StreamMode.canDraw(mc), StreamMode.windowHandle(mc), User32.INSTANCE.GetForegroundWindow(),
                    StreamOverlay.windowHandle(), StreamOverlay.ready(), StreamOverlay.visible(), StreamOverlay.lineCount(), StreamMode.status(mc)));
            throw error;
        }
        check(StreamOverlay.captureAffinity() == 0x11, "Windows confirms capture exclusion");
        int flags = User32.INSTANCE.GetWindowLong(new HWND(Pointer.createConstant(StreamOverlay.windowHandle())), -20);
        check((flags & 0x08000020) == 0x08000020, "Overlay never activates and passes mouse input through");
        LumenGameTest.checkMarkerImage(context.takeScreenshot("lumen-stream-mode-clean-game"), false);
        checkpoint(context, "stream-on", false);

        context.getInput().resizeWindow(1280, 720);
        context.waitTicks(5);
        awaitVisible(context);
        RECT gameClient = new RECT(), overlay = new RECT();
        long game = context.computeOnClient(StreamMode::windowHandle);
        check(User32.INSTANCE.GetClientRect(new HWND(Pointer.createConstant(game)), gameClient)
                && User32.INSTANCE.GetWindowRect(new HWND(Pointer.createConstant(StreamOverlay.windowHandle())), overlay), "Window dimensions available");
        check(gameClient.right - gameClient.left == overlay.right - overlay.left
                && gameClient.bottom - gameClient.top == overlay.bottom - overlay.top, "Overlay follows resized game client");

        context.getInput().holdKey(LumenClient.zoomKey);
        context.waitTicks(4);
        check(StreamOverlay.visible() && StreamOverlay.lineCount() > 0, "Zoom keeps desktop markers visible");
        LumenGameTest.checkMarkerImage(context.takeScreenshot("lumen-stream-mode-clean-zoom"), false);
        context.getInput().releaseKey(LumenClient.zoomKey);
        context.waitTicks(3);
        context.getInput().pressKey(LumenClient.xrayKey);
        context.waitFor(mc -> !StreamOverlay.visible() && StreamOverlay.lineCount() == 0, 100);
        LumenGameTest.checkMarkerImage(captureLastNativeFrame(), true);
        LumenGameTest.checkMarkerImage(context.takeScreenshot("lumen-stream-mode-xray-off"), false);
        context.getInput().pressKey(LumenClient.xrayKey);
        awaitVisible(context);
        context.getInput().pressKey(options -> options.keyToggleGui);
        context.waitFor(mc -> !StreamOverlay.visible(), 100);
        context.getInput().pressKey(options -> options.keyToggleGui);
        awaitVisible(context);

        context.getInput().lookAt(new BlockPos(0, 102, -6));
        context.waitFor(mc -> !StreamOverlay.visible(), 100);
        context.getInput().lookAt(new BlockPos(0, 102, 6));
        awaitVisible(context);
        context.runOnClient(mc -> LumenClient.settings.throughWalls = false);
        context.waitFor(mc -> !StreamOverlay.visible(), 100);
        LumenGameTest.checkMarkerImage(context.takeScreenshot("lumen-stream-mode-occluded"), false);
        context.runOnClient(mc -> LumenClient.settings.throughWalls = true);
        awaitVisible(context);

        context.getInput().pressKey(LumenClient.menuKey);
        context.waitForScreen(LumenScreen.class);
        context.waitFor(mc -> !StreamOverlay.visible(), 100);
        context.runOnClient(mc -> check(!mc.mouseHandler.isMouseGrabbed(), "Stream-mode menu releases cursor"));
        context.clickScreenButton("Streaming");
        context.waitTicks(2);
        context.clickScreenButton("Stream mode  ON");
        context.getInput().pressKey(LumenClient.menuKey);
        context.waitFor(mc -> mc.gui.screen() == null);
        context.waitTicks(4);
        check(!StreamOverlay.visible(), "OFF hides the external overlay");
        LumenGameTest.checkMarkerImage(context.takeScreenshot("lumen-stream-mode-off-normal-restored"), true);
        checkpoint(context, "normal-restored", true);
        StreamOverlay.close();
        context.waitFor(mc -> StreamOverlay.windowHandle() == 0 && !StreamOverlay.ready(), 100);
        LumenClient.LOG.info("LUMEN_STREAM_TEST_OK: native overlay/0x11, clean game framebuffer, UI/persistence, mouse flags, resize, zoom, Xray/F1/menu/depth/behind-camera hide, normal rendering restored and native shutdown");
    }

    private static void awaitVisible(ClientGameTestContext context) {
        prepareWindow(context);
        try {
            context.waitFor(mc -> StreamOverlay.visible(), 200);
        } catch (AssertionError failure) {
            context.runOnClient(mc -> LumenClient.LOG.error("Stream recovery state: draw={}, target={}, foreground={}, overlay={}, ready={}, lines={}, markers={}, status={}, yaw={}, pitch={}",
                    StreamMode.canDraw(mc), StreamMode.windowHandle(mc), User32.INSTANCE.GetForegroundWindow(),
                    StreamOverlay.windowHandle(), StreamOverlay.ready(), StreamOverlay.lineCount(), LumenClient.markerCount(),
                    StreamMode.status(mc), mc.player.getYRot(), mc.player.getXRot()));
            throw failure;
        }
    }

    /** Optional file bridge to a separate portable OBS; no personal OBS state is touched. */
    private static void checkpoint(ClientGameTestContext context, String phase, boolean expectMarkers) {
        String directory = System.getProperty("lumen.test.obsDir");
        if (directory == null) return;
        prepareWindow(context);
        Path folder = Path.of(directory);
        String version = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();
        String id = version.replace('.', '-') + "-" + phase;
        Path response = folder.resolve("response-" + id + ".json");
        try {
            Files.createDirectories(folder);
            Files.deleteIfExists(response);
            JsonObject data = new JsonObject();
            data.addProperty("id", id); data.addProperty("phase", phase);
            data.addProperty("title", "Lumen Capture Test " + version);
            data.addProperty("pid", ProcessHandle.current().pid());
            data.addProperty("hwnd", context.computeOnClient(StreamMode::windowHandle));
            data.addProperty("overlayHwnd", StreamOverlay.windowHandle());
            data.addProperty("expectMarkers", expectMarkers);
            Path temporary = folder.resolve("request.tmp");
            Files.writeString(temporary, data.toString());
            Files.move(temporary, folder.resolve("request.json"), StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException e) { throw new AssertionError("Cannot request isolated OBS capture", e); }
        context.waitFor(mc -> Files.exists(response), 1200);
        try {
            JsonObject result = JsonParser.parseString(Files.readString(response)).getAsJsonObject();
            check(result.get("ok").getAsBoolean(), "OBS capture checkpoint " + id + ": " + result);
            LumenGameTest.checkMarkerImage(Path.of(result.get("screenshot").getAsString()), expectMarkers);
            LumenClient.LOG.info("LUMEN_OBS_CAPTURE_OK: {}", id);
        } catch (java.io.IOException e) { throw new AssertionError("Cannot read isolated OBS capture", e); }
    }

    /** Copies the actual completed native GDI surface after hiding, without changing capture affinity. */
    private static Path captureLastNativeFrame() {
        check(!StreamOverlay.visible() && StreamOverlay.captureAffinity() == 0x11, "Native frame is hidden and still excluded");
        try {
            Object worker = field(StreamOverlay.class, null, "worker");
            HDC source = (HDC)field(worker.getClass(), worker, "bufferDc");
            int width = (int)field(worker.getClass(), worker, "width");
            int height = (int)field(worker.getClass(), worker, "height");
            var gdi = GDI32.INSTANCE;
            var copy = gdi.CreateCompatibleDC(source);
            var bitmap = gdi.CreateCompatibleBitmap(source, width, height);
            var original = gdi.SelectObject(copy, bitmap);
            try {
                check(gdi.BitBlt(copy, 0, 0, width, height, source, 0, 0, 0x00cc0020), "Native buffer copied");
                gdi.SelectObject(copy, original);
                var info = new BITMAPINFO();
                info.bmiHeader.biWidth = width; info.bmiHeader.biHeight = -height;
                info.bmiHeader.biPlanes = 1; info.bmiHeader.biBitCount = 32; info.bmiHeader.biCompression = 0;
                try (Memory pixels = new Memory((long)width * height * 4)) {
                    check(gdi.GetDIBits(copy, bitmap, 0, height, pixels, info, 0) == height, "Native frame pixels read");
                    var image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
                    image.setRGB(0, 0, width, height, pixels.getIntArray(0, width * height), 0, width);
                    var path = FabricLoader.getInstance().getGameDir().resolve("screenshots/lumen-stream-overlay-local-buffer.png");
                    javax.imageio.ImageIO.write(image, "png", path.toFile());
                    return path;
                }
            } finally {
                gdi.SelectObject(copy, original); gdi.DeleteObject(bitmap); gdi.DeleteDC(copy);
            }
        } catch (ReflectiveOperationException | java.io.IOException e) { throw new AssertionError("Cannot inspect native marker surface", e); }
    }

    private static Object field(Class<?> type, Object instance, String name) throws ReflectiveOperationException {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }
}
