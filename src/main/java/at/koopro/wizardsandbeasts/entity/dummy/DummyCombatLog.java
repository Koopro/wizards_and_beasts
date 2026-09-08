package at.koopro.wizardsandbeasts.entity.dummy;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a duelling dummy remembers about who is hitting it.
 *
 * <p>One {@link Bout} per attacker rather than one shared figure for the whole dummy. Two wizards
 * practising on the same target is the normal case in a duelling club, and a shared total would
 * report each of them the other's damage — the one number a training aid must never get wrong.
 *
 * <p>A bout ends after {@code timeoutTicks} without a hit. That gap is what separates "still
 * casting" from "walked away", and it is the only thing that closes a window: nothing here is
 * driven by the dummy's health, so a reset in the middle of a rotation does not restart the count.
 */
@NullMarked
public final class DummyCombatLog {

    /** One attacker's running window. Ticks are level game time. */
    public static final class Bout {
        private long startTick;
        private long lastTick;
        private float damage;
        private float healing;
        private int hits;
        private float biggestHit;

        private Bout(long tick) {
            this.startTick = tick;
            this.lastTick = tick;
        }

        public float damage() {
            return damage;
        }

        public float healing() {
            return healing;
        }

        public int hits() {
            return hits;
        }

        public float biggestHit() {
            return biggestHit;
        }

        /** Seconds the window covers, floored at one so a single hit reads as "this much per second". */
        public float seconds() {
            return Math.max(1.0f, (lastTick - startTick) / 20.0f);
        }

        public float dps() {
            return damage / seconds();
        }

        public float hps() {
            return healing / seconds();
        }
    }

    private final Map<UUID, Bout> bouts = new HashMap<>();

    /**
     * Records one hit and returns the attacker's window.
     *
     * @param amount damage actually applied, after armour and absorption — the figure a player
     *               wants, not the one the spell nominally deals
     */
    public Bout hit(UUID attacker, long tick, float amount) {
        Bout bout = bouts.computeIfAbsent(attacker, id -> new Bout(tick));
        bout.lastTick = tick;
        bout.damage += amount;
        bout.hits++;
        bout.biggestHit = Math.max(bout.biggestHit, amount);
        return bout;
    }

    /** Records healing the dummy received while a bout was open, so a heal test reads as HPS. */
    public Bout heal(UUID healer, long tick, float amount) {
        Bout bout = bouts.computeIfAbsent(healer, id -> new Bout(tick));
        bout.lastTick = tick;
        bout.healing += amount;
        return bout;
    }

    public boolean isEmpty() {
        return bouts.isEmpty();
    }

    /** Attackers whose window has gone quiet for longer than {@code timeoutTicks}. */
    public List<UUID> expired(long tick, int timeoutTicks) {
        List<UUID> done = new ArrayList<>();
        for (var entry : bouts.entrySet()) {
            if (tick - entry.getValue().lastTick >= timeoutTicks) {
                done.add(entry.getKey());
            }
        }
        return done;
    }

    public Bout end(UUID attacker) {
        Bout bout = bouts.remove(attacker);
        return bout == null ? new Bout(0L) : bout;
    }

    public void clear() {
        bouts.clear();
    }
}
