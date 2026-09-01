package at.koopro.wizardsandbeasts.polyjuice;

import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.network.polyjuice.PolyjuiceSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;
import java.util.UUID;

/**
 * Wearing somebody else's face: the server half.
 *
 * <h2>Presentation, never authority</h2>
 * <p>A disguise changes what other clients <em>draw</em> and nothing else. The drinker keeps their own
 * UUID, so every permission check, team membership, claim, vault balance and scoreboard entry in the
 * game continues to resolve to the real person. There is deliberately no API here that hands out the
 * target's anything — the only thing this class can do is set an appearance and a timer.
 *
 * <p>That is worth stating as a rule because the tempting next feature is always "and villagers should
 * give you their discounts". Anything of that shape has to be built as an explicit, enumerated
 * exception, never by making the disguise authoritative.
 *
 * <h2>Everybody sees it</h2>
 * <p>Broadcast to every tracking client, not only to the drinker — a Polyjuice that only fooled the
 * person who drank it would be a screen effect. Same shape as {@code ClientPetrifyState}.
 */
@NullMarked
public final class PolyjuiceService {

    /** An hour in the fiction. Long enough to be a plan, short enough to be a risk. */
    public static final int DEFAULT_DURATION_TICKS = 20 * 300;

    /** The lurch as the body reshapes. Brief and non-negotiable — it is the tell. */
    private static final int TRANSFORM_NAUSEA_TICKS = 60;

    private PolyjuiceService() {}

    public static PolyjuiceState get(ServerPlayer player) {
        return player.getData(ModAttachments.POLYJUICE_STATE.get());
    }

    private static void set(ServerPlayer player, PolyjuiceState state) {
        player.setData(ModAttachments.POLYJUICE_STATE.get(), state);
        PolyjuiceSyncS2CPayload.broadcast(player, state);
    }

    public static boolean isDisguised(ServerPlayer player) {
        return get(player).isDisguised();
    }

    /** Why a dose did or did not take. */
    public enum Result {
        TRANSFORMED,
        NO_SAMPLE,
        ALREADY_DISGUISED
    }

    /**
     * Drink a dose brewed with somebody's hair.
     *
     * <p>Refuses outright while already disguised rather than swapping face mid-effect. Two Polyjuice
     * doses stacking would mean a player could rotate through identities without ever appearing as
     * themselves, which removes the one window in which a disguise can be caught — and being caught
     * between faces is the entire tension of the thing.
     */
    public static Result drink(ServerPlayer player, Optional<UUID> targetId, String targetName,
                               int durationTicks) {
        if (targetId.isEmpty() || targetName.isBlank()) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("polyjuice.wizards_and_beasts.no_sample"));
            return Result.NO_SAMPLE;
        }
        if (get(player).isDisguised()) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("polyjuice.wizards_and_beasts.already"));
            return Result.ALREADY_DISGUISED;
        }

        set(player, new PolyjuiceState(Math.max(1, durationTicks), targetId, targetName));
        player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, TRANSFORM_NAUSEA_TICKS,
                0, false, true, true));
        transformEffects(player);
        PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                Component.translatable("polyjuice.wizards_and_beasts.became.title"),
                Component.translatable("polyjuice.wizards_and_beasts.became.body", targetName));
        return Result.TRANSFORMED;
    }

    /** End a disguise now. Safe to call on somebody who is not wearing one. */
    public static void revert(ServerPlayer player, boolean announce) {
        if (!get(player).isDisguised()) {
            return;
        }
        set(player, PolyjuiceState.NONE);
        transformEffects(player);
        if (announce) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("polyjuice.wizards_and_beasts.revert"));
        }
    }

    /** One tick of a disguise. A no-op for the overwhelming majority of players. */
    public static void tick(ServerPlayer player) {
        PolyjuiceState state = get(player);
        if (!state.isDisguised()) {
            return;
        }
        int next = state.ticksRemaining() - 1;
        if (next <= 0) {
            revert(player, true);
            return;
        }
        // Written straight to the attachment rather than through set(): a countdown does not change
        // the appearance, and re-broadcasting it every tick would be a packet per disguised player
        // per tick to tell every client a number none of them render.
        player.setData(ModAttachments.POLYJUICE_STATE.get(), state.withTicks(next));

        // The last five seconds show. A disguise that ended without warning would be a trap rather
        // than a risk; this is the moment a careful player excuses themselves.
        if (next == 100) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("polyjuice.wizards_and_beasts.fading"));
        }
    }

    /** Re-send one player's disguise to a client that has just started tracking them. */
    public static void resyncTo(ServerPlayer viewer, ServerPlayer subject) {
        PolyjuiceSyncS2CPayload.sendTo(viewer, subject.getUUID(), get(subject));
    }

    private static void transformEffects(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW,
                SoundSource.PLAYERS, 0.9f, 0.6f);
        level.sendParticles(ParticleTypes.SMOKE,
                player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.7, 0.4, 0.03);
        level.sendParticles(ParticleTypes.WITCH,
                player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.7, 0.4, 0.02);
    }
}
