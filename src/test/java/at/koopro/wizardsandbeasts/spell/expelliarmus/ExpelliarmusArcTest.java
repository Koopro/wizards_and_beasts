package at.koopro.wizardsandbeasts.spell.expelliarmus;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** A disarmed wand lobbed to a proficient caster lands at their feet, not past them and not far short. */
class ExpelliarmusArcTest {

    /** Item physics: 0.04 gravity per tick, then 2% drag on every axis. */
    private static Vec3 landing(Vec3 from, Vec3 velocity, double groundY) {
        Vec3 pos = from;
        Vec3 v = velocity;
        for (int tick = 0; tick < 400; tick++) {
            v = v.add(0, -0.04, 0);
            pos = pos.add(v);
            v = v.scale(0.98);
            if (v.y < 0 && pos.y <= groundY) {
                return pos;
            }
        }
        return pos;
    }

    @Test
    void wandLandsNearTheCaster() {
        for (double distance : new double[] {3, 6, 10, 16}) {
            Vec3 from = new Vec3(distance, 1.5, 0);
            Vec3 caster = new Vec3(0, 0, 0);
            Vec3 land = landing(from, ExpelliarmusDisarmHandler.arcTowards(from, caster), 0);
            double miss = Math.hypot(land.x - caster.x, land.z - caster.z);
            assertTrue(miss < Math.max(2.5, distance * 0.3),
                    "from " + distance + " blocks the wand landed " + miss + " from the caster");
            assertTrue(land.x >= -1.0, "from " + distance + " blocks the wand flew past the caster to " + land.x);
        }
    }
}
