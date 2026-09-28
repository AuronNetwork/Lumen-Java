// SPDX-License-Identifier: GPL-3.0-only
package net.auronnetwork.lumen;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.*;

public final class LumenClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("Lumen");
    public static LumenSettings settings = new LumenSettings();
    public static KeyMapping menuKey, xrayKey, zoomKey, brightnessKey;
    public static String saveError = "";
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("lumen.properties");
    private static final BlockScanner scanner = new BlockScanner();
    private static final Map<Block, BlockKind> kinds = new IdentityHashMap<>();
    private static ClientLevel scannedLevel;
    private static List<BlockScanner.Hit> hits = List.of();

    @Override public void onInitializeClient() {
        try { settings = LumenSettings.load(CONFIG); }
        catch (Exception e) { saveError = "Could not load settings. See latest.log."; LOG.error("Cannot load Lumen settings", e); }
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockKind kind = BlockKind.classify(BuiltInRegistries.BLOCK.getKey(block).toString());
            if (kind != null) kinds.put(block, kind);
        }
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("lumen", "controls"));
        menuKey = register("menu", InputConstants.KEY_INSERT, category);
        xrayKey = register("xray", InputConstants.KEY_X, category);
        zoomKey = register("zoom", InputConstants.KEY_C, category);
        brightnessKey = register("fullbright", InputConstants.KEY_B, category);
        ClientTickEvents.END_CLIENT_TICK.register(LumenClient::tick);
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("lumen", "markers"), (graphics, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (settings.throughWalls && canDrawMarkers(mc) && !graphics.guiRenderState.isHudHidden)
                MarkerOverlay.extract(graphics, mc, hits, settings.filters);
        });
        LOG.info("Lumen Java ready: Minecraft 26.3; {} supported blocks; client-side rendering only", kinds.size());
    }
    private static KeyMapping register(String name, int key, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.lumen." + name, InputConstants.Type.KEYBOARD, key, category));
    }
    public static void save() {
        try { settings.save(CONFIG); saveError = ""; }
        catch (Exception e) { saveError = "Could not save settings. See latest.log."; LOG.error("Cannot save Lumen settings", e); }
    }
    public static boolean zoomActive() {
        Minecraft mc = Minecraft.getInstance();
        return settings.zoom && zoomKey != null && zoomKey.isDown() && mc.level != null && mc.player != null
                && mc.gui.screen() == null && mc.isWindowActive();
    }
    public static boolean fullbrightActive() {
        Minecraft mc = Minecraft.getInstance();
        return settings.fullbright && mc.level != null && mc.player != null;
    }
    private static void tick(Minecraft mc) {
        while (menuKey.consumeClick()) {
            if (mc.player != null && mc.gui.screen() == null) mc.gui.setScreen(new LumenScreen());
        }
        boolean gameplay = mc.player != null && mc.gui.screen() == null && mc.isWindowActive();
        while (xrayKey.consumeClick()) if (gameplay) { settings.xray = !settings.xray; save(); }
        while (brightnessKey.consumeClick()) if (gameplay) { settings.fullbright = !settings.fullbright; save(); }
        while (zoomKey.consumeClick()) { /* Zoom follows held state; discard click queue. */ }
        if (mc.level != scannedLevel) { scanner.clear(); hits = List.of(); scannedLevel = mc.level; }
        if (mc.level == null || mc.player == null || !settings.xray) { scanner.clear(); hits = List.of(); return; }
        BlockPos pos = mc.player.blockPosition();
        var center = new BlockScanner.Position(pos.getX(), pos.getY(), pos.getZ());
        var mutable = new BlockPos.MutableBlockPos();
        scanner.tick(center, settings.radius, settings.filters, 3000, 1_500_000, p -> {
            mutable.set(p.x(), p.y(), p.z());
            if (scannedLevel.isOutsideBuildHeight(mutable) || !scannedLevel.hasChunk(p.x() >> 4, p.z() >> 4)) return null;
            return kinds.get(scannedLevel.getBlockState(mutable).getBlock());
        });
        hits = scanner.visible(center, settings.markers);
    }
    public static int markerCount() { return hits.size(); }
    private static boolean canDrawMarkers(Minecraft mc) {
        return settings.xray && mc.level != null && mc.level == scannedLevel && mc.player != null
                && mc.gameRenderer.gameRenderState().shouldRenderLevel;
    }
    /** Invoked during level extraction before vanilla drains its per-frame gizmo collector. */
    public static void extractMarkers() {
        Minecraft mc = Minecraft.getInstance();
        // Iris retains world depth for its composites, so vanilla always-on-top gizmos
        // remain occluded. Through-wall markers use the separate GUI overlay below.
        if (settings.throughWalls || !canDrawMarkers(mc)) return;
        try (var ignored = mc.levelExtractor.collectPerFrameMainThreadGizmos()) {
            for (var hit : hits) {
                if (!settings.selected(hit.kind())) continue;
                var p = hit.position();
                Gizmos.cuboid(new BlockPos(p.x(), p.y(), p.z()), 0.002f, GizmoStyle.stroke(hit.kind().color, 2.0f));
            }
        }
    }
}
