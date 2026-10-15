// SPDX-License-Identifier: GPL-3.0-only
package net.auronnetwork.lumen;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HBITMAP;
import com.sun.jna.platform.win32.WinDef.HDC;
import com.sun.jna.platform.win32.WinDef.HINSTANCE;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.platform.win32.WinUser.SIZE;
import com.sun.jna.platform.win32.WinUser.WNDCLASSEX;
import com.sun.jna.platform.win32.WinUser.WindowProc;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * A separate, click-through Windows window. No marker is drawn into Minecraft's
 * framebuffer. Windows capture exclusion is verified before this window is shown.
 * Capture programs must support the Windows display-affinity contract.
 */
public final class StreamOverlay {
    /** Coordinates are relative to the physical Minecraft client area; color is ARGB. */
    public record Line(float x0, float y0, float x1, float y1, int color) { }

    private record Frame(long owner, List<Line> lines, long submitted, long generation) { }

    private static final long FRAME_PERIOD = 1_000_000_000L / 60;
    private static final long STALE_AFTER = 250_000_000L;
    private static final int EXCLUDE_FROM_CAPTURE = 0x11;
    private static final AtomicReference<Frame> LATEST = new AtomicReference<>();
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final Object LIFECYCLE = new Object();
    private static volatile Worker worker;
    private static volatile boolean unavailable;
    private static volatile boolean ready;
    private static volatile String status = "Overlay starts when markers are available.";
    private static volatile long nativeWindow;
    private static volatile boolean visible;
    private static volatile int lineCount;
    private static volatile int affinity;

    private StreamOverlay() { }

    /** Publishes only the latest immutable frame. Native work happens on a daemon thread. */
    public static void submit(long minecraftHwnd, List<Line> immutableLines) {
        if (minecraftHwnd == 0 || immutableLines == null || immutableLines.isEmpty()) {
            hide();
            return;
        }
        if (unavailable) return;
        LATEST.set(new Frame(minecraftHwnd, immutableLines, System.nanoTime(), GENERATION.get()));
        startIfNeeded();
        Worker current = worker;
        if (current != null) LockSupport.unpark(current);
    }

    /** Invalidates any frame already being prepared; the worker hides within one tick. */
    public static void hide() {
        GENERATION.incrementAndGet();
        LATEST.set(null);
        Worker current = worker;
        if (current != null) LockSupport.unpark(current);
    }

    /** Asynchronously destroys the native resources. A later submission can restart it. */
    public static void close() {
        hide();
        synchronized (LIFECYCLE) {
            Worker current = worker;
            if (current != null) {
                current.stopping = true;
                ready = false;
                LockSupport.unpark(current);
            }
        }
    }

    public static String status() { return status; }
    public static boolean ready() { return ready; }

    // Read-only diagnostics for the in-client smoke test. These never call Win32.
    public static long windowHandle() { return nativeWindow; }
    public static boolean visible() { return visible; }
    public static int lineCount() { return lineCount; }
    public static int captureAffinity() { return affinity; }

    private static void startIfNeeded() {
        if (worker != null || unavailable) return;
        synchronized (LIFECYCLE) {
            if (worker == null && !unavailable && LATEST.get() != null) {
                Worker next = new Worker();
                worker = next;
                status = "Starting Windows overlay...";
                next.start();
            }
        }
    }

    private interface OverlayUser32 extends User32 {
        boolean ClientToScreen(HWND hwnd, POINT point);
        boolean IsIconic(HWND hwnd);
        boolean SetWindowDisplayAffinity(HWND hwnd, int affinity);
        boolean GetWindowDisplayAffinity(HWND hwnd, IntByReference affinity);
        Pointer SetThreadDpiAwarenessContext(Pointer context);
    }

    private interface OverlayGdi32 extends GDI32 {
        HANDLE CreatePen(int style, int width, int color);
        boolean PolyPolyline(HDC dc, Pointer points, int[] pointCounts, int polylineCount);
        boolean PatBlt(HDC dc, int x, int y, int width, int height, int operation);
        boolean GdiFlush();
    }

    private interface Dwm extends StdCallLibrary {
        int DwmIsCompositionEnabled(IntByReference enabled);
    }

    private interface Nt extends StdCallLibrary {
        int RtlGetVersion(Pointer version);
    }

    private static final class Worker extends Thread {
        private static final int EX_STYLE = 0x00080000 | 0x00000020 | 0x00000080
                | 0x08000000 | 0x00000008; // LAYERED, TRANSPARENT, TOOLWINDOW, NOACTIVATE, TOPMOST
        private static final int BLACKNESS = 0x00000042;
        private static final HWND TOPMOST = new HWND(Pointer.createConstant(-1L));
        private final String className = "LumenStreamOverlay_" + Long.toUnsignedString(System.nanoTime());
        private final WindowProc windowProc = this::windowProcedure;
        private volatile boolean stopping;
        private OverlayUser32 user;
        private OverlayGdi32 gdi;
        private Dwm dwm;
        private HINSTANCE instance;
        private HWND window;
        private HDC screenDc;
        private HDC bufferDc;
        private HBITMAP bitmap;
        private HANDLE previousBitmap;
        private Pointer previousDpi;
        private boolean registered;
        private int width;
        private int height;

        private Worker() {
            super("Lumen Windows stream overlay");
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                initialize();
                if (!stopping) ready = true;
                long nextFrame = 0;
                MSG message = new MSG();
                while (!stopping) {
                    while (user.PeekMessage(message, null, 0, 0, 1)) {
                        if (message.message == 0x0012) { // WM_QUIT
                            stopping = true;
                            break;
                        }
                        user.TranslateMessage(message);
                        user.DispatchMessage(message);
                    }
                    if (stopping) break;
                    if (LATEST.get() == null) hideNative("Ready; no markers to display.");
                    long now = System.nanoTime();
                    if (now >= nextFrame) {
                        update(now);
                        nextFrame = now + FRAME_PERIOD;
                    }
                    LockSupport.parkNanos(this, Math.max(1, nextFrame - System.nanoTime()));
                }
            } catch (Throwable failure) {
                unavailable = true;
                String reason = failure.getMessage();
                status = "Overlay unavailable: " + (reason == null
                        ? failure.getClass().getSimpleName() : reason);
            } finally {
                ready = false;
                cleanup();
                synchronized (LIFECYCLE) {
                    if (worker == this) worker = null;
                    if (!unavailable) {
                        status = "Overlay closed.";
                        startIfNeeded();
                    }
                }
            }
        }

        private void initialize() {
            require(Platform.isWindows(), "Windows 10 version 2004 or newer is required.");
            Nt nt = Native.load("ntdll", Nt.class, W32APIOptions.DEFAULT_OPTIONS);
            try (Memory version = new Memory(284)) { // RTL_OSVERSIONINFOEXW, independent of app manifest
                version.clear();
                version.setInt(0, 284);
                require(nt.RtlGetVersion(version) == 0, "Cannot verify the Windows version.");
                int major = version.getInt(4);
                int build = version.getInt(12);
                require(major > 10 || (major == 10 && build >= 19041),
                        "Windows 10 version 2004 or newer is required.");
            }
            user = Native.load("user32", OverlayUser32.class, W32APIOptions.DEFAULT_OPTIONS);
            gdi = Native.load("gdi32", OverlayGdi32.class, W32APIOptions.DEFAULT_OPTIONS);
            dwm = Native.load("dwmapi", Dwm.class, W32APIOptions.DEFAULT_OPTIONS);
            require(compositionEnabled(), "Windows desktop composition is required.");
            // Only the overlay thread changes awareness; Minecraft's process state is untouched.
            previousDpi = user.SetThreadDpiAwarenessContext(Pointer.createConstant(-4L));
            require(previousDpi != null, "Cannot enable physical-pixel overlay coordinates.");
            instance = Kernel32.INSTANCE.GetModuleHandle(null);
            WNDCLASSEX definition = new WNDCLASSEX();
            definition.hInstance = instance;
            definition.lpszClassName = className;
            definition.lpfnWndProc = windowProc;
            require(user.RegisterClassEx(definition).intValue() != 0, nativeError("RegisterClassEx"));
            registered = true;
            // No WS_VISIBLE, no owner and no parent: this is our own hidden top-level window.
            window = user.CreateWindowEx(EX_STYLE, className, "Lumen stream overlay", 0x80000000,
                    0, 0, 1, 1, null, null, instance, null);
            require(valid(window), nativeError("CreateWindowEx"));
            nativeWindow = address(window);
            require(user.SetWindowDisplayAffinity(window, EXCLUDE_FROM_CAPTURE),
                    nativeError("Windows capture exclusion"));
            verifyAffinity();
            screenDc = user.GetDC(null);
            require(valid(screenDc), nativeError("GetDC"));
            bufferDc = gdi.CreateCompatibleDC(screenDc);
            require(valid(bufferDc), nativeError("CreateCompatibleDC"));
            status = "Ready; Windows capture exclusion verified.";
        }

        private void update(long now) {
            Frame frame = LATEST.get();
            if (frame == null || frame.generation != GENERATION.get()
                    || now - frame.submitted > STALE_AFTER || frame.lines.isEmpty()) {
                hideNative("Ready; waiting for current markers.");
                return;
            }
            HWND owner = new HWND(Pointer.createConstant(frame.owner));
            if (!user.IsWindow(owner) || !user.IsWindowVisible(owner) || user.IsIconic(owner)
                    || address(user.GetForegroundWindow()) != frame.owner) {
                hideNative("Ready; Minecraft must be in the foreground.");
                return;
            }
            require(compositionEnabled(), "Windows desktop composition stopped.");
            verifyAffinity();
            RECT client = new RECT();
            if (!user.GetClientRect(owner, client)) {
                hideNative("Ready; waiting for the Minecraft window.");
                return;
            }
            POINT origin = new POINT(client.left, client.top);
            POINT corner = new POINT(client.right, client.bottom);
            if (!user.ClientToScreen(owner, origin) || !user.ClientToScreen(owner, corner)) {
                hideNative("Ready; waiting for the Minecraft window.");
                return;
            }
            int newWidth = corner.x - origin.x;
            int newHeight = corner.y - origin.y;
            if (newWidth <= 0 || newHeight <= 0) {
                hideNative("Ready; Minecraft is minimized.");
                return;
            }
            require(newWidth <= 32768 && newHeight <= 32768
                    && (long) newWidth * newHeight <= 67_108_864,
                    "The Minecraft window exceeds the supported overlay size.");
            resizeBuffer(newWidth, newHeight);
            int drawn = draw(frame.lines);
            // hide()/close() can invalidate an expensive frame while GDI prepares it.
            if (stopping || frame.generation != GENERATION.get() || LATEST.get() == null
                    || System.nanoTime() - frame.submitted > STALE_AFTER || drawn == 0
                    || address(user.GetForegroundWindow()) != frame.owner || user.IsIconic(owner)) {
                hideNative("Ready; waiting for current markers.");
                return;
            }
            require(user.UpdateLayeredWindow(window, screenDc, origin, new SIZE(width, height),
                    bufferDc, new POINT(0, 0), 0, null, 1), nativeError("UpdateLayeredWindow"));
            // Check once more immediately before making the prepared layer visible.
            verifyAffinity();
            if (stopping || frame.generation != GENERATION.get() || LATEST.get() == null) {
                hideNative("Ready; waiting for current markers.");
                return;
            }
            require(user.SetWindowPos(window, TOPMOST, 0, 0, 0, 0,
                    0x0001 | 0x0002 | 0x0010 | 0x0040), nativeError("SetWindowPos"));
            visible = true;
            lineCount = drawn;
            status = "Active; Windows capture exclusion verified.";
        }

        private void verifyAffinity() {
            IntByReference actual = new IntByReference();
            require(user.GetWindowDisplayAffinity(window, actual)
                    && actual.getValue() == EXCLUDE_FROM_CAPTURE,
                    "Windows capture exclusion could not be verified.");
            affinity = actual.getValue();
        }

        private boolean compositionEnabled() {
            IntByReference enabled = new IntByReference();
            return dwm.DwmIsCompositionEnabled(enabled) >= 0 && enabled.getValue() != 0;
        }

        private void resizeBuffer(int newWidth, int newHeight) {
            if (newWidth == width && newHeight == height) return;
            releaseBitmap();
            bitmap = gdi.CreateCompatibleBitmap(screenDc, newWidth, newHeight);
            require(valid(bitmap), nativeError("CreateCompatibleBitmap"));
            previousBitmap = gdi.SelectObject(bufferDc, bitmap);
            require(valid(previousBitmap), nativeError("SelectObject"));
            width = newWidth;
            height = newHeight;
        }

        private int draw(List<Line> lines) {
            require(gdi.PatBlt(bufferDc, 0, 0, width, height, BLACKNESS), nativeError("PatBlt"));
            Map<Integer, List<Line>> batches = new LinkedHashMap<>();
            int count = 0;
            for (Line line : lines) {
                if (line == null || !Float.isFinite(line.x0) || !Float.isFinite(line.y0)
                        || !Float.isFinite(line.x1) || !Float.isFinite(line.y1)) continue;
                int rgb = line.color & 0x00ffffff;
                if (rgb == 0) rgb = 0x010101; // Black is reserved for the transparent background.
                int colorRef = ((rgb & 0xff) << 16) | (rgb & 0xff00) | ((rgb >>> 16) & 0xff);
                batches.computeIfAbsent(colorRef, ignored -> new ArrayList<>()).add(line);
            }
            for (var entry : batches.entrySet()) {
                var batch = entry.getValue();
                // POINT is exactly two 32-bit LONGs on both 32- and 64-bit Windows.
                // Pack Java arrays first, then copy once instead of making a native call per point.
                int[] coordinates = new int[Math.multiplyExact(batch.size(), 4)];
                int[] pointCounts = new int[batch.size()];
                for (int i = 0; i < batch.size(); i++) {
                    Line line = batch.get(i);
                    coordinates[i * 4] = coordinate(line.x0, width);
                    coordinates[i * 4 + 1] = coordinate(line.y0, height);
                    coordinates[i * 4 + 2] = coordinate(line.x1, width);
                    coordinates[i * 4 + 3] = coordinate(line.y1, height);
                    pointCounts[i] = 2; // Each disconnected polyline is one marker edge.
                }
                HANDLE pen = gdi.CreatePen(0, 2, entry.getKey()); // Two physical pixels.
                require(valid(pen), nativeError("CreatePen"));
                HANDLE originalPen = null;
                try (Memory points = new Memory((long) coordinates.length * Integer.BYTES)) {
                    points.write(0, coordinates, 0, coordinates.length);
                    originalPen = gdi.SelectObject(bufferDc, pen);
                    require(valid(originalPen), nativeError("SelectObject"));
                    require(gdi.PolyPolyline(bufferDc, points, pointCounts, batch.size()),
                            nativeError("Draw markers"));
                    count += batch.size();
                } finally {
                    if (valid(originalPen)) gdi.SelectObject(bufferDc, originalPen);
                    gdi.DeleteObject(pen);
                }
            }
            require(gdi.GdiFlush(), nativeError("GdiFlush"));
            return count;
        }

        private LRESULT windowProcedure(HWND hwnd, int message, WPARAM wParam, LPARAM lParam) {
            return switch (message) {
                case 0x0084 -> new LRESULT(-1); // WM_NCHITTEST: HTTRANSPARENT
                case 0x0021 -> new LRESULT(3); // WM_MOUSEACTIVATE: MA_NOACTIVATE
                case 0x0014 -> new LRESULT(1); // WM_ERASEBKGND: the complete layer is supplied atomically
                case 0x0010 -> { // WM_CLOSE, always destroy on the owning thread
                    stopping = true;
                    user.ShowWindow(hwnd, 0);
                    yield new LRESULT(0);
                }
                case 0x0002 -> { stopping = true; yield new LRESULT(0); } // WM_DESTROY
                default -> user.DefWindowProc(hwnd, message, wParam, lParam);
            };
        }

        private void hideNative(String reason) {
            if (visible) user.ShowWindow(window, 0);
            visible = false;
            lineCount = 0;
            status = reason;
        }

        private void releaseBitmap() {
            if (previousBitmap != null) {
                gdi.SelectObject(bufferDc, previousBitmap);
                previousBitmap = null;
            }
            if (bitmap != null) {
                gdi.DeleteObject(bitmap);
                bitmap = null;
            }
            width = height = 0;
        }

        private void cleanup() {
            // Every native object belongs to this thread. Independent cleanup steps prevent a
            // secondary native error from leaving a visible layer or a registered callback.
            try { if (user != null && valid(window)) user.ShowWindow(window, 0); } catch (Throwable ignored) { }
            visible = false;
            lineCount = 0;
            affinity = 0;
            nativeWindow = 0;
            try { if (user != null && valid(window)) user.DestroyWindow(window); } catch (Throwable ignored) { }
            try { if (gdi != null) releaseBitmap(); } catch (Throwable ignored) { }
            try { if (gdi != null && valid(bufferDc)) gdi.DeleteDC(bufferDc); } catch (Throwable ignored) { }
            try { if (user != null && valid(screenDc)) user.ReleaseDC(null, screenDc); } catch (Throwable ignored) { }
            try { if (registered) user.UnregisterClass(className, instance); } catch (Throwable ignored) { }
            try { if (previousDpi != null) user.SetThreadDpiAwarenessContext(previousDpi); } catch (Throwable ignored) { }
        }
    }

    private static int coordinate(float normalized, int extent) {
        // A malformed/offscreen projection must not overflow GDI's coordinate range.
        return (int) Math.max(-65536, Math.min(65536, (double) normalized * extent));
    }

    private static long address(HANDLE handle) {
        return handle == null ? 0 : Pointer.nativeValue(handle.getPointer());
    }

    private static boolean valid(HANDLE handle) {
        long value = address(handle);
        return value != 0 && value != -1;
    }

    private static String nativeError(String action) {
        return action + " failed (Windows error " + Native.getLastError() + ").";
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException(reason);
    }
}
