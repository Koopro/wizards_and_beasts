package at.koopro.wizardsandbeasts.client.spell;

import at.koopro.wizardsandbeasts.entity.spell.SpellProjectileEntity;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Renders spell projectiles as a glowing bolt with a colored comet trail.
 * Uses the same additive-blended lightning render type as the wand beam.
 * Color is derived from the spell's color field.
 */
public class SpellProjectileRenderer extends EntityRenderer<SpellProjectileEntity, SpellProjectileRenderer.ProjectileState> {

    public SpellProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ProjectileState createRenderState() {
        return new ProjectileState();
    }

    @Override
    public void extractRenderState(SpellProjectileEntity entity, ProjectileState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        Spell spell = Spells.byId(entity.getSpellId());
        state.spellColor = spell != null ? spell.getColor() : 0xFFFFFFFF;
        state.jet = spell != null && JET_LOOK.stream().anyMatch(id -> at.koopro.wizardsandbeasts.spell.core.SpellIds.matches(spell.getId(), id));
        state.flown = entity.tickCount + partialTick;
        Vec3 motion = entity.getDeltaMovement();
        state.dx = (float) motion.x;
        state.dy = (float) motion.y;
        state.dz = (float) motion.z;
    }

    @Override
    public void submit(ProjectileState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        float r = ((state.spellColor >> 16) & 0xFF) / 255.0f;
        float g = ((state.spellColor >> 8) & 0xFF) / 255.0f;
        float b = (state.spellColor & 0xFF) / 255.0f;

        Vec3 vel = new Vec3(state.dx, state.dy, state.dz);
        double speed = vel.length();
        if (speed < 0.001) return;

        Vec3 dir = vel.normalize();
        if (state.jet) {
            submitJet(state, dir, speed, poseStack, collector);
            return;
        }
        float trailLength = (float) Math.min(speed * 5.0, 2.5);

        // Perpendicular axes for quad orientation
        Vec3 arbitrary = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 perp1 = dir.cross(arbitrary).normalize();
        Vec3 perp2 = dir.cross(perp1).normalize();

        // 3 layers: outer glow, mid glow, core — like the wand beam
        //           width, alpha, colorBlend (1.0 = pure spell color, 0.0 = white)
        float[][] layers = {
                {0.25f, 0.15f, 0.35f},  // outer: wide, transparent, washed out
                {0.12f, 0.35f, 0.6f},   // mid
                {0.05f, 0.85f, 1.0f},   // core: narrow, bright, pure color
        };

        int segments = 6;

        collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, consumer) -> {
            Matrix4f matrix = pose.pose();

            for (float[] lp : layers) {
                float w = lp[0];
                float a = lp[1];
                float blend = lp[2];
                float lr = r * blend + (1.0f - blend);
                float lg = g * blend + (1.0f - blend);
                float lb = b * blend + (1.0f - blend);

                // Head glow: two crossed quads at the projectile position
                renderHead(matrix, consumer, perp1, perp2, w * 1.5f, lr, lg, lb, a);

                // Trail: fading segments stretching backward along velocity
                for (int i = 0; i < segments; i++) {
                    float t0 = i / (float) segments;
                    float t1 = (i + 1) / (float) segments;

                    Vec3 p1 = dir.scale(-t0 * trailLength);
                    Vec3 p2 = dir.scale(-t1 * trailLength);

                    float fade0 = 1.0f - t0;
                    float fade1 = 1.0f - t1;
                    float w1 = w * (0.3f + 0.7f * fade0);
                    float w2 = w * (0.3f + 0.7f * fade1);

                    trailQuad(matrix, consumer, p1, p2, perp1, w1, w2, lr, lg, lb, a * fade0, a * fade1);
                    trailQuad(matrix, consumer, p1, p2, perp2, w1, w2, lr, lg, lb, a * fade0, a * fade1);
                }
            }
        });
    }

    /**
     * Spells drawn as a jet: the example of the 2026-11-12 spell pass, shown to the owner before any other spell
     * moves to it. The comet above is a 0.05-block additive ribbon that washes out against a day sky; seen from
     * ten blocks a Stunner was a faint pink dot (spell showcase). The jet is the beams' own whole-pixel geometry,
     * a red rod two pixels thick with stepped shells, alpha-blended so the red stays red against the sky.
     */
    static final java.util.Set<String> JET_LOOK = java.util.Set.of("stupefy");

    /** A jet's length behind its head, in blocks: the canon "jet of light", short enough to read as a bolt. */
    private static final double JET_LENGTH = 1.6;

    private static void submitJet(ProjectileState state, Vec3 dir, double speed, PoseStack poseStack,
                                  SubmitNodeCollector collector) {
        // Never longer than the way it has come, so the tail does not reach back into the caster's hand.
        double length = Math.min(JET_LENGTH, Math.max(0.2, state.flown * speed));
        int rgb = state.spellColor & 0xFFFFFF;
        int glow = mixRgb(rgb, 0xFFFFFF, 0.25f);
        at.koopro.wizardsandbeasts.client.beam.BeamStyle style = new at.koopro.wizardsandbeasts.client.beam.BeamStyle(
                2.0f, 2.0f, rgb, glow, 1.0f, 0.6f, 2, 0.0f, false, 0.5f);
        new at.koopro.wizardsandbeasts.client.beam.Laser().render(style, dir.scale(-length), Vec3.ZERO, 1.0f, 0,
                poseStack, collector, (int) state.flown, state.flown - (int) state.flown);
    }

    private static int mixRgb(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static void renderHead(Matrix4f matrix, VertexConsumer consumer,
                                    Vec3 axis1, Vec3 axis2, float size,
                                    float r, float g, float b, float a) {
        // Two perpendicular quads at origin form a star visible from all angles
        for (Vec3 primary : new Vec3[]{axis1, axis2}) {
            Vec3 secondary = (primary == axis1) ? axis2 : axis1;
            Vec3 p = primary.scale(size);
            Vec3 s = secondary.scale(size * 0.5f);

            consumer.addVertex(matrix, (float) (-p.x - s.x), (float) (-p.y - s.y), (float) (-p.z - s.z))
                    .setColor(r, g, b, a);
            consumer.addVertex(matrix, (float) (-p.x + s.x), (float) (-p.y + s.y), (float) (-p.z + s.z))
                    .setColor(r, g, b, a);
            consumer.addVertex(matrix, (float) (p.x + s.x), (float) (p.y + s.y), (float) (p.z + s.z))
                    .setColor(r, g, b, a);
            consumer.addVertex(matrix, (float) (p.x - s.x), (float) (p.y - s.y), (float) (p.z - s.z))
                    .setColor(r, g, b, a);
        }
    }

    private static void trailQuad(Matrix4f matrix, VertexConsumer consumer,
                                   Vec3 p1, Vec3 p2, Vec3 perpAxis,
                                   float w1, float w2,
                                   float r, float g, float b,
                                   float a1, float a2) {
        Vec3 off1 = perpAxis.scale(w1);
        Vec3 off2 = perpAxis.scale(w2);

        consumer.addVertex(matrix,
                (float) (p1.x - off1.x), (float) (p1.y - off1.y), (float) (p1.z - off1.z))
                .setColor(r, g, b, a1);
        consumer.addVertex(matrix,
                (float) (p1.x + off1.x), (float) (p1.y + off1.y), (float) (p1.z + off1.z))
                .setColor(r, g, b, a1);
        consumer.addVertex(matrix,
                (float) (p2.x + off2.x), (float) (p2.y + off2.y), (float) (p2.z + off2.z))
                .setColor(r, g, b, a2);
        consumer.addVertex(matrix,
                (float) (p2.x - off2.x), (float) (p2.y - off2.y), (float) (p2.z - off2.z))
                .setColor(r, g, b, a2);
    }

    public static class ProjectileState extends EntityRenderState {
        public int spellColor = 0xFFFFFFFF;
        public float dx, dy, dz;
        public boolean jet;
        public float flown;
    }
}
