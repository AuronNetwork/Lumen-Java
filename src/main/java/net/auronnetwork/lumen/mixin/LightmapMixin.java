// SPDX-License-Identifier: GPL-3.0-only
package net.auronnetwork.lumen.mixin;

import net.auronnetwork.lumen.LumenClient;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
abstract class LightmapMixin {
    @Shadow private boolean needsUpdate;
    @Unique private boolean lumen$wasActive;
    @Inject(method = "extract", at = @At("HEAD"))
    private void lumen$refresh(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
        boolean active = LumenClient.fullbrightActive();
        if (active || lumen$wasActive) needsUpdate = true;
        lumen$wasActive = active;
    }
    @Inject(method = "extract", at = @At("TAIL"))
    private void lumen$fullbright(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
        if (LumenClient.fullbrightActive()) {
            state.ambientColor = LightmapRenderStateExtractor.WHITE;
            state.brightness = 1.0f;
            state.darknessEffectScale = 0;
            state.bossOverlayWorldDarkening = 0;
            state.needsUpdate = true;
        }
    }
}
