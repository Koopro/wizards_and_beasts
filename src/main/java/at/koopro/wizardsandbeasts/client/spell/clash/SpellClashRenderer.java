package at.koopro.wizardsandbeasts.client.spell.clash;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.client.beam.BeamStyle;
import at.koopro.wizardsandbeasts.client.beam.Laser;
import at.koopro.wizardsandbeasts.client.beam.Lightning;
import at.koopro.wizardsandbeasts.client.beam.WandTipTracker;
import at.koopro.wizardsandbeasts.entity.spell.SpellClashEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a {@link SpellClashEntity}: a beam from each holding caster's wand tip to the joint, and a knot of
 * jagged bolts where the two meet, re-rolled a few times a second so the lock crackles.
 *
 * <p>A side that is not holding draws no beam. During the grace after the bolts meet, that is the cue: the
 * beam comes back out of your wand when you pick the lock up.
 *
 * <p>No new bolt code — this reuses {@link Lightning}, the beam system's jagged shape, and
 * {@link BeamStyle#lightning} for its white core with a coloured glow. Bolts alternate between the
 * two spells' colours, which is what makes a clash read as <em>these two</em> spells fighting
 * rather than a generic spark.
 *
 * <p>Everything is derived from the entity id and the tick, exactly as the wand beam does it, so
 * every client draws the same bolts with nothing extra on the wire.
 */
public class SpellClashRenderer extends EntityRenderer<SpellClashEntity, SpellClashRenderer.ClashState> {

    /** Short, wide-jittered bolts: a lock is a knot of lightning, not a tidy arc. */
    private static final Lightning BOLT = new Lightning(6, 4.0f, 2);
    /** The steady spell beam from a wand tip to the joint. */
    private static final Laser BEAM = new Laser();
    /** Lightning running along each beam, so the stream strains rather than sitting still. */
    private static final Lightning STRAIN = new Lightning(10, 3.0f, 2);
    /** How far along the axis each side's anchor sits, in blocks. */
    private static final double ARM = 0.55;
    /** Half-width of the random wander applied to each bolt's ends, in blocks. */
    private static final double SCATTER = 0.32;

    public SpellClashRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ClashState createRenderState() {
        return new ClashState();
    }

    @Override
    public void extractRenderState(SpellClashEntity clash, ClashState state, float partialTick) {
        super.extractRenderState(clash, state, partialTick);
        state.colorA = clash.getColorA();
        state.colorB = clash.getColorB();
        state.axis = clash.axis();
        state.ticks = clash.tickCount;
        state.seed = clash.getId();
        // Relative to the entity's interpolated position — that is where the submit pose sits.
        Vec3 reference = clash.getPosition(partialTick);
        state.tipA = clash.isHoldingA() ? tipOf(clash, clash.getCasterAId(), partialTick, reference) : null;
        state.tipB = clash.isHoldingB() ? tipOf(clash, clash.getCasterBId(), partialTick, reference) : null;
    }

    /** Where a caster's wand tip is drawn, relative to the joint — the same resolver the wand beam starts at. */
    private static @Nullable Vec3 tipOf(SpellClashEntity clash, int casterId, float partialTick, Vec3 reference) {
        Entity caster = casterId < 0 ? null : clash.level().getEntity(casterId);
        return caster instanceof LivingEntity living
                ? WandTipTracker.resolve(living, partialTick).subtract(reference)
                : null;
    }

    @Override
    public void submit(ClashState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState cameraState) {
        if (state.tipA != null) {
            drawBeam(state, state.tipA, state.colorA, 0, poseStack, collector);
        }
        if (state.tipB != null) {
            drawBeam(state, state.tipB, state.colorB, 1, poseStack, collector);
        }

        Vec3 axis = state.axis;
        if (axis.lengthSqr() < 1.0e-6) {
            return;
        }
        // Two perpendiculars, so the scatter fills a volume around the axis instead of a line.
        Vec3 side = Math.abs(axis.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 perpA = axis.cross(side).normalize();
        Vec3 perpB = axis.cross(perpA).normalize();

        Vec3 fromEnd = axis.scale(-ARM);
        Vec3 toEnd = axis.scale(ARM);

        int bolts = boltCount();
        for (int i = 0; i < bolts; i++) {
            // Same freeze-then-snap trick the beam uses: the shape holds for two ticks, so the bolts
            // flicker rather than boiling at the frame rate.
            RandomSource random = RandomSource.create(state.seed * 31L + i + (long) (state.ticks / 2) * 977L);
            Vec3 origin = fromEnd.add(scatter(random, perpA, perpB));
            Vec3 target = toEnd.add(scatter(random, perpA, perpB));

            // Alternate the two spells' colours; the core stays white, so the lock burns hot in the
            // middle and only the fringes carry the spells' own light.
            BeamStyle style = BeamStyle.lightning(i % 2 == 0 ? state.colorA : state.colorB);
            BOLT.render(style, origin, target, 1.0f, state.seed + i * 37, poseStack, collector,
                    state.ticks, state.partialTick);
        }
    }

    private static void drawBeam(ClashState state, Vec3 tip, int color, int side,
                                 PoseStack poseStack, SubmitNodeCollector collector) {
        BEAM.render(BeamStyle.laser(color), tip, Vec3.ZERO, 1.0f, state.seed + side,
                poseStack, collector, state.ticks, state.partialTick);
        STRAIN.render(BeamStyle.lightning(color), tip, Vec3.ZERO, 1.0f, state.seed * 7 + side,
                poseStack, collector, state.ticks, state.partialTick);
    }

    private static Vec3 scatter(RandomSource random, Vec3 perpA, Vec3 perpB) {
        return perpA.scale((random.nextDouble() - 0.5) * 2.0 * SCATTER)
                .add(perpB.scale((random.nextDouble() - 0.5) * 2.0 * SCATTER));
    }

    /** Bolt count follows the same performance preset the rest of the spell VFX obeys. */
    private static int boltCount() {
        return switch (Config.perfProfile) {
            case LOW -> 2;
            case MEDIUM -> 3;
            case HIGH -> 4;
        };
    }

    /**
     * The clash is a knot of lightning with beams reaching back to both casters, hung on a tiny hitbox, so
     * culling it against that hitbox would pop it out of view at the edge of the screen.
     */
    @Override
    protected boolean affectedByCulling(SpellClashEntity clash) {
        return false;
    }

    public static class ClashState extends EntityRenderState {
        public int colorA = 0xFFFFFF;
        public int colorB = 0xFFFFFF;
        public Vec3 axis = Vec3.ZERO;
        public int ticks;
        public int seed;
        /** Holding casters' wand tips, relative to the joint; {@code null} for a side drawing no beam. */
        public @Nullable Vec3 tipA;
        public @Nullable Vec3 tipB;
    }
}
