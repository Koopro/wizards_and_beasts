package at.koopro.wizardsandbeasts.standing.deed;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.standing.StandingAxis;
import at.koopro.wizardsandbeasts.standing.StandingService;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Fires deeds. The only thing that connects a gameplay event to a change in standing.
 *
 * <p>Hooks call {@link #fire} from seams that already existed; this class decides which authored rules
 * apply and hands the deltas to {@link StandingService}, which owns the clamping and the sync. So the
 * chain is: one existing seam → one dispatch → one mutation seam, with no system learning about any
 * other.
 *
 * <p>Cooldowns are per player and per deed, held in {@link PlayerScopedState} so they cannot outlive a
 * logout. They are deliberately <b>not</b> persisted: a cooldown exists to stop a spell being spammed
 * inside one session, and carrying it across a restart would mean a server reboot could silently owe a
 * player standing they had already earned.
 */
@NullMarked
public final class DeedService {

    /** Per-player map of deed id → game time at which it may next score. */
    private static final PlayerScopedState<Map<Identifier, Long>> COOLDOWNS =
            PlayerScopedState.create("standing_deed_cooldowns");

    private DeedService() {}

    /** A spell resolved successfully. */
    public static void onSpellCast(ServerPlayer player, String spellId) {
        fire(player, DeedTrigger.SPELL_CAST, spellId, null);
    }

    /** The Ministry filed an offence. */
    public static void onOffence(ServerPlayer player, String offenceName) {
        fire(player, DeedTrigger.OFFENCE, offenceName, null);
    }

    /** A Bestiary entry advanced. */
    public static void onBestiaryTier(ServerPlayer player, Identifier entryId, DiscoveryTier reached) {
        fire(player, DeedTrigger.BESTIARY_TIER, entryId.toString(), reached);
    }

    /**
     * Evaluates every deed listening for {@code trigger} against {@code key}.
     *
     * <p>Returns immediately when nothing is authored for the trigger, which is the common case on a
     * default install and matters because two of the three call sites are on the spell cast path.
     *
     * @return how many deeds actually scored
     */
    public static int fire(ServerPlayer player, DeedTrigger trigger,
                           @Nullable String key, @Nullable DiscoveryTier tier) {
        if (DeedRegistry.isEmpty(trigger)) {
            return 0;
        }
        long now = player.level().getGameTime();
        int scored = 0;

        for (Map.Entry<Identifier, Deed> entry : DeedRegistry.forTrigger(trigger)) {
            Deed deed = entry.getValue();
            if (!deed.matches(key) || !deed.meetsTier(tier)) {
                continue;
            }
            if (isOnCooldown(player, entry.getKey(), now)) {
                continue;
            }
            if (apply(player, deed)) {
                markFired(player, entry.getKey(), deed, now);
                scored++;
            }
        }
        return scored;
    }

    /** Applies one deed's deltas. True if any axis actually moved. */
    private static boolean apply(ServerPlayer player, Deed deed) {
        boolean moved = false;
        for (Map.Entry<StandingAxis, Float> effect : deed.effects().entrySet()) {
            StandingService.Adjustment result =
                    StandingService.adjust(player, effect.getKey(), effect.getValue());
            if (result.applied()) {
                moved = true;
                if (result.bandChanged()) {
                    announce(player, effect.getKey(), result);
                }
            }
        }
        return moved;
    }

    /**
     * Tells the player when they cross a band, and only then. A deed moving an axis by two points is
     * not news; becoming someone the Ministry trusts, or stepping past the point where your conduct
     * reads as traditionalist, is. Sent as a toast because chat and the action bar both draw
     * underneath an open screen, and the Bestiary trigger fires with one open.
     */
    private static void announce(ServerPlayer player, StandingAxis axis,
                                 StandingService.Adjustment result) {
        // WARN is the mod's category for "state you should know about but did not ask for", which is
        // what this is in both directions — the enum carries no colour, so a shift toward the light
        // does not get painted as a problem.
        PlayerFeedback.toast(player, NoticeKind.WARN,
                axis.displayName(),
                Component.translatable("standing.wizards_and_beasts.shift",
                        axis.bandName(result.before()), axis.bandName(result.after())));
    }

    // ── cooldowns ──

    private static boolean isOnCooldown(ServerPlayer player, Identifier deedId, long now) {
        Map<Identifier, Long> map = COOLDOWNS.get(player);
        if (map == null) {
            return false;
        }
        Long readyAt = map.get(deedId);
        return readyAt != null && now < readyAt;
    }

    private static void markFired(ServerPlayer player, Identifier deedId, Deed deed, long now) {
        if (deed.cooldownSeconds() <= 0) {
            return;
        }
        COOLDOWNS.computeIfAbsent(player.getUUID(), id -> new HashMap<>())
                .put(deedId, now + (long) deed.cooldownSeconds() * 20L);
    }

    /** Visible for tests and for {@code /reload}: a reload can retire a deed id, so stale keys go. */
    public static void clearCooldowns(ServerPlayer player) {
        COOLDOWNS.remove(player);
    }
}
