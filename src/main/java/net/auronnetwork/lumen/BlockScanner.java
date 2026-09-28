// SPDX-License-Identifier: GPL-3.0-only
package net.auronnetwork.lumen;

import java.util.*;
import java.util.function.Function;

/** Bounded, client-thread-only scan of already loaded blocks. No chunk requests. */
public final class BlockScanner {
    public record Position(int x, int y, int z) {
        public double distanceSquared(Position other) {
            double dx = (double)x - other.x, dy = (double)y - other.y, dz = (double)z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
        Position plus(Position offset) { return new Position(x + offset.x, y + offset.y, z + offset.z); }
    }
    public record Hit(Position position, BlockKind kind) {}
    private final Map<Position, BlockKind> found = new LinkedHashMap<>();
    private final List<Position> offsets = new ArrayList<>();
    private Position anchor;
    private int radius, mask, cursor, refresh;
    public void clear() { found.clear(); offsets.clear(); anchor = null; cursor = refresh = 0; }

    public int tick(Position player, int requestedRadius, int requestedMask, int budget,
                    long timeBudgetNanos, Function<Position, BlockKind> reader) {
        int r = Math.clamp(requestedRadius, 4, 24), m = requestedMask & BlockKind.SUPPORTED_MASK;
        if (m == 0) { clear(); return 0; }
        if (anchor == null || radius != r || mask != m || player.distanceSquared(anchor) > r * r) {
            clear(); anchor = player; radius = r; mask = m;
            for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++)
                if (x*x + y*y + z*z <= r*r) offsets.add(new Position(x, y, z));
            offsets.sort(Comparator.comparingDouble(p -> p.distanceSquared(new Position(0, 0, 0))));
        }
        found.keySet().removeIf(p -> p.distanceSquared(player) > r * r);
        long start = System.nanoTime();
        int used = 0, cap = Math.clamp(budget, 1, 3000);
        var known = new ArrayList<>(found.keySet());
        int count = Math.min(known.size(), Math.min(64, Math.max(1, cap / 4)));
        for (int i = 0; i < count && used < cap && System.nanoTime() - start < timeBudgetNanos; i++) {
            query(known.get(Math.floorMod(refresh++, known.size())), reader); used++;
        }
        while (used < cap && System.nanoTime() - start < timeBudgetNanos) {
            if (cursor == offsets.size()) { cursor = 0; anchor = player; }
            Position p = anchor.plus(offsets.get(cursor++));
            if (p.distanceSquared(player) <= r * r) { query(p, reader); used++; }
        }
        return used;
    }
    private void query(Position p, Function<Position, BlockKind> reader) {
        BlockKind kind = reader.apply(p);
        if (kind != null && (mask & kind.bit()) != 0) {
            if (found.size() < 4096 || found.containsKey(p)) found.put(p, kind);
        } else found.remove(p);
    }
    public List<Hit> visible(Position player, int limit) {
        return found.entrySet().stream().filter(e -> e.getKey().distanceSquared(player) <= radius * radius)
                .sorted(Comparator.comparingDouble(e -> e.getKey().distanceSquared(player)))
                .limit(Math.clamp(limit, 0, 512)).map(e -> new Hit(e.getKey(), e.getValue())).toList();
    }
}
