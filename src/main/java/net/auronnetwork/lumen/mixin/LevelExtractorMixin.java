// SPDX-License-Identifier: GPL-3.0-only
package net.auronnetwork.lumen.mixin;

import net.auronnetwork.lumen.LumenClient;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
abstract class LevelExtractorMixin {
    @Inject(method = "extractGizmos", at = @At("HEAD"))
    private void lumen$markers(CallbackInfo ci) { LumenClient.extractMarkers(); }
}
