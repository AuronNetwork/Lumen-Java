// SPDX-License-Identifier: GPL-3.0-only
package net.auronnetwork.lumen;

import org.joml.Vector4f;
import org.joml.Vector4fc;

/** Homogeneous clipping before perspective division, including edges crossing the camera. */
final class MarkerProjection {
    record Segment(float x0, float y0, float x1, float y1) { }

    static Segment clip(Vector4fc a, Vector4fc b, int width, int height, boolean zeroToOne) {
        if (!a.isFinite() || !b.isFinite() || width <= 0 || height <= 0) return null;
        float start = 0, end = 1;
        for (int plane = 0; plane < 7; plane++) {
            float da = distance(a, plane, zeroToOne), db = distance(b, plane, zeroToOne);
            if (da < 0 && db < 0) return null;
            if ((da < 0) != (db < 0)) {
                float t = da / (da - db);
                if (da < 0) start = Math.max(start, t); else end = Math.min(end, t);
                if (start > end) return null;
            }
        }
        var p = new Vector4f(a).lerp(b, start);
        var q = new Vector4f(a).lerp(b, end);
        float x0 = (p.x / p.w + 1) * width * 0.5f, y0 = (1 - p.y / p.w) * height * 0.5f;
        float x1 = (q.x / q.w + 1) * width * 0.5f, y1 = (1 - q.y / q.w) * height * 0.5f;
        if (!Float.isFinite(x0 + y0 + x1 + y1)) return null;
        return new Segment(x0, y0, x1, y1);
    }

    private static float distance(Vector4fc v, int plane, boolean zeroToOne) {
        return switch (plane) {
            case 0 -> v.w() + v.x();
            case 1 -> v.w() - v.x();
            case 2 -> v.w() + v.y();
            case 3 -> v.w() - v.y();
            case 4 -> zeroToOne ? v.z() : v.w() + v.z();
            case 5 -> v.w() - v.z();
            default -> v.w() - 0.00001f;
        };
    }
}
