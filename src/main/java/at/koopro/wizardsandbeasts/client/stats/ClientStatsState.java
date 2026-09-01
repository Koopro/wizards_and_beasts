package at.koopro.wizardsandbeasts.client.stats;

import at.koopro.wizardsandbeasts.stats.PlayerStat;
import at.koopro.wizardsandbeasts.stats.PlayerStatsData;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public final class ClientStatsState {

    /** How long the training bar takes to slide from its old value to the newly synced one. */
    private static final long EASE_MS = 260L;

    @Nullable
    private static PlayerStatsData current = null;

    /**
     * Where each training bar was drawn when the last packet landed, and when that was.
     *
     * <p>A point of PRECISION is 307 spell hits, so a hairline that snaps between two nearly
     * identical widths is indistinguishable from one that never moves — which is what made training
     * read as broken in the first place. Easing from the previous width gives the bar a visible
     * quarter-second of travel per event without anything extra crossing the wire; the destination
     * is still whatever the server said.
     */
    private static final Map<PlayerStat, Float> easeFrom = new EnumMap<>(PlayerStat.class);
    private static long easeStartedAt;

    private ClientStatsState() {}

    public static void applySync(PlayerStatsData data) {
        PlayerStatsData previous = current;
        // Snapshot where the bars are *right now*, not where the last packet put them: two packets
        // inside the easing window would otherwise restart the slide from a value never drawn.
        for (PlayerStat stat : PlayerStat.values()) {
            if (!stat.isTrainable()) continue;
            // A point just landed, so the accumulator wrapped from nearly full back to nearly empty.
            // Easing that would run the bar backwards across its whole width, which reads as losing
            // the progress the player just earned. Snap instead and let the row flash carry the news.
            if (previous != null && previous.get(stat) != data.get(stat)) {
                easeFrom.remove(stat);
            } else {
                easeFrom.put(stat, trainingProgress(stat));
            }
        }
        easeStartedAt = System.currentTimeMillis();
        current = data;
    }

    /** Returns the last synced stats, or null if no sync has been received yet. */
    @Nullable
    public static PlayerStatsData get() {
        return current;
    }

    /** True if at least one sync packet has been received this session. */
    public static boolean hasData() {
        return current != null;
    }

    /**
     * Training progress for display: the synced value, eased from where the bar was drawn before the
     * packet arrived. Purely cosmetic — {@code get().trainingProgress()} remains the authority.
     */
    public static float trainingProgress(PlayerStat stat) {
        PlayerStatsData data = current;
        if (data == null || !stat.isTrainable()) return 0f;
        float target = Mth.clamp(data.trainingProgress().getOrDefault(stat, 0f), 0f, 1f);

        Float from = easeFrom.get(stat);
        if (from == null) return target;
        long elapsed = System.currentTimeMillis() - easeStartedAt;
        if (elapsed < 0 || elapsed >= EASE_MS) return target;
        return Mth.lerp((float) elapsed / EASE_MS, from, target);
    }

    public static void clear() {
        current = null;
        easeFrom.clear();
    }
}
