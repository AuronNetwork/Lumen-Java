// SPDX-License-Identifier: GPL-3.0-only
package net.auronnetwork.lumen;

import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

public final class LumenSettings {
    public boolean xray = false, throughWalls = true, zoom = true, fullbright = false;
    public int radius = 16, markers = 192, zoomFactor = 4, filters = BlockKind.DEFAULT_MASK;

    public boolean selected(BlockKind kind) { return kind != null && (filters & kind.bit()) != 0; }
    public void normalize() {
        radius = Math.clamp(radius, 4, 24);
        markers = Math.clamp(markers, 32, 512);
        zoomFactor = Math.clamp(zoomFactor, 2, 20);
        filters &= BlockKind.SUPPORTED_MASK;
    }
    public static LumenSettings load(Path path) throws IOException {
        var s = new LumenSettings();
        if (!Files.exists(path)) return s;
        var p = new Properties();
        try (var reader = Files.newBufferedReader(path)) { p.load(reader); }
        s.xray = bool(p, "xray", s.xray);
        s.throughWalls = bool(p, "throughWalls", s.throughWalls);
        s.zoom = bool(p, "zoom", s.zoom);
        s.fullbright = bool(p, "fullbright", s.fullbright);
        s.radius = integer(p, "radius", s.radius);
        s.markers = integer(p, "markers", s.markers);
        s.zoomFactor = integer(p, "zoomFactor", s.zoomFactor);
        s.filters = integer(p, "filters", s.filters);
        s.normalize();
        return s;
    }
    public void save(Path path) throws IOException {
        normalize();
        var p = new Properties();
        p.setProperty("xray", Boolean.toString(xray));
        p.setProperty("throughWalls", Boolean.toString(throughWalls));
        p.setProperty("zoom", Boolean.toString(zoom));
        p.setProperty("fullbright", Boolean.toString(fullbright));
        p.setProperty("radius", Integer.toString(radius));
        p.setProperty("markers", Integer.toString(markers));
        p.setProperty("zoomFactor", Integer.toString(zoomFactor));
        p.setProperty("filters", Integer.toString(filters));
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), "lumen-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temp)) { p.store(writer, "Lumen Java settings"); }
            try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    private static int integer(Properties p, String key, int fallback) {
        try { return Integer.parseInt(p.getProperty(key)); }
        catch (NumberFormatException e) { return fallback; }
    }
    private static boolean bool(Properties p, String key, boolean fallback) {
        String value = p.getProperty(key);
        return "true".equals(value) ? true : "false".equals(value) ? false : fallback;
    }
}
