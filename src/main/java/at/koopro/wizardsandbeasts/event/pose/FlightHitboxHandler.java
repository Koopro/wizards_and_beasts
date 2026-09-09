package at.koopro.wizardsandbeasts.event.pose;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.pose.ClientPoseState;
import at.koopro.wizardsandbeasts.event.ability.FormHitboxHandler;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.pose.FlightHitbox;
import at.koopro.wizardsandbeasts.pose.FlightPoseState;
import at.koopro.wizardsandbeasts.pose.PoseOverride;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Gives a player who is drawn lying flat a box that is not still standing up.
 *
 * <p>The flight pose lays the body out at 78 degrees at {@code PROPELLED} while the collision box
 * stayed the standing {@code 0.6 x 1.8} column. That gap is visible without any debug tooling: you
 * suffocate on a ceiling your model is nowhere near, and arrows hit a metre of air above a body that
 * is horizontal.
 *
 * <p>It is a <em>flatten</em>, not a rotation — see {@link FlightHitbox} for why no rotation exists
 * to do. The box becomes vanilla's elytra box for the duration.
 *
 * <p>Runs on both sides, mirroring {@link FormHitboxHandler}: the server box is authoritative for
 * hit detection and suffocation, and the client box keeps the local player's movement prediction
 * agreeing with it. The state is read from the attachment on the server and from
 * {@link ClientPoseState} on the client, because that is the only place the client has it.
 *
 * <h2>Eye height is deliberately preserved</h2>
 *
 * <p>Vanilla's elytra box drops the eye to {@code 0.4}. Copying that here would move the camera
 * about 1.2 blocks every time a player crosses the {@code PROPELLED} speed threshold and back when
 * they slow down, which the mod's own first-person flight poses ({@code FlightPoseConstants}) are
 * already tuned around. Two systems moving the same camera would fight. The box that decides what
 * you collide with changes; where you look from does not.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class FlightHitboxHandler {

    private FlightHitboxHandler() {}

    @SubscribeEvent
    public static void onEntitySize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!ModuleManager.isEnabled(Module.PLAYER_ANIMATION)) {
            return;
        }
        // An Animagus in flight is a bird, not a prone human. That handler owns the box outright;
        // this one must not fight it, and event listener order is not something to rely on.
        if (FormHitboxHandler.hasFormHitbox(player)) {
            return;
        }

        EntityDimensions prone = FlightHitbox.forState(stateOf(player));
        if (prone == null) {
            return;
        }
        // Keep the camera where the old box put it; only the collision shape changes.
        event.setNewSize(prone.withEyeHeight(event.getOldSize().eyeHeight()));
    }

    /** The player's flight attitude, from whichever side is asking. */
    @Nullable
    private static FlightPoseState stateOf(Player player) {
        PoseOverride override = player.level().isClientSide()
                ? ClientPoseState.get(player.getUUID())
                : player.getData(ModAttachments.POSE_OVERRIDE.get());
        return override.state().orElse(null);
    }
}
