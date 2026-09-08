package at.koopro.wizardsandbeasts.form.sense;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Set;

/**
 * Applies {@link FormSense}s, and takes them away again cleanly.
 *
 * <h2>Night vision without the strobe</h2>
 * Vanilla flashes the screen once a Night Vision instance has fewer than 200 ticks left, so the obvious
 * implementation — re-apply a short effect every tick, as the Animagus passive service did with a
 * 40-tick duration — produces a permanent flicker for as long as the player is transformed. That is why
 * cat form was unpleasant to play in the dark. Here the instance is 400 ticks and is refreshed every
 * {@link #REFRESH_INTERVAL_TICKS}, so it never drops below 300 and never flashes.
 *
 * <p>Removal is conditional rather than unconditional: on revert the effect is stripped only if what is
 * on the player looks like ours (a duration no longer than the one we set). A player who drank an actual
 * Potion of Night Vision has thousands of ticks left and keeps it, which the old
 * {@code AnimagusAbilityService.clearPassives} did not manage.
 *
 * <h2>Scent, without two copies of the rule</h2>
 * The outline itself is drawn client-side with no packet behind it (see
 * {@code FormScentOutlineProvider}), which means the client has to know whether the viewer has the
 * sense. Rather than re-deriving "is this player a transformed cat or an unmedicated wolf" on the
 * client — two copies of one rule, guaranteed to drift — the server applies a marker effect and the
 * client asks only "do I have it". Mob effects already sync, so this costs nothing new on the wire, and
 * it is the same trick {@code Wrackspurt} uses for the same reason.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
@NullMarked
public final class FormSenseService {

    /** Comfortably above vanilla's 200-tick flash threshold, even at the moment before a refresh. */
    public static final int SENSE_EFFECT_DURATION_TICKS = 400;
    /** How often the effects are topped up. 100 ticks leaves 300 on the clock at the worst moment. */
    public static final int REFRESH_INTERVAL_TICKS = 100;
    /**
     * How far a nose carries, in blocks. Read by the client outline provider, so it lives here rather
     * than there — the range is a property of the sense, not of the drawing.
     *
     * <p>Deliberately short. Scent reveals through walls, which is a real power; at 16 blocks it is
     * "something warm is on the other side of this door" rather than a map-wide wallhack.
     */
    public static final double SCENT_RANGE = 16.0;

    private FormSenseService() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % REFRESH_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            apply(player, FormSenses.of(player));
        }
    }

    /**
     * Brings the player's sense effects in line with {@code senses}.
     *
     * <p>Idempotent and safe to call for an untransformed player — with an empty set it is exactly the
     * revert path. Call it directly on transform and on revert so a sense arrives and leaves on the same
     * tick as the form rather than up to {@link #REFRESH_INTERVAL_TICKS} later.
     */
    public static void apply(ServerPlayer player, Set<FormSense> senses) {
        if (senses.contains(FormSense.NIGHT_EYES)) {
            grant(player, MobEffects.NIGHT_VISION);
        } else {
            revoke(player, MobEffects.NIGHT_VISION);
        }

        if (senses.contains(FormSense.SCENT_TRACK)) {
            grant(player, ModEffects.SCENT_TRACKING);
        } else {
            revoke(player, ModEffects.SCENT_TRACKING);
        }
        // FormSense.KEEN_SIGHT is client render tuning with no server effect to apply. See its javadoc.
    }

    /** Strips every sense this service can grant. The revert path, spelled out for callers. */
    public static void clear(ServerPlayer player) {
        apply(player, Set.of());
    }

    private static void grant(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        // ambient = true so it renders as an innate thing rather than a potion; showIcon = false because
        // a sense the body simply has does not belong in the potion tray.
        player.addEffect(new MobEffectInstance(
                effect, SENSE_EFFECT_DURATION_TICKS, 0, true, false, false));
    }

    /**
     * Removes an effect only if it looks like one we granted.
     *
     * <p>The duration test is the whole of it: ours is never above
     * {@link #SENSE_EFFECT_DURATION_TICKS}, and a brewed or commanded effect is far longer. A player who
     * drinks a Potion of Night Vision and then turns into a cat therefore keeps the potion when they
     * turn back, instead of having it silently confiscated.
     */
    private static void revoke(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        MobEffectInstance active = player.getEffect(effect);
        if (active != null && active.getDuration() <= SENSE_EFFECT_DURATION_TICKS) {
            player.removeEffect(effect);
        }
    }
}
