// SPDX-License-Identifier: GPL-3.0-only
package net.auronnetwork.lumen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import java.util.UUID;

/** Shares an immutable activation snapshot with the integrated server thread.
 * No weapon attributes, player timers or save data are overwritten.
 */
public final class AttackCooldown {
    private record Session(IntegratedServer server, LocalPlayer player, UUID owner) { }
    private static volatile Session session;

    public static boolean available(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        return mc.hasSingleplayerServer() && server != null && !server.isPublished()
                && mc.level != null && mc.player != null;
    }

    /** Client-thread entry point, also called immediately when settings are saved. */
    public static void refresh(Minecraft mc) {
        if (!LumenClient.settings.noAttackCooldown || !available(mc)) { clear(); return; }
        var previous = session;
        if (previous == null || previous.server != mc.getSingleplayerServer() || previous.player != mc.player)
            session = new Session(mc.getSingleplayerServer(), mc.player, mc.player.getUUID());
    }

    public static void clear() { session = null; }

    public static boolean appliesTo(Player player) {
        var current = session;
        if (current == null || current.server.isPublished()) return false;
        if (player == current.player) return true;
        return player instanceof ServerPlayer serverPlayer
                && serverPlayer.level().getServer() == current.server
                && player.getUUID().equals(current.owner);
    }
}
