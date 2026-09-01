package at.koopro.wizardsandbeasts.standing;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.corruption.DarkCorruptionService;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.data.MinistryRank;
import at.koopro.wizardsandbeasts.ministry.data.PlayerMinistryRecord;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.standing.StandingSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.Map;

/**
 * Read and move a player's standing. The one seam: every write goes through {@link #adjust}, so the
 * clamp, the band-change notice and the client sync are applied once rather than re-typed at each
 * call site — the mistake {@code DarkCorruptionService} was written to fix for corruption.
 *
 * <p>Reads resolve the derived axes here rather than storing them; see {@link StandingAxis}. That is
 * why {@link #valueOf} takes a {@link Player} and not a {@link MagicalStanding}: two of the three axes
 * are not in the record at all.
 *
 * <p>Server-authoritative throughout. Nothing in this class is safe to call on a client, and nothing
 * on the client can reach it — the client's copy arrives as a payload and is read-only.
 */
@NullMarked
public final class StandingService {

    private StandingService() {}

    // ── configuration, resolved once per call ──

    public static float bound() {
        return Config.standingAxisBound;
    }

    private static int leanPercent() {
        return Config.standingLeanThresholdPercent;
    }

    private static int strongPercent() {
        return Config.standingStrongThresholdPercent;
    }

    // ── reads ──

    public static MagicalStanding get(Player player) {
        return player.getData(ModAttachments.MAGICAL_STANDING.get());
    }

    /**
     * The player's value on {@code axis}, composing the derived axes from their real sources.
     *
     * <p>{@link StandingAxis#MINISTRY} returns 0 when {@code Module.MINISTRY} is off. Not because the
     * record is unreadable — it is still there — but because with the Trace disabled nothing can move
     * it in either direction, and an axis frozen at whatever it held when the module was switched off
     * would gate content on a number the player can no longer change.
     */
    public static float valueOf(Player player, StandingAxis axis) {
        float bound = bound();
        return switch (axis) {
            case TRADITION -> StandingBands.clamp(get(player).tradition(), bound);
            case ALIGNMENT -> StandingBands.alignmentOf(
                    get(player).light(), DarkCorruptionService.get(player), bound);
            case MINISTRY -> {
                if (!ModuleManager.isEnabled(Module.MINISTRY)) {
                    yield 0.0f;
                }
                PlayerMinistryRecord record = MinistryRecords.get(player);
                yield StandingBands.ministryOf(
                        rankCredit(record.rank()), record.notoriety(), bound);
            }
        };
    }

    public static StandingBand bandOf(Player player, StandingAxis axis) {
        return StandingBands.bandFor(valueOf(player, axis), bound(), leanPercent(), strongPercent());
    }

    /** Every axis at once, for the sheet and the sync payload. */
    public static Map<StandingAxis, Float> snapshot(Player player) {
        Map<StandingAxis, Float> values = new EnumMap<>(StandingAxis.class);
        for (StandingAxis axis : StandingAxis.values()) {
            values.put(axis, valueOf(player, axis));
        }
        return values;
    }

    /**
     * Standing conferred by holding Ministry office. Linear in rank ordinal so the four ranks are
     * evenly spaced and a server can flatten the whole thing to zero with one config key.
     */
    public static float rankCredit(MinistryRank rank) {
        return (float) rank.ordinal() * Config.standingMinistryRankCredit;
    }

    // ── writes ──

    /**
     * Moves one axis by {@code delta} and reports what it did.
     *
     * <p>A derived axis is not writable here and returns a refusal rather than throwing: the deltas
     * come from datapack JSON, and a typo in someone's data file must not take a cast down with it.
     * The two derived axes have their own front doors — {@code DarkCorruptionService} for the dark
     * pole and the Ministry's own {@code TraceService} for notoriety — and routing a write to the
     * wrong one silently would be worse than saying no.
     *
     * @return the outcome, including the band before and after so callers can notice a crossing
     */
    public static Adjustment adjust(ServerPlayer player, StandingAxis axis, float delta) {
        if (Float.isNaN(delta) || delta == 0.0f) {
            return Adjustment.unchanged(bandOf(player, axis), valueOf(player, axis));
        }
        float bound = bound();
        MagicalStanding before = get(player);
        StandingBand bandBefore = bandOf(player, axis);

        MagicalStanding after = switch (axis) {
            case TRADITION -> before.withTradition(StandingBands.clamp(before.tradition() + delta, bound));
            case ALIGNMENT -> {
                // Only the light pole is ours. Darkening is corruption, and corruption has an owner:
                // routing it here would skip the vocation scaling that owner applies.
                if (delta < 0.0f) {
                    yield before;
                }
                yield before.withLight(StandingBands.clampUnipolar(before.light() + delta, bound));
            }
            case MINISTRY -> before;
        };

        if (axis == StandingAxis.MINISTRY || (axis == StandingAxis.ALIGNMENT && delta < 0.0f)) {
            return Adjustment.refused(bandBefore, valueOf(player, axis));
        }
        if (after.equals(before)) {
            return Adjustment.unchanged(bandBefore, valueOf(player, axis));
        }

        player.setData(ModAttachments.MAGICAL_STANDING.get(), after);
        StandingBand bandAfter = bandOf(player, axis);
        sync(player);
        return new Adjustment(true, bandBefore != bandAfter, bandBefore, bandAfter, valueOf(player, axis));
    }

    /** Sets a stored axis outright. Admin path only — {@link #adjust} is what gameplay uses. */
    public static boolean set(ServerPlayer player, StandingAxis axis, float value) {
        if (!axis.isStored()) {
            return false;
        }
        float bound = bound();
        MagicalStanding next = get(player).withTradition(StandingBands.clamp(value, bound));
        player.setData(ModAttachments.MAGICAL_STANDING.get(), next);
        sync(player);
        return true;
    }

    /** Wipes the stored half back to neutral. The derived axes are untouched — they are not ours to clear. */
    public static void reset(ServerPlayer player) {
        player.setData(ModAttachments.MAGICAL_STANDING.get(), MagicalStanding.DEFAULT);
        sync(player);
    }

    /**
     * Pushes the player's standing to their own client. Called on every write and from the login /
     * respawn / dimension-change path, which all funnel through {@code PlayerStateSyncService}.
     */
    public static void sync(ServerPlayer player) {
        StandingSyncS2CPayload.syncToPlayer(player);
    }

    /**
     * What one {@link #adjust} did.
     *
     * @param applied     false when the axis refused the write or nothing moved
     * @param bandChanged true when the move crossed a band boundary — the only moment worth telling
     *                    the player about, since a fractional drift is not news
     */
    public record Adjustment(boolean applied, boolean bandChanged,
                             StandingBand before, StandingBand after, float value) {

        static Adjustment unchanged(StandingBand band, float value) {
            return new Adjustment(false, false, band, band, value);
        }

        static Adjustment refused(StandingBand band, float value) {
            return new Adjustment(false, false, band, band, value);
        }
    }
}
