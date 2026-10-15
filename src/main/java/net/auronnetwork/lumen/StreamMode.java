// SPDX-License-Identifier: GPL-3.0-only
package net.auronnetwork.lumen;

import com.sun.jna.Platform;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDLVideo;

/** Connects the frame-owned marker projection to the optional Windows desktop overlay. */
public final class StreamMode {
    private StreamMode() { }

    public static boolean canDraw(Minecraft mc) {
        return LumenClient.settings.streamMode && LumenClient.settings.xray && Platform.isWindows()
                && mc.level != null && mc.player != null && mc.gui.screen() == null
                && !mc.gui.hud.isHidden() && mc.isWindowActive() && !mc.getWindow().isIconified()
                && !mc.getWindow().isExclusiveFullscreen();
    }

    public static void refresh(Minecraft mc) {
        if (!canDraw(mc)) StreamOverlay.hide();
    }

    static long windowHandle(Minecraft mc) {
        return SDLProperties.SDL_GetPointerProperty(SDLVideo.SDL_GetWindowProperties(mc.getWindow().handle()),
                SDLVideo.SDL_PROP_WINDOW_WIN32_HWND_POINTER, 0);
    }

    public static String status(Minecraft mc) {
        if (!LumenClient.settings.streamMode) return "Stream mode is off";
        if (!Platform.isWindows()) return "Windows 10 (2004+) or Windows 11 required";
        if (mc.getWindow().isExclusiveFullscreen()) return "Use windowed or borderless fullscreen";
        if (!LumenClient.settings.xray) return "Stream mode ready; Xray is off";
        return StreamOverlay.status();
    }
}
