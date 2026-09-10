package at.koopro.wizardsandbeasts.form;

import at.koopro.wizardsandbeasts.ability.AnimagusForms;
import at.koopro.wizardsandbeasts.client.form.state.ClientFormDataState;
import at.koopro.wizardsandbeasts.client.pose.ClientPoseState;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.pose.FlightHitbox;
import at.koopro.wizardsandbeasts.pose.FlightPoseState;
import at.koopro.wizardsandbeasts.pose.PoseOverride;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The one place that decides what shape a player collides as.
 *
 * <h2>Why this is not an {@code EntityEvent.Size} listener any more</h2>
 *
 * <p>That event is a <em>late</em> hook. It fires in exactly one place —
 * {@code Entity.refreshDimensions()} — and its result is stored in the cached
 * {@code dimensions} field. Every vanilla path that asks {@code getDimensions(pose)}
 * directly went straight past it and saw the untouched {@code 0.6 x 1.8} player box:
 *
 * <ul>
 *   <li>{@code Player.canPlayerFitWithinBlocksAndEntitiesWhen}, and through it
 *       {@code updatePlayerPose} — so a cat Animagus 1.4 blocks wide was asked whether a
 *       0.6-wide box fits down a one-block corridor, told yes, given {@code STANDING},
 *       and then wedged in the box it actually has.</li>
 *   <li>{@code wouldNotSuffocateAtTargetPose} — the suffocation test, on the wrong box.</li>
 *   <li>{@code getLocalBoundsForPose} — where a dismounting rider is put down.</li>
 *   <li>The passenger attachment point — riders sitting at the wrong height on a
 *       resized player.</li>
 * </ul>
 *
 * <p>{@code LivingEntityDimensionsMixin} calls this from {@code getDimensions} itself, so
 * all of those now ask the same question and get the same answer. The bodies below are the
 * old listeners unchanged; only who calls them moved.
 *
 * <h2>Sides</h2>
 *
 * <p>Runs on both. The server box is authoritative for hit detection, collision and
 * suffocation; the client box is what keeps the local player's movement prediction
 * agreeing with it. State comes from the attachment on the server and from the synced
 * client mirrors on the client, which is the only place the client has it.
 */
@NullMarked
public final class PlayerBoxOverrides {

    private PlayerBoxOverrides() {}

    /**
     * The box this player should collide as, or {@code null} to leave vanilla's alone.
     *
     * @param vanilla what vanilla computed — the same value the old listeners received as
     *               {@code EntityEvent.Size#getOldSize()}, which is why this is a
     *               behaviour-preserving move rather than a rewrite
     */
    @Nullable
    public static EntityDimensions resolve(Player player, EntityDimensions vanilla) {
        EntityDimensions form = formBox(player);
        if (form != null) {
            return form;
        }
        return flightBox(player, vanilla);
    }

    /**
     * An Animagus beast's own collision box.
     *
     * <p>The size system drives the player box through {@code Attributes.SCALE}, which
     * scales {@code 0.6 x 1.8} <em>uniformly</em> — so a cat would still collide as a tall
     * narrow sliver. Real beasts are wider than they are tall, so the form's explicit
     * {@link SizeProfile} hitbox replaces the computed size outright.
     *
     * <p>Built with {@link EntityDimensions#scalable} so the eye height keeps vanilla's
     * convention and the first-person camera sits somewhere believable on a short body.
     */
    @Nullable
    private static EntityDimensions formBox(Player player) {
        String formId = resolveFormId(player);
        if (!AnimagusForms.isAnimagusForm(formId)) {
            return null;
        }
        SizeProfile profile = SizeProfileRegistry.get(formId);
        if (profile == null) {
            return null;
        }
        return EntityDimensions.scalable(profile.hitboxWidth(), profile.hitboxHeight());
    }

    /**
     * A player drawn lying flat gets a box that is not still standing up.
     *
     * <p>It is a flatten, not a rotation — see {@link FlightHitbox} for why no rotation
     * exists to do. The box becomes vanilla's elytra box for the duration.
     *
     * <p><b>Eye height is deliberately preserved.</b> Vanilla's elytra box drops the eye to
     * {@code 0.4}. Copying that would move the camera about 1.2 blocks every time a player
     * crosses the {@code PROPELLED} threshold and back, which the mod's own first-person
     * flight poses are already tuned around. Two systems moving the same camera would
     * fight. What you collide with changes; where you look from does not.
     *
     * <p>An Animagus in flight is a bird, not a prone human, so {@link #formBox} is
     * consulted first and this never sees them.
     */
    @Nullable
    private static EntityDimensions flightBox(Player player, EntityDimensions vanilla) {
        if (!ModuleManager.isEnabled(Module.PLAYER_ANIMATION)) {
            return null;
        }
        EntityDimensions prone = FlightHitbox.forState(flightStateOf(player));
        return prone == null ? null : prone.withEyeHeight(vanilla.eyeHeight());
    }

    /** True when an Animagus form owns this player's collision box. */
    public static boolean hasFormHitbox(Player player) {
        return formBox(player) != null;
    }

    private static @Nullable String resolveFormId(Player player) {
        if (player.level().isClientSide()) {
            ClientFormDataState.FormData data = ClientFormDataState.get(player.getUUID());
            return data != null ? data.formId() : null;
        }
        return player.getData(ModAttachments.HERITAGE_DATA.get()).getActiveFormId();
    }

    /** The player's flight attitude, from whichever side is asking. */
    private static @Nullable FlightPoseState flightStateOf(Player player) {
        PoseOverride override = player.level().isClientSide()
                ? ClientPoseState.get(player.getUUID())
                : player.getData(ModAttachments.POSE_OVERRIDE.get());
        return override.state().orElse(null);
    }
}
