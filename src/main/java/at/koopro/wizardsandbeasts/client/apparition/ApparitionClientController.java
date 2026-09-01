package at.koopro.wizardsandbeasts.client.apparition;

import at.koopro.wizardsandbeasts.client.apparition.state.ClientApparitionPresentationState;
import at.koopro.wizardsandbeasts.client.apparition.state.ClientApparitionWardState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

/**
 * What a charge looks like: the gathering motes around the wizard, and the destination ring telling them
 * where they will land and whether letting go now is the right moment.
 *
 * <p>Two audiences, deliberately. The ring is drawn for the local player alone — it is an aiming aid, and
 * one on every charging wizard in a duel would be noise. The motes are drawn on everyone, because the
 * wind-up is the counter-play telegraph and the people who need to see it are the people close enough to
 * interrupt it.
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

    /**
     * The gathering palette: soot and deep violet, never the Floo's emerald.
     *
     * <p>Distinct from the ring colours above on purpose. The ring is a readout — is the window open, is
     * the spot warded — and answers in traffic-light colours. The motes are the magic itself, and they have
     * to read as Apparition from across a room without being mistaken for a grate flaring.
     */
    private static final int COLOUR_MOTE = 0x5B2E7A;
    private static final int COLOUR_MOTE_SOOT = 0x241E28;
    /** Once the window is open the motes brighten, so the moment to let go is legible from behind. */
    private static final int COLOUR_MOTE_OPEN = 0xD8C8FF;

    /** Motes spawned per frame per charging wizard. See {@link #drawChargeMotes}. */
    private static final int MOTES_PER_FRAME = 2;
    private static final double MOTE_RADIUS_MAX = 1.5;
    private static final double MOTE_RADIUS_MIN = 0.35;
    private static final double MOTE_COLUMN_HEIGHT = 1.9;
    /** Fraction of the distance to the body a mote covers per tick. */
    private static final double MOTE_PULL = 0.12;
    /** Motes rise as they are drawn in, so the swirl climbs the body rather than orbiting the ankles. */
    private static final double MOTE_RISE = 0.03;

    private ApparitionClientController() {
    }

    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        // Everyone's motes, not just the local player's. The charge packets are already addressed to
        // everyone tracking the caster precisely so a wind-up is something bystanders can see coming and
        // interrupt; until this drew them, that audience received the telegraph and was shown nothing.
        drawChargeMotes(mc);

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

    /**
     * The gathering: motes drawn inward toward every wizard currently holding a charge.
     *
     * <p>Deliberately per-frame and few. A handful of motes a frame accumulates into a continuous swirl at
     * any frame rate, where a full ring per frame -- what the destination ring below does, for one player,
     * because it has to be a readable shape -- would be a wall of particles once several people are
     * charging in the same room.
     */
    private static void drawChargeMotes(Minecraft mc) {
        if (mc.level == null) {
            return;
        }
        for (Map.Entry<Integer, ClientApparitionPresentationState.Charge> entry
                : ClientApparitionPresentationState.charges().entrySet()) {
            ClientApparitionPresentationState.Charge charge = entry.getValue();
            if (charge.destination() == null || charge.elapsed() <= 0) {
                continue;
            }
            Entity caster = mc.level.getEntity(entry.getKey());
            if (caster == null) {
                continue;
            }
            // Tightening as the window nears, so the swirl closing on the body is the same information the
            // destination ring carries -- readable from behind, and without looking away from the wizard.
            float progress = charge.determinationProgress();
            double radius = MOTE_RADIUS_MAX - (MOTE_RADIUS_MAX - MOTE_RADIUS_MIN) * progress;
            boolean open = charge.isWindowOpen();

            for (int i = 0; i < MOTES_PER_FRAME; i++) {
                double angle = mc.level.random.nextDouble() * Math.PI * 2.0;
                double height = mc.level.random.nextDouble() * MOTE_COLUMN_HEIGHT;
                double x = caster.getX() + Math.cos(angle) * radius;
                double z = caster.getZ() + Math.sin(angle) * radius;
                // Velocity toward the body, so each mote is visibly being drawn in rather than drifting.
                double pullX = (caster.getX() - x) * MOTE_PULL;
                double pullZ = (caster.getZ() - z) * MOTE_PULL;
                int colour = open ? COLOUR_MOTE_OPEN : (i % 2 == 0 ? COLOUR_MOTE : COLOUR_MOTE_SOOT);

                mc.level.addParticle(new DustParticleOptions(colour, 0.9f),
                        x, caster.getY() + height, z, pullX, MOTE_RISE, pullZ);
                // Ash threaded through it, on the same inward path. Dust alone washes out against a bright
                // sky, and the ash is what makes the swirl read as soot rather than as a spell effect.
                if (!open && i == 0) {
                    mc.level.addParticle(ParticleTypes.ASH,
                            x, caster.getY() + height, z, pullX, MOTE_RISE, pullZ);
                }
            }
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
