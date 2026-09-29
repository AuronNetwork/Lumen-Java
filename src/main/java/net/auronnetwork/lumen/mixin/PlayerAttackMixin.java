// SPDX-License-Identifier: GPL-3.0-only
package net.auronnetwork.lumen.mixin;

import net.auronnetwork.lumen.AttackCooldown;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Loaded only on the physical client, which also hosts the singleplayer server. */
@Mixin(Player.class)
abstract class PlayerAttackMixin {
    @Inject(method = "getAttackStrengthScale", at = @At("HEAD"), cancellable = true)
    private void lumen$fullAttackStrength(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (AttackCooldown.appliesTo((Player)(Object)this)) cir.setReturnValue(1.0f);
    }

    @Inject(method = "cannotAttackWithItem", at = @At("HEAD"), cancellable = true)
    private void lumen$skipMinimumCharge(CallbackInfoReturnable<Boolean> cir) {
        if (AttackCooldown.appliesTo((Player)(Object)this)) cir.setReturnValue(false);
    }
}
