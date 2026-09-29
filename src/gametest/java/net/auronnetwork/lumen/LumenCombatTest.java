// SPDX-License-Identifier: GPL-3.0-only
package net.auronnetwork.lumen;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.UUID;

/** Actual integrated-server damage checks. All targets exist only in the test world. */
final class LumenCombatTest {
    private static void check(boolean value, String description) {
        if (!value) throw new AssertionError(description);
    }
    private static ItemStack chargeGatedItem() {
        var item = new ItemStack(Items.IRON_SWORD);
        item.set(DataComponents.MINIMUM_ATTACK_CHARGE, 1.0f);
        return item;
    }
    private static float damage(ServerPlayer player, boolean checkImmunity) {
        var level = player.level();
        var target = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
        check(target != null, "Test target created");
        target.setPos(player.getX() + 1, player.getY(), player.getZ());
        target.setNoAi(true);
        level.addFreshEntity(target);
        try {
            float before = target.getHealth();
            player.attack(target);
            float after = target.getHealth();
            if (checkImmunity) {
                player.attack(target);
                check(target.getHealth() == after, "Target damage immunity remains unchanged");
            }
            return before - after;
        } finally { target.discard(); }
    }
    static void run(ClientGameTestContext context, TestServerContext server) {
        context.runOnClient(mc -> check(!LumenClient.settings.noAttackCooldown, "No Attack Cooldown defaults OFF"));
        server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE)));
        context.waitTicks(30); // Apply vanilla equipment modifiers and charge the slow axe.
        float[] baseline = server.computeOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            check(player.getMainHandItem().is(Items.IRON_AXE), "Baseline uses an unenchanted iron axe");
            float speed = (float)player.getAttributeValue(Attributes.ATTACK_SPEED);
            float charged = damage(player, false);
            player.resetAttackStrengthTicker();
            check(player.getAttackStrengthScale(0) == 0, "Vanilla attack begins uncharged");
            check(player.cannotAttackWithItem(chargeGatedItem(), 0), "Vanilla minimum charge applies");
            float weak = damage(player, false);
            check(charged > weak * 3, "Vanilla fast repeat is weaker than a charged attack");
            check(charged > 5, "Weapon modifiers affect actual damage");
            return new float[]{charged, weak, speed, (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE)};
        });
        context.clickScreenButton("No Attack Cooldown  OFF");
        context.runOnClient(mc -> {
            check(LumenClient.settings.noAttackCooldown && AttackCooldown.appliesTo(mc.player), "UI activates local combat");
            mc.player.resetAttackStrengthTicker();
            check(mc.player.getAttackStrengthScale(0) == 1, "Client attack indicator agrees with server");
            check(!mc.player.cannotAttackWithItem(chargeGatedItem(), 0), "Client minimum charge is removed");
            try {
                check(LumenSettings.load(FabricLoader.getInstance().getConfigDir().resolve("lumen.properties")).noAttackCooldown,
                        "ON setting persists");
            } catch (java.io.IOException e) { throw new AssertionError(e); }
        });
        float[] boosted = server.computeOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            player.resetAttackStrengthTicker();
            check(player.getAttackStrengthScale(0) == 1, "Integrated server uses full charge immediately");
            check(!player.cannotAttackWithItem(chargeGatedItem(), 0), "Server minimum charge is removed");
            float first = damage(player, true), second = damage(player, false);
            check(Math.abs(first - baseline[0]) < 0.0001f && Math.abs(second - baseline[0]) < 0.0001f,
                    "Consecutive attacks on fresh targets both deal fully charged damage");
            check(player.getAttributeValue(Attributes.ATTACK_SPEED) == baseline[2], "Attack-speed attribute is untouched");
            check(player.getAttributeValue(Attributes.ATTACK_DAMAGE) == baseline[3], "Weapon damage attribute is untouched");
            var other = new ServerPlayer(s, player.level(), new GameProfile(UUID.randomUUID(), "OtherPlayer"), ClientInformation.createDefault());
            other.resetAttackStrengthTicker();
            check(!AttackCooldown.appliesTo(other) && other.getAttackStrengthScale(0) == 0, "Other players stay vanilla");
            return new float[]{first, second};
        });
        context.waitTicks(3);
        context.takeScreenshot("lumen-no-attack-cooldown-on");
        context.clickScreenButton("No Attack Cooldown  ON");
        context.runOnClient(mc -> {
            check(!AttackCooldown.appliesTo(mc.player), "OFF takes effect immediately");
            mc.player.resetAttackStrengthTicker();
            check(mc.player.getAttackStrengthScale(0) == 0, "Client vanilla cooldown restored");
        });
        float restored = server.computeOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            player.resetAttackStrengthTicker();
            check(player.getAttackStrengthScale(0) == 0, "Server vanilla cooldown restored");
            check(player.cannotAttackWithItem(chargeGatedItem(), 0), "Minimum charge restored");
            float result = damage(player, false);
            check(Math.abs(result - baseline[1]) < 0.0001f, "OFF restores actual weak-attack damage");
            return result;
        });
        // Leave the preference enabled for the world-exit/re-entry lifecycle checks.
        context.waitTicks(2);
        context.takeScreenshot("lumen-no-attack-cooldown-off");
        context.clickScreenButton("No Attack Cooldown  OFF");
        LumenClient.LOG.info("LUMEN_COMBAT_TEST_OK: charged={}, weak={}, on_first={}, on_repeat={}, off_restored={}; UI/persistence, minimum charge, other-player scope and target immunity passed",
                baseline[0], baseline[1], boosted[0], boosted[1], restored);
    }
}
