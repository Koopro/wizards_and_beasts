package at.koopro.wizardsandbeasts.heritage.werewolf;

import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * The lycanthropy layer's persistent state, as typed accessors over the custom flags on
 * {@link PlayerHeritageData}.
 *
 * <p>No new attachment, and that is the point. {@code PlayerHeritageData} is already registered
 * {@code copyOnDeath}, already written to disk by {@code ModAttachments.registerData}, and its flag map
 * already rides {@code HeritageDataSyncS2CPayload} to the owning client — so a flag stored here is
 * server-authoritative, survives a relog and a death, and reaches the client for free. A dedicated
 * {@code WerewolfData} attachment would have had to re-earn all three, and the Obscurial layer had
 * already proved the flag map carries this kind of state well ({@code ObscurialHeritageHandler}).
 *
 * <p>Values are strings because the flag map is; every read is total, falling back rather than
 * throwing, so a hand-edited save cannot stop a player loading.
 */
@NullMarked
public final class WerewolfState {

    /** True while the mod, not the player, is driving the body. The loss-of-control contract's switch. */
    public static final String FLAG_LOSS_OF_CONTROL = "werewolf_loss_of_control";
    /** Accumulated moonlight, in exposure points. See {@link WerewolfConfig#exposureThreshold}. */
    public static final String FLAG_EXPOSURE = "werewolf_moon_exposure";
    /** Absolute game tick at which a pending change completes; absent when nothing is pending. */
    public static final String FLAG_TRANSFORM_AT_TICK = "werewolf_transform_at_tick";
    /** True when a medicated werewolf has chosen to stay in wolf form past the moon's claim on them. */
    public static final String FLAG_VOLUNTARY = "werewolf_voluntary";
    /** True while gear was unequipped by the change, so the revert knows a strip happened. */
    public static final String FLAG_STRIPPED = "werewolf_equipment_stripped";

    private WerewolfState() {}

    public static PlayerHeritageData data(ServerPlayer player) {
        return player.getData(ModAttachments.HERITAGE_DATA.get());
    }

    // ── loss of control ────────────────────────────────────────────────

    public static boolean isLossOfControl(PlayerHeritageData data) {
        return "true".equals(data.getFlag(FLAG_LOSS_OF_CONTROL));
    }

    public static void setLossOfControl(PlayerHeritageData data, boolean value) {
        if (value) {
            data.setFlag(FLAG_LOSS_OF_CONTROL, "true");
        } else {
            data.removeFlag(FLAG_LOSS_OF_CONTROL);
        }
    }

    // ── moonlight exposure ─────────────────────────────────────────────

    public static int getExposure(PlayerHeritageData data) {
        return parseInt(data.getFlag(FLAG_EXPOSURE));
    }

    /** Adds {@code delta}, clamping to {@code [0, threshold]} so the counter cannot bank a head start. */
    public static int addExposure(PlayerHeritageData data, int delta, int max) {
        int next = Math.max(0, Math.min(max, getExposure(data) + delta));
        if (next == 0) {
            data.removeFlag(FLAG_EXPOSURE);
        } else {
            data.setFlag(FLAG_EXPOSURE, Integer.toString(next));
        }
        return next;
    }

    public static void clearExposure(PlayerHeritageData data) {
        data.removeFlag(FLAG_EXPOSURE);
    }

    // ── the pending change ─────────────────────────────────────────────

    public static boolean hasPendingTransform(PlayerHeritageData data) {
        return data.getFlag(FLAG_TRANSFORM_AT_TICK) != null;
    }

    public static long getTransformAtTick(PlayerHeritageData data) {
        return parseLong(data.getFlag(FLAG_TRANSFORM_AT_TICK));
    }

    public static void setTransformAtTick(PlayerHeritageData data, long tick) {
        data.setFlag(FLAG_TRANSFORM_AT_TICK, Long.toString(tick));
    }

    public static void clearPendingTransform(PlayerHeritageData data) {
        data.removeFlag(FLAG_TRANSFORM_AT_TICK);
    }

    // ── medicated choice ───────────────────────────────────────────────

    public static boolean isVoluntary(PlayerHeritageData data) {
        return "true".equals(data.getFlag(FLAG_VOLUNTARY));
    }

    public static void setVoluntary(PlayerHeritageData data, boolean value) {
        if (value) {
            data.setFlag(FLAG_VOLUNTARY, "true");
        } else {
            data.removeFlag(FLAG_VOLUNTARY);
        }
    }

    // ── equipment ──────────────────────────────────────────────────────

    public static boolean wasStripped(PlayerHeritageData data) {
        return "true".equals(data.getFlag(FLAG_STRIPPED));
    }

    public static void setStripped(PlayerHeritageData data, boolean value) {
        if (value) {
            data.setFlag(FLAG_STRIPPED, "true");
        } else {
            data.removeFlag(FLAG_STRIPPED);
        }
    }

    /** Wipes every werewolf flag. Used by the revert and by an admin reset. */
    public static void clearAll(PlayerHeritageData data) {
        data.removeFlag(FLAG_LOSS_OF_CONTROL);
        data.removeFlag(FLAG_EXPOSURE);
        data.removeFlag(FLAG_TRANSFORM_AT_TICK);
        data.removeFlag(FLAG_VOLUNTARY);
        data.removeFlag(FLAG_STRIPPED);
    }

    private static int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
