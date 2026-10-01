package at.koopro.wizardsandbeasts.spell.tuning;

import at.koopro.wizardsandbeasts.spell.core.SpellRequirement;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The live spell administration state every {@code Spell} accessor consults. Pure — no {@code Config}, no
 * server — so the cast formulas that read it stay unit-testable.
 *
 * <p><b>Two layers.</b> The <em>local</em> layer is written by the side that owns the state: the server
 * (overrides from world data, globals from {@code Config}). The <em>remote</em> layer is written on a client
 * that is connected to someone else's server, from {@code SpellTuningSyncS2CPayload}, and wins while it is
 * set, so the HUD and menus show the server's numbers rather than the client's own config file. A client
 * playing on its own integrated server never sets the remote layer: the local layer already is the server's,
 * and reading a copy of it that lags one packet behind would let the server thread act on stale values.
 *
 * <p>Snapshots are immutable and published through volatile fields, so a reader on any thread sees one
 * whole state, never half of an administrator's change.
 */
@NullMarked
public final class SpellTuning {

    private static volatile SpellTuningSnapshot local = SpellTuningSnapshot.EMPTY;
    private static volatile @Nullable SpellTuningSnapshot remote;
    /** Bumped on every publish; lets {@code Spell} cache the range-adjusted properties it derives. */
    private static volatile long version;

    private SpellTuning() {}

    public static SpellTuningSnapshot current() {
        SpellTuningSnapshot synced = remote;
        return synced != null ? synced : local;
    }

    public static SpellTuningSnapshot local() {
        return local;
    }

    public static long version() {
        return version;
    }

    // ── writers ──

    /** The owning side replaces its state. Server thread (or the config-load thread for globals). */
    public static synchronized void publishLocal(SpellTuningSnapshot snapshot) {
        local = snapshot;
        version++;
    }

    public static synchronized void publishLocalGlobals(SpellTuningSnapshot.Globals globals) {
        publishLocal(local.withGlobals(globals));
    }

    /** Drops the server-owned overrides (a world closing), keeping the config-owned globals. */
    public static synchronized void clearLocalOverrides() {
        publishLocal(new SpellTuningSnapshot(java.util.Map.of(), local.globals()));
    }

    /** A remote server's state arrived. */
    public static synchronized void acceptRemote(SpellTuningSnapshot snapshot) {
        remote = snapshot;
        version++;
    }

    /** Left the remote server. */
    public static synchronized void clearRemote() {
        remote = null;
        version++;
    }

    // ── readers used by Spell ──

    public static boolean enabled(String spellId) {
        return current().override(spellId).enabled().orElse(Boolean.TRUE);
    }

    /** The authored cooldown unless overridden, then the global multiplier; never below one tick. */
    public static int cooldownTicks(String spellId, int authored) {
        SpellTuningSnapshot state = current();
        int base = state.override(spellId).cooldownTicks().orElse(authored);
        float multiplier = state.globals().cooldownMultiplier();
        if (multiplier == 1.0f) {
            return base;
        }
        return Math.max(1, Math.round(base * multiplier));
    }

    /** The authored damage unless overridden, then the global multiplier. */
    public static float damage(String spellId, float authored) {
        SpellTuningSnapshot state = current();
        return state.override(spellId).damage().orElse(authored) * state.globals().damageMultiplier();
    }

    /** The authored range unless overridden, then the global multiplier. A range of 0 means "unused" and stays 0. */
    public static float range(String spellId, float authored) {
        if (authored <= 0.0f) {
            return authored;
        }
        SpellTuningSnapshot state = current();
        return state.override(spellId).range().orElse(authored) * state.globals().rangeMultiplier();
    }

    /** Whether any range adjustment is in force for this spell (so the caller can skip the copy). */
    public static boolean rangeAdjusted(String spellId) {
        SpellTuningSnapshot state = current();
        return state.globals().rangeMultiplier() != 1.0f || state.override(spellId).range().isPresent();
    }

    public static SpellRequirement requirement(String spellId, SpellRequirement authored) {
        SpellRequirement override = current().requirementOverride(spellId);
        return override != null ? override : authored;
    }

    /** The authored learning skill unless overridden; an override of {@code ""} removes the requirement. */
    public static @Nullable String requiredSkill(String spellId, @Nullable String authored) {
        java.util.Optional<String> override = current().override(spellId).requiredSkill();
        if (override.isEmpty()) {
            return authored;
        }
        return override.get().isBlank() ? null : override.get();
    }

    public static SpellTuningSnapshot.Globals globals() {
        return current().globals();
    }
}
