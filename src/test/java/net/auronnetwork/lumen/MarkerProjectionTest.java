// SPDX-License-Identifier: GPL-3.0-only
package net.auronnetwork.lumen;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarkerProjectionTest {
    @Test void clipsToViewportWithoutDrawingBehindCameraForBothDepthRanges() {
        for (boolean zeroToOne : new boolean[]{false, true}) {
            // Minecraft uses reversed near/far depth. Both OpenGL and zero-to-one work.
            var projection = new Matrix4f().setPerspective((float)Math.PI / 2, 1, 100, 0.05f, zeroToOne);
            var left = projection.transform(new Vector4f(-20, 0, -10, 1));
            var right = projection.transform(new Vector4f(20, 0, -10, 1));
            var line = MarkerProjection.clip(left, right, 800, 800, zeroToOne);
            assertNotNull(line);
            assertEquals(0, line.x0(), 0.01f); assertEquals(800, line.x1(), 0.01f);
            assertEquals(400, line.y0(), 0.01f); assertEquals(400, line.y1(), 0.01f);
            assertNull(MarkerProjection.clip(projection.transform(new Vector4f(0, 0, 1, 1)),
                    projection.transform(new Vector4f(1, 0, 1, 1)), 800, 800, zeroToOne));
        }
    }

    @Test void nearPlaneCrossingsAndExtremeInputStayFiniteAndBounded() {
        for (boolean zeroToOne : new boolean[]{false, true}) {
            var projection = new Matrix4f().setPerspective((float)Math.PI / 2, 1, 100, 0.05f, zeroToOne);
            var line = MarkerProjection.clip(projection.transform(new Vector4f(0, 0, 1, 1)),
                    projection.transform(new Vector4f(0.1f, 0.1f, -1, 1)), 800, 800, zeroToOne);
            assertNotNull(line);
            for (float value : new float[]{line.x0(), line.y0(), line.x1(), line.y1()}) {
                assertTrue(Float.isFinite(value)); assertTrue(value >= -0.01f && value <= 800.01f);
            }
            assertNull(MarkerProjection.clip(projection.transform(new Vector4f(0, 0, -0.001f, 1)),
                    projection.transform(new Vector4f(0.001f, 0, -0.002f, 1)), 800, 800, zeroToOne));
        }
        assertNull(MarkerProjection.clip(new Vector4f(Float.NaN), new Vector4f(1), 800, 800, true));
        assertNull(MarkerProjection.clip(new Vector4f(1), new Vector4f(1), 0, 0, true));
    }

    @Test void perspectiveAndZoomPreserveScreenCenterAndExpandOffCenterEdges() {
        var normal = new Matrix4f().setPerspective((float)Math.toRadians(70), 16f/9, 100, 0.05f, true);
        var zoom = new Matrix4f().setPerspective((float)(2 * Math.atan(Math.tan(Math.toRadians(35)) / 4)), 16f/9, 100, 0.05f, true);
        var a = new Vector4f(0, 0, -10, 1); var b = new Vector4f(1, 1, -10, 1);
        var n = MarkerProjection.clip(normal.transform(new Vector4f(a)), normal.transform(new Vector4f(b)), 1280, 720, true);
        var z = MarkerProjection.clip(zoom.transform(new Vector4f(a)), zoom.transform(new Vector4f(b)), 1280, 720, true);
        assertNotNull(n); assertNotNull(z);
        assertEquals(640, n.x0(), 0.01); assertEquals(360, n.y0(), 0.01);
        assertEquals((n.x1() - 640) * 4, z.x1() - 640, 0.01);
        assertEquals((360 - n.y1()) * 4, 360 - z.y1(), 0.01);
    }
}
