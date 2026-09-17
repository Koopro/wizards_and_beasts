package at.koopro.wizardsandbeasts.disguise;

import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.network.disguise.DisguiseSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;
import java.util.UUID;

/**
 * Wearing somebody else's face: the server half, and the only way in.
 *
 * <h2>Presentation, never authority</h2>
 * <p>A disguise changes what other clients <em>draw</em> and nothing else. The wearer keeps their own
 * UUID, so every permission check, team membership, claim, vault balance and scoreboard entry in the
 * game continues to resolve to the real person. There is deliberately no method here that hands out
 * the target's anything — the only thing this class can do is set an appearance and a timer.
 *
 * <p>That is worth stating as a rule because the tempting next feature is always "and villagers should
 * give you their discounts". Anything of that shape has to be built as an explicit, enumerated
 * exception, never by making the disguise authoritative.
 *
 * <h2>Who calls this</h2>
 * <p>Two callers today and both are thin: {@code PolyjuiceService}, which adds the potion's rules
 * (a sample, a duration, a refusal under Veritaserum, nausea on the turn), and
 * {@code DisguiseCommands}, which adds none. Nothing in here knows about either — a caller supplies a
 * target and a duration, and that is the whole contract. A Metamorphmagus or a Boggart would be a
 * third caller and would need nothing added here.
 *
 * <h2>Everybody sees it</h2>
 * <p>Broadcast to every tracking client, not only to the wearer — a disguise that only fooled the
 * person wearing it would be a screen effect.
 */
@NullMarked
public final class DisguiseSystemAPI {

    /** How long before the end the wearer is told. Five seconds — time to leave a room, not a fight. */
    private static final int FADE_WARNING_TICKS = 100;

    private DisguiseSystemAPI() {}

    public static DisguiseState get(ServerPlayer player) {
        return player.getData(ModAttachments.DISGUISE_STATE.get());
    }

    public static boolean isDisguised(ServerPlayer player) {
        return get(player).isDisguised();
    }

    /**
     * Put a face on, replacing whatever is already there.
     *
     * <p>Unconditional by design. Every rule about <em>whether</em> a disguise may start — already
     * wearing one, under Veritaserum, holding no sample — belongs to the caller that has those rules,
     * because they differ per caller: a potion refuses to stack, an operator command must not. A
     * shared layer that enforced one caller's rules on the others would be a layer that quietly has
     * opinions.
     *
     * @param durationTicks how long it lasts, or {@link DisguiseState#INDEFINITE} for no clock
     * @param ceremony      whether to play the smoke and the sound. False for administrative changes,
     *                      which should not announce themselves to the room
     */
    public static void apply(ServerPlayer player, UUID targetId, String targetName,
                             int durationTicks, boolean ceremony) {
        set(player, new DisguiseState(
                durationTicks == DisguiseState.INDEFINITE
                        ? DisguiseState.INDEFINITE
                        : Math.max(1, durationTicks),
                Optional.of(targetId),
                targetName));
        if (ceremony) {
            transformEffects(player);
        }
    }

    /** A disguise with no clock. What the admin command and any future permanent form would use. */
    public static void applyIndefinite(ServerPlayer player, UUID targetId, String targetName) {
        apply(player, targetId, targetName, DisguiseState.INDEFINITE, false);
    }

    /**
     * End a disguise now.
     *
     * @return whether there was one to end. Safe to call on somebody wearing their own face.
     */
    public static boolean clear(ServerPlayer player, boolean ceremony) {
        if (!isDisguised(player)) {
            return false;
        }
        set(player, DisguiseState.NONE);
        if (ceremony) {
            transformEffects(player);
        }
        return true;
    }

    /**
     * One tick of the clock.
     *
     * <p>A no-op for the overwhelming majority of players, and for every indefinite disguise — an
     * admin disguise has no clock to run down, which is the only difference the countdown knows about
     * between the two callers.
     */
    public static void tick(ServerPlayer player) {
        DisguiseState state = get(player);
        if (!state.isDisguised() || state.isIndefinite()) {
            return;
        }
        DisguiseState next = state.tickDown();
        if (!next.isDisguised()) {
            clear(player, true);
            PlayerFeedback.actionBar(player,
                    Component.translatable("disguise.wizards_and_beasts.revert"));
            return;
        }
        // Written straight to the attachment rather than through set(): a countdown does not change
        // the appearance, and re-broadcasting it every tick would be a packet per disguised player
        // per tick to tell every client a number none of them render.
        player.setData(ModAttachments.DISGUISE_STATE.get(), next);

        // The last five seconds show. A disguise that ended without warning would be a trap rather
        // than a risk; this is the moment a careful player excuses themselves.
        if (next.ticksRemaining() == FADE_WARNING_TICKS) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("disguise.wizards_and_beasts.fading"));
        }
    }

    /** Re-send one player's disguise to a client that has just started tracking them. */
    public static void resyncTo(ServerPlayer viewer, ServerPlayer subject) {
        DisguiseSyncS2CPayload.sendTo(viewer, subject.getUUID(), get(subject));
    }

    /**
     * Re-send a player their own disguise.
     *
     * <p>Needed because {@code PlayerEvent.StartTracking} never fires for a player tracking
     * themselves: on login, respawn and every dimension change, the one client guaranteed <em>not</em>
     * to be told is the wearer's. Without this a rejoining disguised player sees their own face while
     * the whole server sees somebody else's, which is the one failure mode they cannot detect.
     */
    public static void resyncSelf(ServerPlayer player) {
        DisguiseSyncS2CPayload.sendTo(player, player.getUUID(), get(player));
    }

    private static void set(ServerPlayer player, DisguiseState state) {
        player.setData(ModAttachments.DISGUISE_STATE.get(), state);
        DisguiseSyncS2CPayload.broadcast(player, state);
    }

    /** The lurch as the body reshapes. Brief and loud — it is the tell. */
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
