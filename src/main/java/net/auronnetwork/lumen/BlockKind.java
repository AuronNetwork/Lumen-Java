// SPDX-License-Identifier: GPL-3.0-only
package net.auronnetwork.lumen;

public enum BlockKind {
    DIAMOND("Diamond", 0xff1affff), EMERALD("Emerald", 0xff1aff4d),
    GOLD("Gold", 0xffffcc1a), IRON("Iron", 0xffffb380),
    REDSTONE("Redstone", 0xffff2626), LAPIS("Lapis Lazuli", 0xff4066ff),
    COAL("Coal", 0xffa6a6a6), COPPER("Copper", 0xffff7333),
    QUARTZ("Quartz", 0xfff2f2ff), ANCIENT_DEBRIS("Ancient Debris", 0xffcc59ff),
    CHEST("Chests", 0xffffb347), TRAPPED_CHEST("Trapped Chests", 0xffff6b6b),
    ENDER_CHEST("Ender Chests", 0xffb094ff);

    public final String label;
    public final int color;
    BlockKind(String label, int color) { this.label = label; this.color = color; }
    public int bit() { return 1 << ordinal(); }
    public static final int SUPPORTED_MASK = (1 << values().length) - 1;
    public static final int DEFAULT_MASK = DIAMOND.bit() | EMERALD.bit() | GOLD.bit() | IRON.bit() | ANCIENT_DEBRIS.bit();

    public static BlockKind classify(String id) {
        if (id == null || !id.startsWith("minecraft:")) return null;
        return switch (id.substring(10)) {
            case "diamond_ore", "deepslate_diamond_ore" -> DIAMOND;
            case "emerald_ore", "deepslate_emerald_ore" -> EMERALD;
            case "gold_ore", "deepslate_gold_ore", "nether_gold_ore" -> GOLD;
            case "iron_ore", "deepslate_iron_ore" -> IRON;
            case "redstone_ore", "deepslate_redstone_ore" -> REDSTONE;
            case "lapis_ore", "deepslate_lapis_ore" -> LAPIS;
            case "coal_ore", "deepslate_coal_ore" -> COAL;
            case "copper_ore", "deepslate_copper_ore" -> COPPER;
            case "nether_quartz_ore" -> QUARTZ;
            case "ancient_debris" -> ANCIENT_DEBRIS;
            case "chest" -> CHEST;
            case "trapped_chest" -> TRAPPED_CHEST;
            case "ender_chest" -> ENDER_CHEST;
            default -> null;
        };
    }
}
