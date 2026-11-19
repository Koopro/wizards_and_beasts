package at.koopro.wizardsandbeasts.client.vfx;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Effects that last: smoke rising off a scorch, a healing spiral climbing a body, dark smoke curling off a wand
 * while a curse is held. Each is a small callback run on client ticks for a set time, so a spell's aftermath is
 * spawned here by the client rather than streamed from the server tick by tick.
 *
 * <p>Live emitters are capped by {@code vfxQuality} (8 / 24 / 48); over the cap the oldest stops. Every particle an
 * emitter spawns should go through {@link VfxBudget#count} so the per-tick ceiling holds.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class VfxEmitters {

    /** One tick of an emitter: {@code age} counts from 0. */
    @FunctionalInterface
    public interface Tick {
        void run(ClientLevel level, RandomSource random, int age);
    }

    private static final class Emitter {
        final Tick tick;
        final int life;
        final int every;
        int age;
        int delay;

        Emitter(Tick tick, int life, int every, int delay) {
            this.tick = tick;
            this.life = life;
            this.every = Math.max(1, every);
            this.delay = delay;
        }
    }

    private static final List<Emitter> LIVE = new ArrayList<>();

    private VfxEmitters() {}

    static int cap(Config.VfxQuality q) {
        return switch (q) {
            case LOW -> 8;
            case MEDIUM -> 24;
            case HIGH -> 48;
        };
    }

    /** Runs {@code tick} every {@code every} ticks for {@code life} ticks, starting after {@code delay}. */
    public static void start(Tick tick, int life, int every, int delay) {
        int cap = cap(VfxBudget.quality());
        while (LIVE.size() >= cap) {
            LIVE.remove(0);
        }
        LIVE.add(new Emitter(tick, life, every, delay));
    }

    public static void start(Tick tick, int life, int every) {
        start(tick, life, every, 0);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (LIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) {
            return;
        }
        // Copy: an emitter's tick may start another emitter.
        for (Emitter e : List.copyOf(LIVE)) {
            if (e.delay > 0) {
                e.delay--;
                continue;
            }
            if (e.age % e.every == 0) {
                e.tick.run(level, level.random, e.age);
            }
            e.age++;
        }
        LIVE.removeIf(e -> e.delay <= 0 && e.age >= e.life);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        LIVE.clear();
    }

    /** Stops every emitter (the spell showcase between spells). */
    public static void clear() {
        LIVE.clear();
    }

    public static int live() {
        return LIVE.size();
    }
}
