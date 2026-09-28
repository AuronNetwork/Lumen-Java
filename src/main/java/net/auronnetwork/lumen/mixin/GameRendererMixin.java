// SPDX-License-Identifier: GPL-3.0-only
package net.auronnetwork.lumen.mixin;

import net.auronnetwork.lumen.MarkerOverlay;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "extract", at = @At("HEAD"))
    private void lumen$clearPreviousFrame(CallbackInfo ci) {
        ((GameRenderer)(Object)this).gameRenderState().guiRenderState.setData(MarkerOverlay.KEY, null);
    }

    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"), index = 0)
    private Matrix4f lumen$captureWorldProjection(Matrix4f projection) {
        var frame = ((GameRenderer)(Object)this).gameRenderState().guiRenderState.getData(MarkerOverlay.KEY);
        if (frame != null) frame.setWorldProjection(projection);
        return projection;
    }
}
