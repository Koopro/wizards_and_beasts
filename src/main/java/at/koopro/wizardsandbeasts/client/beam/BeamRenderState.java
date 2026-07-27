package at.koopro.wizardsandbeasts.client.beam;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Snapshot the beam renderer extracts on the main thread and draws from on the render thread. */
public class BeamRenderState extends EntityRenderState {
    public @Nullable BeamStyle style;
    public @Nullable BeamShape shape;
    /** Origin and target, relative to the beam entity's interpolated position. */
    public Vec3 origin = Vec3.ZERO;
    public Vec3 target = Vec3.ZERO;
    public float progress;
    public int seed;
    public int ticks;
    // No partialTick field here on purpose: EntityRenderState already declares one, and redeclaring
    // it shadows the parent's rather than overriding it. Two fields, one name, and which one you get
    // depends on the static type of the reference — it only worked because both were set alike.
    /** False when the caster could not be resolved to a living entity — skip drawing. */
    public boolean valid;
}
