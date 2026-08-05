package at.koopro.wizardsandbeasts.client.apparition;

import at.koopro.wizardsandbeasts.client.apparition.state.ClientApparitionPresentationState;
import at.koopro.wizardsandbeasts.client.apparition.state.ClientApparitionWardState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.NullMarked;

/**
 * The destination ring — where you will land, and whether letting go now is the right moment.
 *
 * <p>Driven entirely by {@link ClientApparitionPresentationState}, which holds nothing but what the server
 * last said. It used to count its own ticks against the datapack's client-side charge length and had only two
 * states, charged and not; it could therefore be confidently wrong about both the moment and the place. Every
 * number below now comes off the wire, so a disagreement with the server is a bug rather than drift.
 *
 * <p>Three readable states, because the player is aiming at exactly one of them:
 * <ul>
 *   <li><b>Determination</b> — a partial arc that fills. Nothing to do yet.</li>
 *   <li><b>Deliberation</b> — a closed double ring, bright. This is the moment; it must not be mistakable
 *       for the state either side of it.</li>
 *   <li><b>Closed</b> — dim and red. You held too long and the discharge is coming.</li>
 * </ul>
 */
@NullMarked
public final class ApparitionClientController {

    /** Points around a full ring. The arc during Determination draws a leading fraction of these. */
    private static final int RING_POINTS = 28;
    private static final double RING_RADIUS = 0.7;
    /** Second, tighter ring drawn only while the window is open — the "now" marker. */
    private static final double INNER_RING_RADIUS = 0.45;

    private static final int COLOUR_CHARGING = 0xFFD94D;
    private static final int COLOUR_WINDOW_OPEN = 0xFFFFFF;
    private static final int COLOUR_CLOSED = 0x8A1F1F;
    private static final int COLOUR_BLOCKED = 0xFF3333;

    private ApparitionClientController() {
    }

    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        ClientApparitionPresentationState.Charge charge =
                ClientApparitionPresentationState.charge(player.getId());
        if (charge == null) {
            return;
        }
        Vec3 destination = charge.destination();
        if (destination == null) {
            // No viable landing spot right now. Drawing a ring somewhere would promise an arrival the server
            // has already decided it cannot honour.
            return;
        }

        boolean warded = ClientApparitionWardState.isWarded(mc.level.dimension().identifier(), destination);
        boolean windowOpen = charge.isWindowOpen();
        boolean windowClosed = charge.isWindowClosed();

        int colour = warded ? COLOUR_BLOCKED
                : windowClosed ? COLOUR_CLOSED
                : windowOpen ? COLOUR_WINDOW_OPEN
                : COLOUR_CHARGING;

        // During Determination only a leading arc is drawn, so the ring visibly closes as the window nears.
        // Once it opens, the ring is whole and gains a second one: closure plus doubling is a much harder
        // signal to misread than a colour change alone, which matters for anyone playing colour-blind.
        int points = windowOpen || windowClosed
                ? RING_POINTS
                : Math.max(2, Math.round(RING_POINTS * charge.determinationProgress()));

        drawRing(mc, destination, RING_RADIUS, points, colour);
        if (windowOpen && !warded) {
            drawRing(mc, destination, INNER_RING_RADIUS, RING_POINTS, colour);
        }
    }

    private static void drawRing(Minecraft mc, Vec3 centre, double radius, int points, int colour) {
        if (mc.level == null) {
            return;
        }
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * (i / (double) RING_POINTS);
            Vec3 p = centre.add(Math.cos(angle) * radius, 0.08, Math.sin(angle) * radius);
            mc.level.addParticle(new DustParticleOptions(colour, 1.0f), p.x, p.y, p.z, 0.0, 0.015, 0.0);
        }
    }
}
