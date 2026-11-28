package dev.emotewheel;

/** Places n wheel slots on a ring around (cx, cy) of a 9x5 chest grid. Slot index = row * 9 + col. */
final class Layout {
    private Layout() {}

    static int[] ring(int n, int cx, int cy, int minCol, int maxCol) {
        boolean[] used = new boolean[54];
        used[cy * 9 + cx] = true;
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            double tx = cx + 2.1 * Math.sin(a), ty = cy - 2.1 * Math.cos(a);
            int best = -1;
            double bd = 1e9;
            for (int row = 0; row <= 4; row++) {
                for (int col = minCol; col <= maxCol; col++) {
                    int s = row * 9 + col;
                    if (used[s]) continue;
                    double d = Math.hypot(col - tx, row - ty);
                    if (d < bd) { bd = d; best = s; }
                }
            }
            used[best] = true;
            out[i] = best;
        }
        return out;
    }

    /** Pool grid of the equip screen: columns 5..8, rows 0..4 (20 slots). */
    static int pool(int k) { return (k / 4) * 9 + 5 + (k % 4); }
}
