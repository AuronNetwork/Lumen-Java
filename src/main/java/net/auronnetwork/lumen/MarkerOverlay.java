// SPDX-License-Identifier: GPL-3.0-only
package net.auronnetwork.lumen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** One frame-owned GUI batch, composited after world shaders and below menus/HUD. */
public final class MarkerOverlay implements GuiElementRenderState {
    public static final RenderStateDataKey<MarkerOverlay> KEY = RenderStateDataKey.create(() -> "Lumen marker frame");
    private final List<BlockScanner.Hit> hits;
    private final Matrix4f view, projection = new Matrix4f();
    private final Matrix3x2f pose;
    private final double cameraX, cameraY, cameraZ;
    private final float halfWidth;
    private final ScreenRectangle viewport;
    private final boolean zeroToOne;
    private final boolean external;
    private final long targetWindow;
    private boolean projectionReady;

    private MarkerOverlay(GuiGraphicsExtractor graphics, Minecraft mc, List<BlockScanner.Hit> hits) {
        external = LumenClient.settings.streamMode;
        targetWindow = external ? StreamMode.windowHandle(mc) : 0;
        var camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        view = new Matrix4f(camera.viewRotationMatrix);
        cameraX = camera.pos.x; cameraY = camera.pos.y; cameraZ = camera.pos.z;
        this.hits = external && !LumenClient.settings.throughWalls
                ? hits.stream().filter(hit -> visibleFromCamera(mc, hit)).toList() : hits;
        pose = new Matrix3x2f(graphics.pose());
        viewport = new ScreenRectangle(0, 0, graphics.guiWidth(), graphics.guiHeight());
        halfWidth = (float) (1.0 / mc.getWindow().getGuiScale()); // two physical pixels
        zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
    }

    static void extract(GuiGraphicsExtractor graphics, Minecraft mc, List<BlockScanner.Hit> hits, int filters) {
        var selected = hits.stream().filter(h -> (filters & h.kind().bit()) != 0).toList();
        if (selected.isEmpty()) { if (LumenClient.settings.streamMode) StreamOverlay.hide(); return; }
        var frame = new MarkerOverlay(graphics, mc, selected);
        graphics.guiRenderState.setData(KEY, frame);
        // Stream-mode markers never enter the game framebuffer, including on native failure.
        if (!frame.external) graphics.guiRenderState.addGuiElement(frame);
    }

    /** Called on this frame during world rendering, before the GUI vertices are built.
     * Uses the real world projection, including zoom, view bobbing and hurt/portal effects.
     */
    public void setWorldProjection(Matrix4f worldProjection) {
        projection.set(worldProjection).mul(view);
        projectionReady = true;
        if (external) {
            var lines = new ArrayList<StreamOverlay.Line>(hits.size() * 12);
            projectLines((line, color) -> lines.add(new StreamOverlay.Line(line.x0() / viewport.width(),
                    line.y0() / viewport.height(), line.x1() / viewport.width(), line.y1() / viewport.height(), color)));
            StreamOverlay.submit(targetWindow, List.copyOf(lines));
        }
    }

    @Override public void buildVertices(VertexConsumer vertices) {
        if (!projectionReady || LumenClient.settings.streamMode) return;
        projectLines((line, color) -> draw(vertices, line, color));
    }

    private void projectLines(BiConsumer<MarkerProjection.Segment, Integer> output) {
        var corners = new Vector4f[8];
        for (int i = 0; i < 8; i++) corners[i] = new Vector4f();
        for (var hit : hits) {
            var p = hit.position();
            for (int i = 0; i < 8; i++) {
                // Subtract the double-precision camera position before converting to float.
                float x = (float)(p.x() - cameraX + ((i & 1) == 0 ? -0.002 : 1.002));
                float y = (float)(p.y() - cameraY + ((i & 2) == 0 ? -0.002 : 1.002));
                float z = (float)(p.z() - cameraZ + ((i & 4) == 0 ? -0.002 : 1.002));
                projection.transform(corners[i].set(x, y, z, 1));
            }
            for (int i = 0; i < 8; i++) for (int axis = 1; axis <= 4; axis <<= 1) {
                if ((i & axis) != 0) continue;
                var line = MarkerProjection.clip(corners[i], corners[i | axis], viewport.width(), viewport.height(), zeroToOne);
                if (line != null) output.accept(line, hit.kind().color);
            }
        }
    }

    /** A desktop overlay has no world depth buffer; depth mode uses block line of sight. */
    private boolean visibleFromCamera(Minecraft mc, BlockScanner.Hit hit) {
        var p = hit.position();
        var position = new BlockPos(p.x(), p.y(), p.z());
        var result = mc.level.clip(new ClipContext(new Vec3(cameraX, cameraY, cameraZ),
                Vec3.atCenterOf(position), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        return result.getType() == HitResult.Type.MISS || result.getBlockPos().equals(position);
    }

    private void draw(VertexConsumer vertices, MarkerProjection.Segment line, int color) {
        float dx = line.x1() - line.x0(), dy = line.y1() - line.y0();
        float length = (float)Math.sqrt(dx * dx + dy * dy);
        if (length < 0.0001f) return;
        float nx = -dy / length * halfWidth, ny = dx / length * halfWidth;
        vertices.addVertexWith2DPose(pose, line.x0() + nx, line.y0() + ny).setColor(color);
        vertices.addVertexWith2DPose(pose, line.x1() + nx, line.y1() + ny).setColor(color);
        vertices.addVertexWith2DPose(pose, line.x1() - nx, line.y1() - ny).setColor(color);
        vertices.addVertexWith2DPose(pose, line.x0() - nx, line.y0() - ny).setColor(color);
    }
    @Override public RenderPipeline pipeline() { return RenderPipelines.GUI; }
    @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
    @Override public ScreenRectangle scissorArea() { return viewport; }
    @Override public ScreenRectangle bounds() { return viewport; }
}
