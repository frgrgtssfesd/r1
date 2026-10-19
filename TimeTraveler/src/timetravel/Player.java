package timetravel;

import java.util.ArrayList;
import java.util.List;

/** Игрок: позиция и показатели здоровья, знаний и ресурсов. */
public class Player {
    public double x, y;
    public int health = 100, knowledge = 0, resources = 20;
    public int facing = 1;
    public double anim;
    public boolean moving;
    public final List<String> log = new ArrayList<>();

    public void apply(int h, int k, int r) {
        health = Math.max(0, Math.min(100, health + h));
        knowledge = Math.max(0, knowledge + k);
        resources = Math.max(0, resources + r);
    }

    public boolean dead() { return health <= 0; }
}
