package at.koopro.wizardsandbeasts.client.form.sense;

import at.koopro.wizardsandbeasts.client.render.outline.EntityOutlines;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.form.sense.FormSenseService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NullMarked;

/**
 * The warm outline a beast's nose puts on anything living nearby.
 *
 * <p><b>Only you see it, and it costs nothing on the wire.</b> The outline is a client render-state
 * field, and the decision is made against the <em>local</em> player's own effect — so it is per-viewer
 * by construction rather than by any filtering, and no packet, no attachment and no server tick are
 * involved. Same shape as {@code WrackspurtOutlineProvider}, for the same reasons.
 *
 * <p>The condition is a single marker effect rather than a client-side re-derivation of "am I a
 * transformed cat, or an unmedicated wolf". That rule lives once, on the server, in
 * {@code FormSenseService}; this end only asks whether the server said yes. Two copies of a rule that
 * has to agree across a network boundary is how a form ends up smelling things it should not.
 */
@NullMarked
public final class FormScentOutlineProvider {

    /** Blood-warm, matching the effect's own colour and distinct from Wrackspurt violet. */
    public static final int OUTLINE_RGB = 0xC2603A;

    private FormScentOutlineProvider() {}

    /** Registered once, from the client setup. */
    public static void register() {
        EntityOutlines.registerProvider(FormScentOutlineProvider::colourFor);
    }

    private static int colourFor(Entity target) {
        LocalPlayer viewer = Minecraft.getInstance().player;
        if (viewer == null || target == viewer) {
            return EntityOutlines.NO_OUTLINE;
        }
        if (!viewer.hasEffect(ModEffects.SCENT_TRACKING)) {
            return EntityOutlines.NO_OUTLINE;
        }
        // A nose finds things that are alive. Item frames, boats and armour stands do not smell of
        // anything, and outlining them would turn a sense into a block-entity radar.
        if (!(target instanceof LivingEntity living) || !living.isAlive()) {
            return EntityOutlines.NO_OUTLINE;
        }
        double range = FormSenseService.SCENT_RANGE;
        if (viewer.distanceToSqr(living) > range * range) {
            return EntityOutlines.NO_OUTLINE;
        }
        // ARGB.opaque, not the bare RGB: a colour with alpha 0 reads back as "no outline" and fails
        // silently. EntityOutlines' own javadoc warns about exactly this.
        return ARGB.opaque(OUTLINE_RGB);
    }
}
