package at.koopro.wizardsandbeasts.client.apparition.state;

import at.koopro.wizardsandbeasts.apparition.ApparitionCrackVariant;
import at.koopro.wizardsandbeasts.apparition.ApparitionPhase;
import at.koopro.wizardsandbeasts.apparition.ApparitionTier;
import at.koopro.wizardsandbeasts.apparition.splinch.SplinchTier;
import at.koopro.wizardsandbeasts.network.apparition.ApparitionPresentationS2CPayload;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the client currently knows about Apparitions in progress and Apparitions that just finished.
 *
 * <p>Class-init-safe: only common-typed static state, no Minecraft client types held anywhere, so the payload
 * class can reference it without dragging client classes onto a dedicated server.
 *
 * <p>The charge map is the server's clock as last reported. Nothing here counts ticks on its own — that local
 * estimate is exactly what this replaces — so a renderer reading {@link #charge} is reading the server's
 * numbers or nothing at all.
 */
@NullMarked
public final class ClientApparitionPresentationState {

    /** Ticks a resolution event stays drainable before it is dropped, so a paused client cannot hoard them. */
    private static final int EVENT_LIFETIME_TICKS = 40;
    /** Hard ceiling on queued resolutions, so a hostile or broken server cannot grow this without bound. */
    private static final int MAX_EVENTS = 64;

    /** One in-flight attempt as the server last described it. */
    public record Charge(
            ApparitionTier tier,
            ApparitionPhase phase,
            int elapsed,
            int windowOpen,
            int windowClose,
            Vec3 origin) {

        /** Charge progress in {@code [0, 1]} — how full the Determination bar is. */
        public float determinationProgress() {
            if (windowOpen <= 0) {
                return 1.0f;
            }
            return Math.max(0.0f, Math.min(1.0f, elapsed / (float) windowOpen));
        }

        public boolean isWindowOpen() {
            return elapsed >= windowOpen && elapsed <= windowClose;
        }

        public boolean isWindowClosed() {
            return elapsed > windowClose;
        }
    }

    /** One finished attempt, waiting to be turned into particles and sound. */
    public record Resolution(
            int casterId,
            ApparitionTier tier,
            SplinchTier splinchTier,
            Vec3 origin,
            @Nullable Vec3 destination,
            int radius,
            ApparitionCrackVariant crackVariant,
            int receivedAtTick) {

        public boolean arrived() {
            return destination != null;
        }
    }

    private static final Map<Integer, Charge> CHARGES = new LinkedHashMap<>();
    private static final List<Resolution> RESOLUTIONS = new ArrayList<>();

    private static int clientTick;

    private ClientApparitionPresentationState() {}

    public static void accept(ApparitionPresentationS2CPayload payload) {
        if (payload.phase() == ApparitionPhase.RESOLVING) {
            CHARGES.remove(payload.casterId());
            if (RESOLUTIONS.size() < MAX_EVENTS) {
                RESOLUTIONS.add(new Resolution(
                        payload.casterId(),
                        payload.tier(),
                        payload.splinchTier() == null ? SplinchTier.CLEAN : payload.splinchTier(),
                        payload.origin(),
                        payload.destination(),
                        payload.radius(),
                        payload.crackVariant(),
                        clientTick));
            }
            return;
        }
        if (payload.phase() == ApparitionPhase.IDLE) {
            CHARGES.remove(payload.casterId());
            return;
        }
        CHARGES.put(payload.casterId(), new Charge(
                payload.tier(), payload.phase(), payload.elapsed(),
                payload.windowOpen(), payload.windowClose(), payload.origin()));
    }

    /** The attempt {@code casterId} is currently making, or {@code null}. */
    public static @Nullable Charge charge(int casterId) {
        return CHARGES.get(casterId);
    }

    public static Map<Integer, Charge> charges() {
        return Map.copyOf(CHARGES);
    }

    /** Resolutions received since the last drain. Removes them — each one is rendered once. */
    public static List<Resolution> drainResolutions() {
        if (RESOLUTIONS.isEmpty()) {
            return List.of();
        }
        List<Resolution> drained = List.copyOf(RESOLUTIONS);
        RESOLUTIONS.clear();
        return drained;
    }

    /** Called once per client tick. Ages out anything a renderer never came to collect. */
    public static void tick() {
        clientTick++;
        RESOLUTIONS.removeIf(event -> clientTick - event.receivedAtTick() > EVENT_LIFETIME_TICKS);
    }

    /** Drops everything. Called on disconnect so no state survives into the next server. */
    public static void clear() {
        CHARGES.clear();
        RESOLUTIONS.clear();
        clientTick = 0;
    }
}
