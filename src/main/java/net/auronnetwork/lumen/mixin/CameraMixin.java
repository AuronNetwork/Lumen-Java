// SPDX-License-Identifier: GPL-3.0-only
package net.auronnetwork.lumen.mixin;

import net.auronnetwork.lumen.LumenClient;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
abstract class CameraMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void lumen$zoom(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (LumenClient.zoomActive()) {
            double radians = Math.toRadians(cir.getReturnValueF());
            cir.setReturnValue((float)Math.toDegrees(2 * Math.atan(Math.tan(radians / 2) / LumenClient.settings.zoomFactor)));
        }
    }
}
