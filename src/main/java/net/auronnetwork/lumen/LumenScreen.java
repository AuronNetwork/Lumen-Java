// SPDX-License-Identifier: GPL-3.0-only
package net.auronnetwork.lumen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import java.util.function.*;

/** Native screen ownership releases the cursor and prevents clicks reaching gameplay. */
public final class LumenScreen extends Screen {
    private static final int PAPER = 0xfff5f3ea, MUTED = 0xff9b9b92, SIGNAL = 0xffffce00;
    private int left, top, panelWidth, panelHeight, tab;
    public LumenScreen() { super(Component.literal("Lumen")); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) { }
    @Override public void onClose() { LumenClient.save(); minecraft.gui.setScreen(null); }
    @Override public boolean keyPressed(KeyEvent event) {
        if (LumenClient.menuKey.matches(event)) { onClose(); return true; }
        return super.keyPressed(event);
    }
    @Override protected void init() {
        panelWidth = Math.min(600, width - 16); panelHeight = Math.min(340, height - 16);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        int contentWidth = panelWidth - 24;
        String[] tabs = {"Features", "Ore Filters", "Chest Filters"};
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            button(left + 12 + i * (contentWidth / 3), top + 43, contentWidth / 3 - 4, 22,
                    () -> tabs[index], () -> tab == index, () -> { tab = index; rebuildWidgets(); }, "");
        }
        int y = top + 76;
        int bodyHeight = panelHeight - 104;
        int gap = Math.clamp(bodyHeight / 5, 20, 43);
        int rowHeight = Math.clamp(gap - 5, 16, 30);
        var s = LumenClient.settings;
        if (tab == 0) {
            int columnWidth = (contentWidth - 12) / 2;
            toggle(left + 12, y, columnWidth, rowHeight, "Xray", () -> s.xray, () -> s.xray = !s.xray,
                    "Highlight selected ores and chests. Default key: X.");
            toggle(left + 12, y + gap, columnWidth, rowHeight, "Through walls", () -> s.throughWalls, () -> s.throughWalls = !s.throughWalls,
                    "Keep markers visible behind blocks.");
            toggle(left + 12, y + 2 * gap, columnWidth, rowHeight, "Zoom", () -> s.zoom, () -> s.zoom = !s.zoom,
                    "Hold the Zoom key (default C). Release to restore your view.");
            toggle(left + 12, y + 3 * gap, columnWidth, rowHeight, "Fullbright", () -> s.fullbright, () -> s.fullbright = !s.fullbright,
                    "Brighten the view locally. Default key: B.");
            toggle(left + 12, y + 4 * gap, columnWidth, rowHeight, "No Attack Cooldown", () -> s.noAttackCooldown, () -> s.noAttackCooldown = !s.noAttackCooldown,
                    "Full melee attack strength without recharging in singleplayer. Inactive on multiplayer/LAN. Target damage immunity is unchanged.");
            int right = left + 24 + columnWidth;
            addRenderableWidget(new LumenSlider(right, y, columnWidth, rowHeight, "Radius", " blocks", 4, 24, 1, () -> s.radius, v -> s.radius = v));
            addRenderableWidget(new LumenSlider(right, y + gap, columnWidth, rowHeight, "Markers", "", 32, 512, 32, () -> s.markers, v -> s.markers = v));
            addRenderableWidget(new LumenSlider(right, y + 2 * gap, columnWidth, rowHeight, "Zoom", "x", 2, 20, 1, () -> s.zoomFactor, v -> s.zoomFactor = v));
            button(right, y + 4 * gap, columnWidth, rowHeight, () -> "Done", () -> false, this::onClose,
                    "Close Lumen and return to your game.");
        } else {
            int start = tab == 1 ? 0 : 10, end = tab == 1 ? 10 : 13;
            int columns = panelWidth >= 390 ? 3 : 2;
            int rows = (end - start + columns - 1) / columns;
            int spacing = Math.min(40, bodyHeight / rows);
            int cellWidth = contentWidth / columns;
            for (int i = start; i < end; i++) {
                BlockKind kind = BlockKind.values()[i];
                int n = i - start;
                button(left + 12 + n % columns * cellWidth, y + n / columns * spacing, cellWidth - 5, Math.min(30, spacing - 5),
                        () -> (s.selected(kind) ? "+ " : "") + kind.label, () -> s.selected(kind),
                        () -> { s.filters ^= kind.bit(); LumenClient.save(); },
                        "Toggle " + kind.label + ". Xray must be enabled.");
            }
        }
    }
    private void toggle(int x, int y, int w, int h, String text, BooleanSupplier value, Runnable action, String tooltip) {
        button(x, y, w, h, () -> text + (value.getAsBoolean() ? "  ON" : "  OFF"), value,
                () -> { action.run(); LumenClient.save(); }, tooltip);
    }
    private void button(int x, int y, int w, int h, Supplier<String> label, BooleanSupplier selected, Runnable action, String tooltip) {
        var b = new LumenButton(x, y, w, h, label, selected, action);
        if (!tooltip.isEmpty()) b.setTooltip(Tooltip.create(Component.literal(tooltip)));
        addRenderableWidget(b);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0x26050505);
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xeb050505);
        g.fill(left, top, left + panelWidth, top + 2, SIGNAL);
        g.text(font, "LUMEN", left + 12, top + 13, PAPER, false);
        g.text(font, "AURON NETWORK / JAVA 26.2", left + 12, top + 28, MUTED, false);
        String close = LumenClient.menuKey.getTranslatedKeyMessage().getString() + " / Esc";
        g.text(font, close, left + panelWidth - 12 - font.width(close), top + 13, SIGNAL, false);
        if (tab == 0) {
            int columnWidth = (panelWidth - 36) / 2;
            int gap = Math.clamp((panelHeight - 104) / 5, 20, 43);
            String scope = AttackCooldown.available(minecraft) ? "Combat: singleplayer" : "Combat inactive: local only";
            g.centeredText(font, font.plainSubstrByWidth(scope, columnWidth - 8), left + 24 + columnWidth + columnWidth / 2,
                    top + 76 + 3 * gap + 4, MUTED);
        }
        String status = LumenClient.saveError.isEmpty()
                ? (LumenClient.settings.xray ? LumenClient.markerCount() + " blocks marked" : "Xray is off")
                : LumenClient.saveError;
        g.text(font, font.plainSubstrByWidth(status, panelWidth - 24), left + 12, top + panelHeight - 16,
                LumenClient.saveError.isEmpty() ? MUTED : 0xffff6b6b, false);
        super.extractRenderState(g, mx, my, delta);
    }
    private static final class LumenButton extends Button {
        private final Supplier<String> label;
        private final BooleanSupplier selected;
        LumenButton(int x, int y, int w, int h, Supplier<String> label, BooleanSupplier selected, Runnable action) {
            super(x, y, w, h, Component.literal(label.get()), b -> action.run(), DEFAULT_NARRATION);
            this.label = label; this.selected = selected;
        }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
            setMessage(Component.literal(label.get()));
            boolean active = selected.getAsBoolean(), hover = isHoveredOrFocused();
            g.fill(getX(), getY(), getX() + width, getY() + height, hover ? 0xff353127 : active ? 0xff292515 : 0xff171717);
            g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, active || hover ? SIGNAL : 0xff343434);
            var font = Minecraft.getInstance().font;
            g.centeredText(font, font.plainSubstrByWidth(label.get(), width - 8), getX() + width / 2, getY() + (height - 8) / 2, active ? SIGNAL : PAPER);
        }
    }
    private static final class LumenSlider extends AbstractSliderButton {
        private final String label, unit;
        private final int min, max, step;
        private final IntConsumer setter;
        LumenSlider(int x, int y, int w, int h, String label, String unit, int min, int max, int step, IntSupplier getter, IntConsumer setter) {
            super(x, y, w, h, Component.empty(), (double)(getter.getAsInt() - min) / (max - min));
            this.label = label; this.unit = unit; this.min = min; this.max = max; this.step = step; this.setter = setter;
            updateMessage();
        }
        private int amount() { return Math.clamp(min + (int)Math.round(value * (max - min) / step) * step, min, max); }
        @Override protected void updateMessage() { setMessage(Component.literal(label + ": " + amount() + unit)); }
        @Override protected void applyValue() { setter.accept(amount()); LumenClient.save(); }
        @Override public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            g.fill(getX(), getY(), getX() + width, getY() + height, 0xff171717);
            int handle = getX() + 4 + (int)(value * (width - 8));
            g.fill(getX() + 4, getY() + height - 4, getX() + width - 4, getY() + height - 2, 0xff343434);
            g.fill(getX() + 4, getY() + height - 4, handle, getY() + height - 2, SIGNAL);
            g.fill(handle - 2, getY() + height - 6, handle + 2, getY() + height - 1, isHoveredOrFocused() ? PAPER : SIGNAL);
            g.centeredText(Minecraft.getInstance().font, getMessage(), getX() + width / 2, getY() + Math.max(2, (height - 12) / 2), PAPER);
            handleCursor(g);
        }
    }
}
