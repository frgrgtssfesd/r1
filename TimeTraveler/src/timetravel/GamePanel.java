package timetravel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/** Игровой цикл, ввод (WASD / E / 1-3 / J) **/
public class GamePanel extends JPanel implements ActionListener, KeyListener {
    static final int W = 960, H = 640, TS = GameMap.TS;
    enum S { MENU, PLAY, DIALOG, JOURNAL, END }

    S state = S.MENU;
    Player p = new Player();
    GameMap[] maps;
    int era, ev = -1;
    Npc talking;
    double camX, camY, t;
    String hint = "", endTitle = "", endText = "";
    int hintT;
    final Set<Integer> keys = new HashSet<>();
    final Random rnd = new Random();
    final javax.swing.Timer timer = new javax.swing.Timer(16, this);

    public GamePanel() {
        setPreferredSize(new Dimension(W, H));
        setFocusable(true);
        addKeyListener(this);
        reset();
    }

    public void start() { timer.start(); requestFocusInWindow(); }

    void reset() { p = new Player(); maps = GameMap.build(); enter(0); }

    void enter(int e) {
        era = e;
        p.x = (GameMap.SPAWN_X + 0.5) * TS; p.y = (GameMap.SPAWN_Y + 0.5) * TS;
        camX = Math.max(0, p.x - W / 2.0); camY = Math.max(0, p.y - H / 2.0);
        say("Эпоха: " + maps[e].name);
    }

    void say(String s) { hint = s; hintT = 180; }

    // ---------- логика ----------
    @Override public void actionPerformed(ActionEvent e) {
        t += 0.016;
        if (hintT > 0) hintT--;
        if (state == S.PLAY) update();
        if (state == S.PLAY || state == S.DIALOG)
            for (Npc n : maps[era].npcs) { n.frozen = state == S.DIALOG && n == talking; n.update(maps[era], rnd); }
        repaint();
    }

    void update() {
        double dx = 0, dy = 0;
        if (keys.contains(KeyEvent.VK_W) || keys.contains(KeyEvent.VK_UP)) dy -= 1;
        if (keys.contains(KeyEvent.VK_S) || keys.contains(KeyEvent.VK_DOWN)) dy += 1;
        if (keys.contains(KeyEvent.VK_A) || keys.contains(KeyEvent.VK_LEFT)) dx -= 1;
        if (keys.contains(KeyEvent.VK_D) || keys.contains(KeyEvent.VK_RIGHT)) dx += 1;
        GameMap m = maps[era];
        p.moving = dx != 0 || dy != 0;
        if (dx != 0 || dy != 0) {
            double len = Math.hypot(dx, dy), sp = 3.4;
            dx = dx / len * sp; dy = dy / len * sp;
            if (dx != 0) p.facing = dx > 0 ? 1 : -1;
            p.anim += 0.25;
            if (free(m, p.x + dx, p.y)) p.x += dx;
            if (free(m, p.x, p.y + dy)) p.y += dy;
        }
        camX += (p.x - W / 2.0 - camX) * 0.12;
        camY += (p.y - H / 2.0 - camY) * 0.12;
        camX = Math.max(0, Math.min(GameMap.W * TS - W, camX));
        camY = Math.max(0, Math.min(GameMap.H * TS - H, camY));
        if (Math.hypot(p.x - m.portalX(), p.y - m.portalY()) < 28) {
            if (!m.allDone()) { if (hintT < 30) say("Портал закрыт: поговори со всеми жителями эпохи (E)"); }
            else if (era < 2) enter(era + 1);
            else finish();
        }
    }

    boolean free(GameMap m, double x, double y) {
        double r = 11;
        return !m.solid(x - r, y - 4) && !m.solid(x + r, y - 4) && !m.solid(x - r, y + r) && !m.solid(x + r, y + r);
    }

    Npc nearNpc() {
        for (Npc n : maps[era].npcs) if (Math.hypot(p.x - n.x(), p.y - n.y()) < 64) return n;
        return null;
    }

    void choose(int i) {
        Story.Choice c = Story.EV[ev].choices()[i];
        p.apply(c.health(), c.knowledge(), c.resources());
        p.log.add(maps[era].name + " / " + talking.name + ": " + c.text());
        if (p.dead()) { finish(); return; }
        if (c.next() < 0) { talking.done = true; state = S.PLAY; say(maps[era].allDone() ? "Портал открыт!" : "Диалог завершён"); }
        else ev = c.next();
    }

    void finish() {
        String[] e = Story.ending(p);
        endTitle = e[0]; endText = e[1];
        ResultWriter.save(p, endTitle);
        state = S.END;
    }

    @Override public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        keys.add(k);
        switch (state) {
            case MENU -> { if (k == KeyEvent.VK_ENTER) state = S.PLAY; }
            case PLAY -> {
                if (k == KeyEvent.VK_E) {
                    Npc n = nearNpc();
                    if (n == null) say("Рядом никого нет");
                    else if (n.done) say(n.name + ": «Мы уже всё обсудили, путник»");
                    else { talking = n; n.facing = p.x > n.x() ? 1 : -1; ev = n.startEvent; state = S.DIALOG; }
                } else if (k == KeyEvent.VK_J) state = S.JOURNAL;
            }
            case DIALOG -> {
                int i = k - KeyEvent.VK_1;
                if (i >= 0 && i < Story.EV[ev].choices().length) choose(i);
            }
            case JOURNAL -> state = S.PLAY;
            case END -> { if (k == KeyEvent.VK_ENTER) { reset(); state = S.MENU; } }
        }
    }
    @Override public void keyReleased(KeyEvent e) { keys.remove(e.getKeyCode()); }
    @Override public void keyTyped(KeyEvent e) {}

    // ---------- отрисовка ----------
    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (state == S.MENU) { drawMenu(g); return; }
        if (state == S.END) { drawEnd(g); return; }
        drawWorld(g);
        drawHud(g);
        if (state == S.DIALOG) drawDialog(g);
        if (state == S.JOURNAL) drawJournal(g);
    }

    static Color adj(Color c, int d) {
        return new Color(Math.max(0, Math.min(255, c.getRed() + d)), Math.max(0, Math.min(255, c.getGreen() + d)), Math.max(0, Math.min(255, c.getBlue() + d)));
    }
    static int hash(int a, int b) { return Math.abs((a * 73856093) ^ (b * 19349663)) % 256; }
    static double mod(double v, double m) { return ((v % m) + m) % m; }

    void drawWorld(Graphics2D g) {
        GameMap m = maps[era];
        g.setColor(m.bg); g.fillRect(0, 0, W, H);
        AffineTransform old = g.getTransform();
        g.translate(-(int) camX, -(int) camY);
        int c0 = Math.max(0, (int) camX / TS), c1 = Math.min(GameMap.W - 1, ((int) camX + W) / TS + 1);
        int r0 = Math.max(0, (int) camY / TS), r1 = Math.min(GameMap.H - 1, ((int) camY + H) / TS + 1);
        for (int r = r0; r <= r1; r++)
            for (int c = c0; c <= c1; c++) {
                g.setColor(adj((r + c) % 2 == 0 ? m.f1 : m.f2, hash(c, r) % 14 - 7));
                g.fillRect(c * TS, r * TS, TS, TS);
                if (era == 2) { g.setColor(new Color(90, 200, 255, 28)); g.drawRect(c * TS, r * TS, TS, TS); }
            }
        drawPath(g);
        for (double[] d : m.decor) drawDecor(g, d);
        drawPortal(g, m);
        Npc near = nearNpc();
        for (int r = 0; r < GameMap.H; r++) {
            for (int c = 0; c < GameMap.W; c++) if (m.t[r][c] == 1 || m.t[r][c] == 2) drawBlock(g, m, c, r);
            for (int[] b : m.buildings) if (b[1] + b[3] - 1 == r) drawBuilding(g, b);
            for (Npc n : m.npcs) if ((int) (n.y() / TS) == r) {
                drawChar(g, n.x(), n.y(), n.color, n.name, n.anim, n.facing, n.moving, n.startEvent % 3, false);
                if (!n.done) {
                    g.setFont(new Font("SansSerif", Font.BOLD, 26));
                    int ey = (int) (n.y() - 118 + Math.sin(t * 4) * 4);
                    g.setColor(new Color(0, 0, 0, 160)); g.drawString("!", (int) n.x() - 3, ey + 2);
                    g.setColor(new Color(255, 220, 60)); g.drawString("!", (int) n.x() - 4, ey);
                }
                if (n == near && !n.done) {
                    g.setFont(new Font("SansSerif", Font.BOLD, 13));
                    g.setColor(new Color(0, 0, 0, 150)); g.fillRoundRect((int) n.x() - 44, (int) n.y() + 12, 88, 20, 10, 10);
                    g.setColor(Color.WHITE); g.drawString("[E] говорить", (int) n.x() - 35, (int) n.y() + 27);
                }
            }
            if ((int) (p.y / TS) == r) drawChar(g, p.x, p.y, new Color(0xE8B030), "Путешественник", p.anim, p.facing, p.moving, 3, true);
        }
        for (double[] q : m.parts) {
            if (era == 0) {
                g.setColor(new Color(240, 210, 150, 90));
                g.fillOval((int) mod(q[0] + t * (18 + q[2] * 30), GameMap.W * TS), (int) (q[1] + Math.sin(t + q[2] * 9) * 8), 3, 3);
            } else if (era == 1) {
                double px = q[0] + Math.sin(t * 0.6 + q[2] * 9) * 30, py = q[1] + Math.cos(t * 0.5 + q[2] * 7) * 20;
                int a = Math.max(10, Math.min(255, (int) (120 + 110 * Math.sin(t * 3 + q[2] * 20))));
                g.setColor(new Color(255, 240, 120, a / 4)); g.fillOval((int) px - 7, (int) py - 7, 14, 14);
                g.setColor(new Color(255, 250, 170, a)); g.fillOval((int) px - 1, (int) py - 1, 3, 3);
            } else {
                g.setColor(new Color(120, 230, 255, 150));
                g.fillRect((int) q[0], (int) mod(q[1] - t * (15 + q[2] * 25), GameMap.H * TS), 2, 2);
            }
        }
        g.setTransform(old);
        g.setColor(era == 0 ? new Color(255, 170, 60, 26) : era == 1 ? new Color(255, 245, 190, 14) : new Color(40, 90, 255, 40));
        g.fillRect(0, 0, W, H);
        g.setPaint(new RadialGradientPaint(W / 2f, H / 2f, W * 0.7f, new float[]{0.55f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 170)}));
        g.fillRect(0, 0, W, H);
    }

    void drawPath(Graphics2D g) {
        int y = GameMap.PORTAL_Y * TS + 8, x0 = GameMap.SPAWN_X * TS, x1 = (GameMap.PORTAL_X + 1) * TS;
        g.setColor(era == 0 ? new Color(0xEBD9AA) : era == 1 ? new Color(0x9C8458) : new Color(0x1B2440));
        g.fillRoundRect(x0, y, x1 - x0, TS - 16, 24, 24);
        g.setColor(era == 2 ? new Color(80, 220, 255, 150) : new Color(0, 0, 0, 40));
        g.setStroke(new BasicStroke(2)); g.drawRoundRect(x0, y, x1 - x0, TS - 16, 24, 24); g.setStroke(new BasicStroke(1));
        g.setColor(new Color(0, 0, 0, 30));
        for (int x = x0 + 20; x < x1 - 10; x += 36) g.drawLine(x, y + 4, x, y + TS - 20);
    }

    void drawDecor(Graphics2D g, double[] d) {
        int x = (int) d[0], y = (int) d[1], k = (int) d[2];
        switch (era) {
            case 0 -> {
                if (k == 0) { g.setColor(new Color(120, 100, 70, 150)); g.fillOval(x, y, 7, 4); }
                else if (k == 1) { g.setColor(new Color(110, 80, 40, 170)); g.drawLine(x, y, x - 4, y - 8); g.drawLine(x, y, x, y - 10); g.drawLine(x, y, x + 4, y - 7); }
                else { g.setColor(new Color(255, 240, 200, 70)); g.fillOval(x, y, 22, 8); }
            }
            case 1 -> {
                if (k == 0) { g.setColor(new Color(40, 110, 50, 190)); g.drawLine(x, y, x - 3, y - 7); g.drawLine(x, y, x, y - 9); g.drawLine(x, y, x + 3, y - 7); }
                else if (k == 1) { g.setColor(new Color(40, 110, 50)); g.drawLine(x, y, x, y - 6); g.setColor(Color.getHSBColor((float) d[3], 0.6f, 1f)); g.fillOval(x - 3, y - 10, 6, 6); }
                else { g.setColor(new Color(110, 110, 115)); g.fillOval(x, y, 8, 5); }
            }
            default -> {
                if (k == 0) { g.setColor(new Color(120, 230, 255, 120)); g.fillOval(x, y, 4, 4); }
                else if (k == 1) { g.setColor(new Color(15, 20, 40, 120)); g.fillRect(x, y, 18, 10); }
                else { g.setColor(new Color(255, 140, 60, 200)); g.fillOval(x, y, 3, 3); }
            }
        }
    }

    void drawPortal(Graphics2D g, GameMap m) {
        double cx = m.portalX(), cy = m.portalY();
        boolean on = m.allDone();
        Color gc = on ? new Color(90, 190, 255) : new Color(150, 150, 160);
        g.setPaint(new RadialGradientPaint((float) cx, (float) cy, 110, new float[]{0f, 1f},
                new Color[]{new Color(gc.getRed(), gc.getGreen(), gc.getBlue(), on ? 110 : 40), new Color(gc.getRed(), gc.getGreen(), gc.getBlue(), 0)}));
        g.fillOval((int) cx - 110, (int) cy - 70, 220, 140);
        g.setColor(new Color(60, 60, 70)); g.fillOval((int) cx - 34, (int) cy - 18, 68, 36);
        for (int i = 0; i < 5; i++) {
            double rad = 14 + i * 6 + Math.sin(t * 3 + i) * 3;
            g.setColor(on ? new Color(110 + i * 20, 180, 255, 130 - i * 20) : new Color(130, 130, 130, 80 - i * 12));
            g.fill(new Ellipse2D.Double(cx - rad, cy - rad * 0.6, rad * 2, rad * 1.2));
        }
        if (on) for (int i = 0; i < 12; i++) {
            double a = t * 1.5 + i * 0.52, rr = 34 + 6 * Math.sin(t * 2 + i);
            g.setColor(new Color(200, 240, 255, 210));
            g.fillOval((int) (cx + Math.cos(a) * rr), (int) (cy + Math.sin(a) * rr * 0.6 - ((t * 30 + i * 9) % 34)), 4, 4);
        }
    }

    void drawBlock(Graphics2D g, GameMap m, int c, int r) {
        int x = c * TS, y = r * TS;
        if (m.t[r][c] == 1) {
            g.setPaint(new GradientPaint(x, y - 20, m.wall.brighter(), x, y + TS - 20, m.wall)); g.fillRect(x, y - 20, TS, TS);
            g.setColor(m.wall.darker().darker()); g.fillRect(x, y + TS - 20, TS, 20);
            g.setColor(new Color(0, 0, 0, 50)); g.drawRect(x, y - 20, TS - 1, TS - 1); g.drawLine(x, y + TS - 8, x + TS, y + TS - 8);
            if (era == 1) { g.setColor(m.wall.brighter()); g.fillRect(x + 4, y - 26, 10, 8); g.fillRect(x + 30, y - 26, 10, 8); }
            else if (era == 2) { g.setColor(new Color(90, 220, 255, 170)); g.fillRect(x + 6, y + TS - 14, TS - 12, 3); }
            else { g.setColor(new Color(0, 0, 0, 35)); g.drawLine(x + TS / 2, y - 20, x + TS / 2, y + TS - 20); }
            return;
        }
        g.setColor(new Color(0, 0, 0, 70)); g.fillOval(x + 4, y + TS - 18, TS - 8, 14);
        double sway = Math.sin(t + c * 1.7);
        switch (era) {
            case 0 -> { // пальма
                int tx = x + 26, ty = y - 36;
                g.setColor(new Color(0x6B4A2A)); g.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new QuadCurve2D.Double(x + 22, y + TS - 8, x + 30, y + 10, tx, ty));
                g.setColor(new Color(0x2F8A3A)); g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i <= 6; i++) {
                    double a = i * Math.PI / 6;
                    g.draw(new QuadCurve2D.Double(tx, ty, tx + Math.cos(a) * 16, ty - 10 - Math.sin(a) * 6,
                            tx + Math.cos(a) * 30 + sway * 2, ty - Math.sin(a) * 10 + 14));
                }
                g.setStroke(new BasicStroke(1));
            }
            case 1 -> { // дерево
                g.setColor(new Color(0x5B3A1E)); g.fillRoundRect(x + 19, y - 6, 10, TS - 4, 4, 4);
                int[][] cs = {{24, -22, 22}, {10, -8, 18}, {38, -8, 18}};
                for (int[] k : cs) {
                    float cx = x + k[0] + (float) sway, cy = y + k[1];
                    g.setPaint(new RadialGradientPaint(cx - 4, cy - 6, k[2], new float[]{0, 1}, new Color[]{new Color(0x62B85A), new Color(0x1F5A2B)}));
                    g.fillOval((int) cx - k[2], (int) cy - k[2], k[2] * 2, k[2] * 2);
                }
            }
            default -> { // кристалл
                int a = (int) (60 + 40 * Math.sin(t * 2 + c));
                g.setPaint(new RadialGradientPaint(x + 24, y + 10, 40, new float[]{0, 1}, new Color[]{new Color(100, 220, 255, a), new Color(100, 220, 255, 0)}));
                g.fillOval(x - 16, y - 30, 80, 80);
                int[][] cs = {{24, 40, 14, 62}, {10, 42, 8, 34}, {38, 42, 8, 26}};
                for (int[] k : cs) {
                    Path2D.Double s = new Path2D.Double();
                    s.moveTo(x + k[0], y + k[1] - k[3]); s.lineTo(x + k[0] + k[2], y + k[1]);
                    s.lineTo(x + k[0], y + k[1] + 4); s.lineTo(x + k[0] - k[2], y + k[1]); s.closePath();
                    g.setPaint(new GradientPaint(x + k[0] - k[2], y, new Color(0x9FF7FF), x + k[0] + k[2], y + 40, new Color(0x2A5BD7)));
                    g.fill(s);
                }
            }
        }
    }

    void drawBuilding(Graphics2D g, int[] b) {
        int x = b[0] * TS, bw = b[2] * TS, bh = b[3] * TS, yb = b[1] * TS + bh, st = b[4], cx = x + bw / 2;
        g.setColor(new Color(0, 0, 0, 80)); g.fillOval(x - 6, yb - 16, bw + 40, 32);
        if (era == 0 && st == 1) { // пирамида
            int ay = yb - bh - 70;
            g.setColor(new Color(0xEAD08F)); g.fillPolygon(new int[]{x, cx, cx}, new int[]{yb - 6, yb + 8, ay}, 3);
            g.setColor(new Color(0xB38F4E)); g.fillPolygon(new int[]{cx, x + bw, cx}, new int[]{yb + 8, yb - 6, ay}, 3);
            g.setColor(new Color(0, 0, 0, 40));
            for (int i = 1; i < 8; i++) {
                double f = i / 8.0;
                int ly = (int) (yb - 6 + (ay - (yb - 6)) * f), my = (int) (yb + 8 + (ay - (yb + 8)) * f);
                g.drawLine((int) (x + (cx - x) * f), ly, cx, my); g.drawLine(cx, my, (int) (x + bw - (bw / 2.0) * f), ly);
            }
            g.setColor(new Color(0xFFD34D)); g.fillPolygon(new int[]{cx - 6, cx + 6, cx}, new int[]{ay + 12, ay + 12, ay}, 3);
        } else if (era == 0) { // дом из песчаника
            int h = 46;
            g.setPaint(new GradientPaint(x, yb - h, new Color(0xEBD7A8), x, yb, new Color(0xC7A56A))); g.fillRect(x, yb - h, bw, h);
            g.setColor(new Color(0, 0, 0, 35)); for (int ly = yb - h + 12; ly < yb; ly += 12) g.drawLine(x, ly, x + bw, ly);
            g.setColor(new Color(0xF2E3B8)); g.fillRect(x - 4, yb - h - 14, bw + 8, 16);
            g.setColor(new Color(0, 0, 0, 50)); g.drawRect(x - 4, yb - h - 14, bw + 8, 16);
            for (int i = x + 6, n = 0; i < x + bw - 10; i += 14, n++) { g.setColor(n % 2 == 0 ? new Color(0x2A6FB0) : new Color(0xB8402E)); g.fillRect(i, yb - h + 3, 8, 4); }
            g.setColor(new Color(0x3A2412)); g.fillRoundRect(cx - 11, yb - 28, 22, 28, 12, 12);
            g.fillRect(x + 10, yb - 32, 8, 12); g.fillRect(x + bw - 18, yb - 32, 8, 12);
        } else if (era == 1 && st == 1) { // каменная башня
            int h = 84;
            g.setPaint(new GradientPaint(x, 0, new Color(0xA9A9B0), x + bw, 0, new Color(0x6A6A72))); g.fillRect(x, yb - h, bw, h);
            g.setColor(new Color(0, 0, 0, 40)); for (int ly = yb - h + 10; ly < yb; ly += 10) g.drawLine(x, ly, x + bw, ly);
            g.setColor(new Color(0x8C8C94)); for (int i = 0; i < 5; i++) g.fillRect(x + i * bw / 5 + 2, yb - h - 10, bw / 5 - 6, 12);
            g.setColor(new Color(0x1B1B22)); g.fillRoundRect(cx - 5, yb - h + 20, 10, 20, 6, 6); g.fillRoundRect(cx - 12, yb - 28, 24, 28, 12, 12);
            g.setColor(new Color(0x4A2E18)); g.drawLine(cx, yb - h - 10, cx, yb - h - 48);
            g.setColor(new Color(0xC83030));
            g.fillPolygon(new int[]{cx, cx + 26, cx}, new int[]{yb - h - 48, yb - h - 40 + (int) (Math.sin(t * 4) * 3), yb - h - 32}, 3);
        } else if (era == 1) { // фахверковый дом
            int h = 40, rt = yb - h - 30;
            g.setPaint(new GradientPaint(x, yb - h, new Color(0xF0E2C0), x, yb, new Color(0xCDBB93))); g.fillRect(x, yb - h, bw, h);
            g.setColor(new Color(0x5A3B22)); g.fillRect(x, yb - h, bw, 4); g.fillRect(x, yb - 4, bw, 4);
            for (int i = 0; i <= 3; i++) g.fillRect(x + i * (bw - 4) / 3, yb - h, 4, h);
            g.setColor(new Color(0xA8442F));
            g.fillPolygon(new int[]{x - 10, x + bw + 10, x + bw - 14, x + 14}, new int[]{yb - h + 4, yb - h + 4, rt, rt}, 4);
            g.setColor(new Color(0, 0, 0, 45)); for (int ly = rt + 8; ly < yb - h + 4; ly += 8) g.drawLine(x - 6, ly, x + bw + 6, ly);
            g.setColor(new Color(0x77736E)); g.fillRect(x + bw - 34, rt - 14, 12, 20);
            g.setColor(new Color(0x4A2E18)); g.fillRoundRect(cx - 10, yb - 26, 20, 26, 10, 10);
            g.setColor(new Color(0xFFD477)); g.fillRect(x + 10, yb - 32, 10, 12); g.fillRect(x + bw - 20, yb - 32, 10, 12);
        } else if (st == 0) { // купол
            g.setPaint(new GradientPaint(x, yb - 26, new Color(0x3A4468), x, yb, new Color(0x1A2038))); g.fillRect(x, yb - 26, bw, 26);
            g.setColor(new Color(90, 220, 255)); g.fillRect(x, yb - 28, bw, 3);
            for (int i = x + 10; i < x + bw - 10; i += 16) { g.setColor(new Color(120, 230, 255, 200)); g.fillOval(i, yb - 18, 8, 8); }
            Arc2D dome = new Arc2D.Double(x + 6, yb - 26 - bw * 0.42, bw - 12, bw * 0.84, 0, 180, Arc2D.CHORD);
            g.setPaint(new GradientPaint(x, yb - bw, new Color(0xE0FBFF), x + bw, yb, new Color(0x3C7FD8))); g.fill(dome);
            g.setColor(new Color(255, 255, 255, 120)); g.setStroke(new BasicStroke(2)); g.draw(dome); g.setStroke(new BasicStroke(1));
        } else { // технологичный блок
            int h = 66;
            g.setPaint(new GradientPaint(x, yb - h, new Color(0x2A3350), x, yb, new Color(0x151B30))); g.fillRect(x, yb - h, bw, h);
            g.setColor(new Color(0x4C5B80)); g.fillRect(x - 3, yb - h - 14, bw + 6, 16);
            g.setColor(new Color(90, 220, 255, 190)); for (int ly = yb - h + 14; ly < yb - 20; ly += 16) g.fillRect(x + 8, ly, bw - 16, 2);
            g.setColor(new Color(0x38C6D9)); g.fillRect(cx - 9, yb - 26, 18, 26);
            g.setColor(new Color(0x9EA8C8)); g.drawLine(x + bw - 12, yb - h - 14, x + bw - 12, yb - h - 40);
            if (Math.sin(t * 5) > 0) { g.setColor(Color.RED); g.fillOval(x + bw - 15, yb - h - 44, 6, 6); }
        }
    }

    void drawChar(Graphics2D g, double x, double y, Color body, String name, double anim, int face, boolean mv, int look, boolean hero) {
        int px = (int) x, py = (int) y;
        double sw = mv ? Math.sin(anim) * 5 : 0;
        int bob = mv ? -(int) (Math.abs(Math.sin(anim)) * 3) : (int) Math.round(Math.sin(t * 2));
        g.setColor(new Color(0, 0, 0, 90)); g.fillOval(px - 15, py - 5, 30, 12);
        if (hero) { g.setColor(new Color(80, 255, 130, 170)); g.setStroke(new BasicStroke(2)); g.drawOval(px - 21, py - 9, 42, 18); g.setStroke(new BasicStroke(1)); }
        Color dark = body.darker().darker();
        g.setColor(dark);
        g.fillRoundRect(px - 8, py - 16 - (int) Math.max(0, sw), 6, 16, 3, 3);
        g.fillRoundRect(px + 2, py - 16 - (int) Math.max(0, -sw), 6, 16, 3, 3);
        g.setColor(body.darker());
        g.fillRoundRect(px - 16, py - 42 + bob + (int) (sw * 0.6), 6, 18, 4, 4);
        g.fillRoundRect(px + 10, py - 42 + bob - (int) (sw * 0.6), 6, 18, 4, 4);
        g.setPaint(new GradientPaint(px - 12, py - 44, body.brighter(), px + 12, py - 14, body.darker()));
        g.fillRoundRect(px - 12, py - 44 + bob, 24, 32, 12, 12);
        g.setColor(new Color(0, 0, 0, 110)); g.drawRoundRect(px - 12, py - 44 + bob, 24, 32, 12, 12);
        g.setColor(new Color(0x4A2E18)); g.fillRect(px - 12, py - 24 + bob, 24, 4);
        g.setColor(new Color(0xFFD866)); g.fillRect(px - 2, py - 24 + bob, 4, 4);
        g.setColor(new Color(0xF2CDA0)); g.fillOval(px - 9, py - 62 + bob, 18, 18);
        g.setColor(new Color(0, 0, 0, 110)); g.drawOval(px - 9, py - 62 + bob, 18, 18);
        g.setColor(Color.BLACK); g.fillOval(px + face * 4 - 1, py - 55 + bob, 3, 3);
        switch (look) {
            case 0 -> { g.setColor(dark); g.fillPolygon(new int[]{px - 12, px + 12, px}, new int[]{py - 54 + bob, py - 54 + bob, py - 82 + bob}, 3); g.fillRect(px - 13, py - 56 + bob, 26, 4); }
            case 1 -> { g.setColor(new Color(0xB8BCC4)); g.fillArc(px - 10, py - 65 + bob, 20, 18, 0, 180); g.setColor(new Color(0xD03030)); g.fillRect(px - 2, py - 72 + bob, 4, 10); }
            case 2 -> { g.setColor(dark); g.fillArc(px - 11, py - 65 + bob, 22, 20, 0, 180); }
            default -> { g.setColor(new Color(0x5A3A1E)); g.fillArc(px - 10, py - 65 + bob, 20, 16, 0, 180); g.setColor(new Color(0xD03030)); g.fillRect(px - 10, py - 57 + bob, 20, 3); }
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        int w = g.getFontMetrics().stringWidth(name);
        g.setColor(new Color(0, 0, 0, 130)); g.fillRoundRect(px - w / 2 - 5, py - 105, w + 10, 17, 8, 8);
        g.setColor(Color.WHITE); g.drawString(name, px - w / 2, py - 92);
        if (hero) {
            g.setColor(new Color(0, 0, 0, 170)); g.fillRect(px - 22, py - 114, 44, 7);
            g.setColor(new Color(0x3CCB4F)); g.fillRect(px - 21, py - 113, (int) (42.0 * p.health / 100), 5);
        }
    }

    void drawMinimap(Graphics2D g) {
        GameMap m = maps[era];
        int mx = 14, my = H - 112, s = 5;
        g.setColor(new Color(0, 0, 0, 180)); g.fillRoundRect(mx - 4, my - 4, GameMap.W * s + 8, GameMap.H * s + 8, 10, 10);
        for (int r = 0; r < GameMap.H; r++)
            for (int c = 0; c < GameMap.W; c++) {
                int v = m.t[r][c];
                g.setColor(v == 0 ? adj(m.f1, -50) : v == 1 ? m.wall.darker() : v == 3 ? m.wall : new Color(30, 80, 40));
                g.fillRect(mx + c * s, my + r * s, s, s);
            }
        g.setColor(m.allDone() ? new Color(90, 200, 255) : Color.GRAY);
        g.fillOval(mx + GameMap.PORTAL_X * s - 1, my + GameMap.PORTAL_Y * s - 1, 8, 8);
        for (Npc n : m.npcs) { g.setColor(n.done ? Color.GRAY : new Color(255, 220, 60)); g.fillOval(mx + (int) (n.x() / TS * s) - 2, my + (int) (n.y() / TS * s) - 2, 5, 5); }
        g.setColor(Color.WHITE); g.fillOval(mx + (int) (p.x / TS * s) - 2, my + (int) (p.y / TS * s) - 2, 5, 5);
        g.setColor(new Color(255, 255, 255, 120)); g.drawRect(mx + (int) (camX / TS * s), my + (int) (camY / TS * s), W / TS * s, H / TS * s);
    }

    void bar(Graphics2D g, int x, int y, int v, int max, Color c, String label) {
        g.setColor(new Color(0, 0, 0, 160)); g.fillRoundRect(x, y, 200, 18, 8, 8);
        g.setColor(c); g.fillRoundRect(x + 2, y + 2, (int) (196.0 * Math.min(v, max) / max), 14, 6, 6);
        g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString(label + ": " + v, x + 8, y + 14);
    }

    void drawHud(Graphics2D g) {
        drawMinimap(g);
        bar(g, 14, 14, p.health, 100, new Color(0xD9423E), "Здоровье");
        bar(g, 14, 38, p.knowledge, 100, new Color(0x4A90E2), "Знания");
        bar(g, 14, 62, p.resources, 100, new Color(0xE0B030), "Ресурсы");
        g.setFont(new Font("SansSerif", Font.BOLD, 18)); g.setColor(Color.WHITE);
        g.drawString(maps[era].name, W - 190, 32);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12)); g.setColor(new Color(255, 255, 255, 170));
        g.drawString("WASD - ходьба   E - говорить   J - журнал", W - 280, H - 14);
        if (hintT > 0) {
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            int w = g.getFontMetrics().stringWidth(hint);
            g.setColor(new Color(0, 0, 0, 170)); g.fillRoundRect(W / 2 - w / 2 - 12, 90, w + 24, 30, 12, 12);
            g.setColor(Color.WHITE); g.drawString(hint, W / 2 - w / 2, 110);
        }
    }

    List<String> wrap(String s, FontMetrics fm, int w) {
        List<String> out = new ArrayList<>(); String line = "";
        for (String wd : s.split(" ")) {
            String tr = line.isEmpty() ? wd : line + " " + wd;
            if (fm.stringWidth(tr) > w) { out.add(line); line = wd; } else line = tr;
        }
        out.add(line);
        return out;
    }

    void drawDialog(Graphics2D g) {
        int x = 40, y = H - 250, w = W - 80, h = 230;
        g.setColor(new Color(10, 12, 24, 230)); g.fillRoundRect(x, y, w, h, 20, 20);
        g.setColor(talking.color); g.setStroke(new BasicStroke(3)); g.drawRoundRect(x, y, w, h, 20, 20);
        g.setFont(new Font("SansSerif", Font.BOLD, 18)); g.setColor(talking.color.brighter());
        g.drawString(talking.name, x + 20, y + 30);
        g.setFont(new Font("SansSerif", Font.PLAIN, 16)); g.setColor(Color.WHITE);
        int ly = y + 58;
        for (String l : wrap(Story.EV[ev].text(), g.getFontMetrics(), w - 40)) { g.drawString(l, x + 20, ly); ly += 22; }
        ly += 10; g.setColor(new Color(0xFFD866));
        Story.Choice[] ch = Story.EV[ev].choices();
        for (int i = 0; i < ch.length; i++) { g.drawString((i + 1) + ".  " + ch[i].text(), x + 30, ly); ly += 26; }
    }

    void drawJournal(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 220)); g.fillRoundRect(120, 80, W - 240, H - 160, 20, 20);
        g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 22)); g.drawString("Журнал решений", 150, 125);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        int start = Math.max(0, p.log.size() - 13), y = 160;
        if (p.log.isEmpty()) g.drawString("Пока пусто. Поговори с жителями эпохи.", 150, y);
        for (int i = start; i < p.log.size(); i++, y += 28) g.drawString((i + 1) + ". " + p.log.get(i), 150, y);
        g.setColor(new Color(255, 255, 255, 140)); g.drawString("Нажми любую клавишу", 150, H - 105);
    }

    void drawBackdrop(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(0x0B1030), 0, H, new Color(0x3A1C5E)));
        g.fillRect(0, 0, W, H);
        Random r = new Random(5);
        for (int i = 0; i < 90; i++) {
            double tw = 0.5 + 0.5 * Math.sin(t * 2 + i);
            g.setColor(new Color(255, 255, 255, (int) (60 + 150 * tw)));
            g.fillOval(r.nextInt(W), r.nextInt(H), 2 + (i % 3), 2 + (i % 3));
        }
        double cx = W / 2.0, cy = 330;
        g.setColor(new Color(120, 190, 255, 90)); g.setStroke(new BasicStroke(3));
        g.draw(new Ellipse2D.Double(cx - 150, cy - 150, 300, 300));
        g.setStroke(new BasicStroke(2));
        g.drawLine((int) cx, (int) cy, (int) (cx + Math.cos(t) * 130), (int) (cy + Math.sin(t) * 130));
        g.drawLine((int) cx, (int) cy, (int) (cx + Math.cos(t / 12) * 80), (int) (cy + Math.sin(t / 12) * 80));
    }

    void center(Graphics2D g, String s, int y) { g.drawString(s, W / 2 - g.getFontMetrics().stringWidth(s) / 2, y); }

    void drawMenu(Graphics2D g) {
        drawBackdrop(g);
        g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 54)); center(g, "ПУТЕШЕСТВЕННИК ВО ВРЕМЕНИ", 110);
        g.setFont(new Font("SansSerif", Font.PLAIN, 18)); g.setColor(new Color(0xBFD7FF));
        center(g, "Египет  >  Средневековье  >  Будущее. Каждое решение меняет историю.", 150);
        g.setFont(new Font("SansSerif", Font.PLAIN, 16)); g.setColor(Color.WHITE);
        center(g, "WASD - ходьба   E - говорить с персонажем   1-3 - выбор ответа   J - журнал", 540);
        g.setFont(new Font("SansSerif", Font.BOLD, 24)); g.setColor(new Color(0xFFD866, false));
        if ((int) (t * 2) % 2 == 0) center(g, "Нажми ENTER, чтобы начать", 590);
    }

    void drawEnd(Graphics2D g) {
        drawBackdrop(g);
        g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 42)); center(g, "Финал: " + endTitle, 190);
        g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        int y = 250;
        for (String l : wrap(endText, g.getFontMetrics(), 640)) { center(g, l, y); y += 26; }
        g.setColor(new Color(0xFFD866));
        center(g, "Здоровье " + p.health + "   Знания " + p.knowledge + "   Ресурсы " + p.resources, y + 30);
        center(g, "Счёт: " + (p.knowledge * 2 + p.resources + p.health), y + 62);
        g.setColor(new Color(255, 255, 255, 170)); g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        center(g, "Результат записан в results.txt.  ENTER - играть снова", 580);
    }
}
