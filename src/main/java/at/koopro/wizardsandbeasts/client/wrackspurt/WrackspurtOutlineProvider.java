package at.koopro.wizardsandbeasts.client.wrackspurt;

import at.koopro.wizardsandbeasts.client.render.outline.EntityOutlines;
import at.koopro.wizardsandbeasts.wrackspurt.Wrackspurt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;

/**
 * The violet outline a Dirigible Plum puts on anything hiding nearby.
 *
 * <p><b>Only you see it, and it costs nothing on the wire.</b> The outline is a client render-state
 * field ({@code EntityOutlines}), and the decision is made against the <em>local</em> player's own
 * effect — so it is per-viewer by construction rather than by any filtering, and no packet, no
 * attachment and no server tick are involved.
 *
 * <p>This is also why it cannot be spoofed into a wallhack: it draws vanilla's outline, which the
 * post-processing chain already renders through walls for the Glowing effect. That is the intended
 * behaviour here — the point of Wrackspurt Sight is noticing the person who is not where they appear
 * not to be.
 */
@NullMarked
public final class WrackspurtOutlineProvider {

    private WrackspurtOutlineProvider() {}

    /** Registered once, from the client setup. */
    public static void register() {
        EntityOutlines.registerProvider(WrackspurtOutlineProvider::colourFor);
    }

    private static int colourFor(Entity target) {
        LocalPlayer viewer = Minecraft.getInstance().player;
        if (viewer == null || target == viewer) {
            return EntityOutlines.NO_OUTLINE;
        }
        if (!Wrackspurt.canSee(viewer)) {
            return EntityOutlines.NO_OUTLINE;
        }
        if (!Wrackspurt.inRange(viewer, target) || !Wrackspurt.isConcealed(target)) {
            return EntityOutlines.NO_OUTLINE;
        }
        // ARGB.opaque, not the bare RGB: a colour with alpha 0 reads back as "no outline" and fails
        // silently. EntityOutlines' own javadoc warns about exactly this.
        return ARGB.opaque(Wrackspurt.OUTLINE_RGB);
    }
}
