package timetravel;

import java.awt.Color;
import java.util.*;

/** Карта эпохи. Тайлы */
public class GameMap {
    public static final int TS = 48, W = 26, H = 18;
    public static final int PORTAL_X = 23, PORTAL_Y = 9, SPAWN_X = 2, SPAWN_Y = 9;

    public final int[][] t = new int[H][W];
    public final String name;
    public final int era;
    public final Color f1, f2, wall, bg;
    public final List<Npc> npcs;
    public final List<int[]> buildings = new ArrayList<>();   // c, r, w, h, стиль
    public final List<double[]> decor = new ArrayList<>();    // x, y, вид, вариант
    public final double[][] parts = new double[55][3];        // частицы атмосферы

    public GameMap(int era, String name, long seed, Color f1, Color f2, Color wall, Color bg, Npc... npcs) {
        this.era = era; this.name = name; this.f1 = f1; this.f2 = f2; this.wall = wall; this.bg = bg;
        this.npcs = Arrays.asList(npcs);
        do { generate(seed++); } while (!reachable());
    }

    private List<int[]> pois() {
        List<int[]> l = new ArrayList<>();
        l.add(new int[]{SPAWN_X, SPAWN_Y});
        l.add(new int[]{PORTAL_X, PORTAL_Y});
        for (Npc n : npcs) l.add(new int[]{n.tx, n.ty});
        return l;
    }

    private boolean nearPoi(int x0, int y0, int x1, int y1) {
        for (int[] p : pois()) if (p[0] >= x0 && p[0] <= x1 && p[1] >= y0 && p[1] <= y1) return true;
        return false;
    }

    private void generate(long seed) {
        Random r = new Random(seed);
        buildings.clear(); decor.clear();
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) t[y][x] = (x == 0 || y == 0 || x == W - 1 || y == H - 1) ? 1 : 0;
        int[][] sizes = {{4, 3}, {3, 2}, {3, 3}, {2, 2}, {3, 2}};
        for (int tries = 0; tries < 300 && buildings.size() < 5; tries++) {
            int[] s = sizes[r.nextInt(sizes.length)];
            int w = s[0], h = s[1], c = 2 + r.nextInt(W - 4 - w), rr = 2 + r.nextInt(H - 4 - h);
            if (rr - 1 <= PORTAL_Y && PORTAL_Y <= rr + h) continue;          // дорожка по центру свободна
            if (nearPoi(c - 2, rr - 2, c + w + 1, rr + h + 1)) continue;
            boolean ok = true;
            for (int y = rr - 1; y <= rr + h; y++) for (int x = c - 1; x <= c + w; x++) if (t[y][x] != 0) ok = false;
            if (!ok) continue;
            for (int y = rr; y < rr + h; y++) for (int x = c; x < c + w; x++) t[y][x] = 3;
            int style = era == 0 ? (w == 4 ? 1 : 0) : era == 1 ? (w == 2 ? 1 : 0) : r.nextInt(2);
            buildings.add(new int[]{c, rr, w, h, style});
        }
        for (int y = 1; y < H - 1; y++)
            for (int x = 1; x < W - 1; x++)
                if (t[y][x] == 0 && y != PORTAL_Y && !nearPoi(x - 1, y - 1, x + 1, y + 1) && r.nextDouble() < 0.06) t[y][x] = 2;
        for (int i = 0; i < 140; i++) {
            int c = 1 + r.nextInt(W - 2), rr = 1 + r.nextInt(H - 2);
            if (t[rr][c] == 0) decor.add(new double[]{c * TS + r.nextInt(TS - 10), rr * TS + 8 + r.nextInt(TS - 16), r.nextInt(3), r.nextDouble()});
        }
        for (double[] q : parts) { q[0] = r.nextDouble() * W * TS; q[1] = r.nextDouble() * H * TS; q[2] = r.nextDouble(); }
    }

    private boolean reachable() {
        boolean[][] seen = new boolean[H][W];
        Deque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{SPAWN_X, SPAWN_Y}); seen[SPAWN_Y][SPAWN_X] = true;
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] s : d) {
                int nx = c[0] + s[0], ny = c[1] + s[1];
                if (t[ny][nx] == 0 && !seen[ny][nx]) { seen[ny][nx] = true; q.add(new int[]{nx, ny}); }
            }
        }
        for (int[] p : pois()) if (!seen[p[1]][p[0]]) return false;
        return true;
    }

    public boolean solid(double px, double py) {
        int c = (int) Math.floor(px / TS), r = (int) Math.floor(py / TS);
        return c < 0 || r < 0 || c >= W || r >= H || t[r][c] != 0;
    }

    public boolean allDone() { for (Npc n : npcs) if (!n.done) return false; return true; }
    public double portalX() { return (PORTAL_X + 0.5) * TS; }
    public double portalY() { return (PORTAL_Y + 0.5) * TS; }

    public static GameMap[] build() {
        return new GameMap[]{
            new GameMap(0, "Древний Египет", 11, new Color(0xE3C98F), new Color(0xDAB97C), new Color(0xB48A48), new Color(0x1B1408),
                new Npc("Жрец Хети", 6, 5, new Color(0x3B6EA5), 0), new Npc("Строитель Рамес", 19, 12, new Color(0xB5651D), 4)),
            new GameMap(1, "Средневековье", 22, new Color(0x7FA36B), new Color(0x759A62), new Color(0x7A7A80), new Color(0x0E1A10),
                new Npc("Рыцарь Эдмунд", 19, 5, new Color(0xA03030), 6), new Npc("Алхимик Бруно", 6, 12, new Color(0x6B3FA0), 9)),
            new GameMap(2, "Далёкое будущее", 33, new Color(0x2B3550), new Color(0x263049), new Color(0x4C5B80), new Color(0x05070F),
                new Npc("Робот ZX-9", 6, 5, new Color(0x38C6D9), 10), new Npc("Доктор Нова", 19, 12, new Color(0xE05C9A), 11))
        };
    }
}
