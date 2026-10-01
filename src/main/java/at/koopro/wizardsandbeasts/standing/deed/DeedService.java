package at.koopro.wizardsandbeasts.standing.deed;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.standing.StandingAxis;
import at.koopro.wizardsandbeasts.standing.StandingService;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
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
 * <p>Cooldowns are per player and per deed, saved on the {@code DEED_COOLDOWNS} attachment against absolute game
 * time. They used to be held in memory only and cleared at logout, which meant a relog reset every one of them: a
 * player could cast the Patronus, log out, log in and cast it again for another three points of standing
 * (2026-09-29, documentation/MULTIPLAYER_AUDIT.md). Game time is monotonic across restarts, so a saved cooldown
 * never owes anyone standing — it only stops the same deed scoring twice inside its window.
 */
@NullMarked
public final class DeedService {

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
        Long readyAt = player.getData(ModAttachments.DEED_COOLDOWNS.get()).get(deedId);
        return readyAt != null && now < readyAt;
    }

    private static void markFired(ServerPlayer player, Identifier deedId, Deed deed, long now) {
        if (deed.cooldownSeconds() <= 0) {
            return;
        }
        Map<Identifier, Long> next = new HashMap<>();
        // Expired entries are dropped as the map is rewritten, so it never grows past the deeds still cooling.
        player.getData(ModAttachments.DEED_COOLDOWNS.get()).forEach((id, readyAt) -> {
            if (readyAt > now) {
                next.put(id, readyAt);
            }
        });
        next.put(deedId, now + (long) deed.cooldownSeconds() * 20L);
        player.setData(ModAttachments.DEED_COOLDOWNS.get(), Map.copyOf(next));
    }

    /** Visible for tests and for {@code /reload}: a reload can retire a deed id, so stale keys go. */
    public static void clearCooldowns(ServerPlayer player) {
        player.setData(ModAttachments.DEED_COOLDOWNS.get(), Map.of());
    }
}
