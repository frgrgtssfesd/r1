package timetravel;

import java.awt.Color;
import java.util.Random;

/** Персонаж, который бродит возле своего места*/
public class Npc {
    public final String name;
    public final int tx, ty, startEvent;
    public final Color color;
    public boolean done, moving, frozen;
    public int facing = 1;
    public double x, y, anim;
    private double gx, gy, wait = 1, stuck;

    public Npc(String name, int tx, int ty, Color color, int startEvent) {
        this.name = name; this.tx = tx; this.ty = ty; this.color = color; this.startEvent = startEvent;
        x = gx = (tx + 0.5) * GameMap.TS;
        y = gy = (ty + 0.5) * GameMap.TS;
    }

    public double x() { return x; }
    public double y() { return y; }

    private void pickTarget(GameMap m, Random r) {
        for (int i = 0; i < 12; i++) {
            int c = tx + r.nextInt(7) - 3, rr = ty + r.nextInt(7) - 3;
            if (c > 0 && rr > 0 && c < GameMap.W - 1 && rr < GameMap.H - 1 && m.t[rr][c] == 0) {
                gx = (c + 0.5) * GameMap.TS; gy = (rr + 0.5) * GameMap.TS; return;
            }
        }
    }

    private boolean free(GameMap m, double px, double py) {
        double r = 10;
        return !m.solid(px - r, py - 4) && !m.solid(px + r, py - 4) && !m.solid(px - r, py + r) && !m.solid(px + r, py + r);
    }

    public void update(GameMap m, Random rnd) {
        if (frozen) { moving = false; return; }
        if (wait > 0) { wait -= 0.016; moving = false; return; }
        double dx = gx - x, dy = gy - y, d = Math.hypot(dx, dy);
        if (d < 3) { pickTarget(m, rnd); wait = 1 + rnd.nextDouble() * 3; moving = false; return; }
        double vx = dx / d * 1.1, vy = dy / d * 1.1;
        boolean mv = false;
        if (free(m, x + vx, y)) { x += vx; mv = true; }
        if (free(m, x, y + vy)) { y += vy; mv = true; }
        moving = mv;
        if (mv) { anim += 0.16; stuck = 0; if (Math.abs(vx) > 0.2) facing = vx > 0 ? 1 : -1; }
        else if ((stuck += 0.016) > 0.5) { pickTarget(m, rnd); stuck = 0; }
    }
}
