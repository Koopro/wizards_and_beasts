package at.koopro.wizardsandbeasts.veritaserum;

import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.polyjuice.PolyjuiceService;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;

/**
 * Veritaserum: three drops, and you cannot hold a false face.
 *
 * <h2>What "truth" is, mechanically</h2>
 * <p>A truth serum in a game with no dialogue system is a design trap. The obvious readings are all
 * bad: policing what a player types is unenforceable and unpleasant, and a chat filter is worse.
 * What this mod <em>does</em> have is an unusually deep stack of ways to be someone else — Polyjuice,
 * the Demiguise cloak, invisibility, Animagus, Metamorphmagus — plus Occlumency, which is literally
 * the skill of keeping your mind shut. So truth here is <b>the inability to conceal your identity</b>,
 * which is a real, checkable, entirely mechanical thing:
 *
 * <ul>
 *   <li>Any Polyjuice disguise ends the moment the dose lands, and another cannot be started while
 *       the compulsion runs — {@link #blocksDisguise}.</li>
 *   <li>Invisibility is stripped and cannot be kept; the tick puts it out again if something
 *       re-applies it.</li>
 *   <li>The drinker is outlined, so the compulsion is visible to the room rather than a private
 *       status the interrogator has to take on trust.</li>
 *   <li>Occlumency reads as zero, so Legilimency goes straight through — {@link #occlumencyFor}.</li>
 * </ul>
 *
 * <h2>What it deliberately does not do</h2>
 * <p>It does not touch chat, names, or anything a player says. It does not reveal a player's
 * location, inventory or vault. Nothing here grants the interrogator a capability they did not
 * already have — Legilimency still has to be cast, and still has its own gate. This makes Veritaserum
 * an <em>opener</em> for an existing ability rather than a private-data leak wearing a potion's name,
 * which matters because a dose can be slipped into somebody's drink without their agreement.
 *
 * <h2>Server authority</h2>
 * <p>Every field lives in a server-side attachment and every check runs here. The client is told
 * nothing except the glow, which it could already see.
 */
@NullMarked
public final class VeritaserumService {

    /** Three minutes. Long enough to be interrogated in, short enough not to be a sentence. */
    public static final int DEFAULT_DURATION_TICKS = 20 * 180;

    /** How often the tick re-checks that nothing has re-concealed the drinker. */
    private static final int ENFORCE_INTERVAL_TICKS = 10;

    private VeritaserumService() {}

    // ── state ──────────────────────────────────────────────────────────────

    public static VeritaserumState get(Player player) {
        return player.getData(ModAttachments.VERITASERUM_STATE.get());
    }

    private static void set(ServerPlayer player, VeritaserumState state) {
        player.setData(ModAttachments.VERITASERUM_STATE.get(), state);
    }

    /** Whether this player is currently compelled. Safe on any player, on either side. */
    public static boolean isCompelled(Player player) {
        return get(player).isActive();
    }

    // ── drinking ───────────────────────────────────────────────────────────

    /**
     * Take a dose.
     *
     * <p>A second dose extends rather than refusing or punishing. Veritaserum has no fictional
     * overdose the way Felix does, and refusing would let a target render themselves immune by
     * drinking a weak dose of their own first — the opposite of what the potion is for.
     */
    public static void dose(ServerPlayer player, int durationTicks) {
        VeritaserumState state = get(player);
        int next = Math.max(1, durationTicks);
        boolean fresh = !state.isActive();
        set(player, new VeritaserumState(Math.max(state.ticksRemaining(), next),
                player.level().getGameTime()));

        // Strip first, then announce: the disguise ending is the thing that happened, and telling
        // somebody they cannot hide before actually un-hiding them would be a window to act in.
        strip(player);

        if (fresh) {
            PlayerFeedback.toast(player, NoticeKind.WARN,
                    Component.translatable("veritaserum.wizards_and_beasts.dosed.title"),
                    Component.translatable("veritaserum.wizards_and_beasts.dosed.body"));
        } else {
            PlayerFeedback.actionBar(player,
                    Component.translatable("veritaserum.wizards_and_beasts.deepened"));
        }
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.6f, 0.7f);
            level.sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 1.2, player.getZ(), 18, 0.3, 0.4, 0.3, 0.01);
        }
    }

    // ── the compulsion ─────────────────────────────────────────────────────

    /**
     * Whether this player may not start a disguise right now.
     *
     * <p>Consulted by {@code PolyjuiceService.drink}. Phrased as a question about the <em>player</em>
     * rather than as a Polyjuice-specific hook so any future disguise route asks the same thing.
     */
    public static boolean blocksDisguise(Player player) {
        return isCompelled(player);
    }

    /**
     * The Occlumency this player can bring to bear, which is none while compelled.
     *
     * <p>Wraps the ordinary read rather than replacing it, so the one call site in
     * {@code LegilimencyServerLogic} stays a single expression and there is no second place where
     * Occlumency could be resolved differently.
     */
    public static float occlumencyFor(Player player, float ordinaryLevel) {
        return isCompelled(player) ? 0.0f : ordinaryLevel;
    }

    /**
     * Undo whatever the drinker is currently hiding behind.
     *
     * <p>Called on the dose and again on a slow tick. The repeat matters: a compelled player who
     * quaffs an invisibility potion, or steps back under a Demiguise cloak, must not get to keep it,
     * and a one-shot strip at dose time would make the whole compulsion trivially defeatable by
     * re-applying a second later.
     */
    private static void strip(ServerPlayer player) {
        PolyjuiceService.revert(player, true);
        player.removeEffect(MobEffects.INVISIBILITY);
        // Re-applied rather than topped up so it never lapses a tick before the compulsion does.
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING,
                ENFORCE_INTERVAL_TICKS * 3, 0, true, false, true));
    }

    // ── the clock ──────────────────────────────────────────────────────────

    /** One tick of somebody's compulsion. Cheap for everyone who is not under one. */
    public static void tick(ServerPlayer player) {
        VeritaserumState state = get(player);
        if (!state.isActive()) {
            return;
        }
        int next = state.ticksRemaining() - 1;
        if (next <= 0) {
            set(player, VeritaserumState.NONE);
            player.removeEffect(MobEffects.GLOWING);
            PlayerFeedback.actionBar(player,
                    Component.translatable("veritaserum.wizards_and_beasts.wears_off"));
            return;
        }
        set(player, state.withTicks(next));
        if (next % ENFORCE_INTERVAL_TICKS == 0) {
            strip(player);
        }
    }
}
