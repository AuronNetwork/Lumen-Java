// SPDX-License-Identifier: GPL-3.0-only
package net.auronnetwork.lumen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LumenCoreTest {
    @TempDir Path directory;
    private static final BlockScanner.Position ORIGIN = new BlockScanner.Position(0, 0, 0);
    @Test void blockTypesAreExactAndNamespaced() {
        assertEquals(BlockKind.DIAMOND, BlockKind.classify("minecraft:deepslate_diamond_ore"));
        assertEquals(BlockKind.GOLD, BlockKind.classify("minecraft:nether_gold_ore"));
        assertEquals(BlockKind.CHEST, BlockKind.classify("minecraft:chest"));
        assertEquals(BlockKind.TRAPPED_CHEST, BlockKind.classify("minecraft:trapped_chest"));
        assertEquals(BlockKind.ENDER_CHEST, BlockKind.classify("minecraft:ender_chest"));
        for (String name : List.of("minecraft:chest_minecart", "mod:chest", "minecraft:deepslate_chest", "minecraft:barrel", "minecraft:stone", "minecraft:nether_diamond_ore"))
            assertNull(BlockKind.classify(name), name);
    }
    @Test void freshSettingsKeepChestsOffAndPersistIndependentSelections() throws Exception {
        var file = directory.resolve("config/lumen.properties");
        var s = LumenSettings.load(file);
        assertFalse(s.selected(BlockKind.CHEST)); assertTrue(s.selected(BlockKind.DIAMOND));
        s.filters = BlockKind.CHEST.bit() | BlockKind.ENDER_CHEST.bit();
        s.fullbright = true; s.zoomFactor = 8; s.save(file);
        var restored = LumenSettings.load(file);
        assertEquals(s.filters, restored.filters); assertTrue(restored.fullbright); assertEquals(8, restored.zoomFactor);
        assertFalse(restored.selected(BlockKind.TRAPPED_CHEST));
    }
    @Test void malformedAndExtremeSettingsAreBounded() throws Exception {
        var file = directory.resolve("lumen.properties");
        Files.writeString(file, "radius=2147483647\nmarkers=-100\nzoomFactor=oops\nfilters=65535\nzoom=not-boolean\n");
        var s = LumenSettings.load(file);
        assertEquals(24, s.radius); assertEquals(32, s.markers); assertEquals(4, s.zoomFactor);
        assertEquals(BlockKind.SUPPORTED_MASK, s.filters); assertTrue(s.zoom);
        assertEquals(1, Files.list(directory).count());
    }
    @Test void attackCooldownDefaultsOffForNewAndExistingConfigsAndPersistsBothStates() throws Exception {
        var file = directory.resolve("lumen.properties");
        assertFalse(LumenSettings.load(file).noAttackCooldown);
        Files.writeString(file, "xray=true\nradius=24\n");
        var s = LumenSettings.load(file);
        assertFalse(s.noAttackCooldown); assertTrue(s.xray); assertEquals(24, s.radius);
        s.noAttackCooldown = true; s.save(file);
        assertTrue(LumenSettings.load(file).noAttackCooldown);
        s.noAttackCooldown = false; s.save(file);
        assertFalse(LumenSettings.load(file).noAttackCooldown);
        Files.writeString(file, "noAttackCooldown=invalid\n");
        assertFalse(LumenSettings.load(file).noAttackCooldown);
    }
    @Test void streamModeDefaultsOffAndPersistsWithoutChangingOtherOptions() throws Exception {
        var file = directory.resolve("lumen.properties");
        assertFalse(LumenSettings.load(file).streamMode);
        Files.writeString(file, "xray=true\nthroughWalls=false\nfilters=8191\n");
        var s = LumenSettings.load(file);
        assertFalse(s.streamMode);
        s.streamMode = true; s.save(file);
        var restored = LumenSettings.load(file);
        assertTrue(restored.streamMode); assertTrue(restored.xray);
        assertFalse(restored.throughWalls); assertEquals(s.filters, restored.filters);
        restored.streamMode = false; restored.save(file);
        assertFalse(LumenSettings.load(file).streamMode);
        Files.writeString(file, "streamMode=invalid\n");
        assertFalse(LumenSettings.load(file).streamMode);
    }
    @Test void markerOrderingLimitsAndRefreshTrackWorldChanges() {
        var scanner = new BlockScanner(); var world = new HashMap<BlockScanner.Position, BlockKind>();
        var near = new BlockScanner.Position(1, 0, 0); var far = new BlockScanner.Position(3, 0, 0);
        world.put(near, BlockKind.CHEST); world.put(far, BlockKind.CHEST);
        scanner.tick(ORIGIN, 4, BlockKind.CHEST.bit(), 3000, Long.MAX_VALUE, world::get);
        assertEquals(2, scanner.visible(ORIGIN, 32).size());
        assertEquals(near, scanner.visible(ORIGIN, 1).getFirst().position());
        world.remove(near); world.put(far, BlockKind.DIAMOND);
        scanner.tick(ORIGIN, 4, BlockKind.CHEST.bit(), 64, Long.MAX_VALUE, world::get);
        assertTrue(scanner.visible(ORIGIN, 32).isEmpty());
    }
    @Test void readsAndRadiusAreBoundedAndNoFiltersDoNoWork() {
        var scanner = new BlockScanner(); int[] reads = {0};
        int used = scanner.tick(ORIGIN, 4, BlockKind.CHEST.bit(), 75, Long.MAX_VALUE, p -> {
            reads[0]++; assertTrue(p.distanceSquared(ORIGIN) <= 16); return BlockKind.CHEST;
        });
        assertEquals(75, reads[0]); assertEquals(75, used);
        assertEquals(0, scanner.tick(ORIGIN, 4, 0, 3000, Long.MAX_VALUE, p -> { fail("No reads expected"); return null; }));
        assertTrue(scanner.visible(ORIGIN, 512).isEmpty());
    }
    @Test void teleportAndWorldResetDiscardOldHits() {
        var scanner = new BlockScanner();
        scanner.tick(ORIGIN, 4, BlockKind.CHEST.bit(), 64, Long.MAX_VALUE, p -> BlockKind.CHEST);
        var destination = new BlockScanner.Position(29_999_900, 200, -29_999_900);
        scanner.tick(destination, 4, BlockKind.CHEST.bit(), 64, Long.MAX_VALUE, p -> null);
        assertTrue(scanner.visible(destination, 512).isEmpty());
        scanner.clear(); assertTrue(scanner.visible(ORIGIN, 512).isEmpty());
    }
    @Test void filtersChangeImmediatelyAndDoubleChestsStaySeparate() {
        var scanner = new BlockScanner();
        var right = new BlockScanner.Position(1, 0, 0);
        scanner.tick(ORIGIN, 4, BlockKind.CHEST.bit(), 3000, Long.MAX_VALUE, p -> p.equals(ORIGIN) || p.equals(right) ? BlockKind.CHEST : BlockKind.DIAMOND);
        assertEquals(2, scanner.visible(ORIGIN, 512).size());
        scanner.tick(ORIGIN, 4, BlockKind.DIAMOND.bit(), 10, Long.MAX_VALUE, p -> BlockKind.CHEST);
        assertTrue(scanner.visible(ORIGIN, 512).isEmpty());
    }
}
