package at.koopro.wizardsandbeasts.client.beam;

import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisual;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a beam look into a GUI rectangle through the production renderer — the same {@code BeamEntityRenderer#submit}
 * and {@code BeamGeometry} every beam in the world uses, fed a render state built by hand (a GUI has no caster, wand tip
 * or ray). Nothing is spawned and nothing outlives the frame: the state is a local, so closing the screen that calls
 * this leaves no preview behind.
 */
public final class BeamPreviewViewport {

    /** Half the beam's length inside the viewport, in blocks. */
    private static final float HALF_SPAN = 1.1f;
    /** Turned off-axis so the rod reads as a volume rather than a flat bar. */
    private static final float YAW = 0.42f;
    private static final float PITCH = -0.20f;
    /** Fixed, so a bolt does not re-roll differently between two viewports showing the same tick. */
    private static final int SEED = 1337;

    private BeamPreviewViewport() {}

    /**
     * @param alpha brightness 0..1 (the preview's fade loop); 0 draws nothing
     * @param ticks the preview's own clock, so pausing freezes crackle, spin and sparks
     */
    public static void draw(GuiGraphics graphics, BeamVisual visual, float alpha, int ticks, float partialTick,
                            int x0, int y0, int x1, int y1) {
        if (alpha <= 0f || x1 - x0 < 8 || y1 - y0 < 8) {
            return;
        }
        BeamAppearance.Appearance look = BeamAppearance.of(visual);
        BeamRenderState state = new BeamRenderState();
        state.entityType = ModEntities.BEAM.get();
        state.style = look.style().withOpacityScale(alpha);
        state.shape = look.shape();
        state.origin = new Vec3(-HALF_SPAN, 0, 0);
        state.target = new Vec3(HALF_SPAN, 0, 0);
        state.progress = 1f;
        state.seed = SEED;
        state.valid = true;
        state.ticks = ticks;
        state.partialTick = partialTick;
        state.boundingBoxWidth = HALF_SPAN * 2f;
        state.boundingBoxHeight = 1f;
        // rotateZ(PI) first as the inventory does it (GUI Y points down), then a three-quarter view.
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).rotateY(YAW).rotateX(PITCH);
        float scale = Math.min((x1 - x0) * 0.4f / HALF_SPAN, (y1 - y0) * 2.5f);
        graphics.submitEntityRenderState(state, scale, new Vector3f(), rotation, null, x0, y0, x1, y1);
    }
}
