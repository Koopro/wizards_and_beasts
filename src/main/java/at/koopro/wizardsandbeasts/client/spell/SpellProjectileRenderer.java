package at.koopro.wizardsandbeasts.client.spell;

import at.koopro.wizardsandbeasts.client.beam.BeamStyle;
import at.koopro.wizardsandbeasts.client.beam.Laser;
import at.koopro.wizardsandbeasts.entity.spell.SpellProjectileEntity;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellIds;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * Draws a spell bolt as a jet of light: a short rod in the spell's colour, built from the beams' own whole-pixel
 * geometry ({@link Laser} → {@code BeamGeometry}), two stepped shells and a few sparks, alpha-blended so the colour
 * stays the spell's colour against a bright sky.
 *
 * <p>Until 2026-11-13 bolts were a 0.05-block additive comet; from ten blocks in daylight every one of them was a
 * faint dot and all fifteen were the same shape (spell showcase). Stupefy was the example; the owner left the
 * rollout to the art pass, and every bolt now flies as a jet whose thickness and length say what the spell does.
 */
public class SpellProjectileRenderer extends EntityRenderer<SpellProjectileEntity, SpellProjectileRenderer.ProjectileState> {

    /**
     * A bolt's build: thickness in pixels and length in blocks. A cutting curse is a thin long line, a blasting
     * curse a short heavy slug, a jinx something between.
     */
    record Jet(float widthPx, double length) {}

    static final Jet DEFAULT_JET = new Jet(2.0f, 1.6);

    static final Map<String, Jet> JETS = Map.ofEntries(
            Map.entry("diffindo", new Jet(1.0f, 2.2)),
            Map.entry("confringo", new Jet(3.0f, 1.1)),
            Map.entry("expulso", new Jet(3.0f, 1.0)),
            Map.entry("engorgio", new Jet(3.0f, 1.3)),
            Map.entry("reducio", new Jet(1.0f, 1.2)),
            Map.entry("langlock", new Jet(1.0f, 1.4)),
            Map.entry("obscuro", new Jet(2.0f, 1.2)));

    /** The jet a spell flies as: its own build if it has one, else the plain two-pixel jet. */
    static Jet jetFor(String spellId) {
        for (Map.Entry<String, Jet> e : JETS.entrySet()) {
            if (SpellIds.matches(spellId, e.getKey())) {
                return e.getValue();
            }
        }
        return DEFAULT_JET;
    }

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
        state.jet = spell != null ? jetFor(spell.getId()) : DEFAULT_JET;
        state.flown = entity.tickCount + partialTick;
        Vec3 motion = entity.getDeltaMovement();
        state.dx = (float) motion.x;
        state.dy = (float) motion.y;
        state.dz = (float) motion.z;
    }

    @Override
    public void submit(ProjectileState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        Vec3 vel = new Vec3(state.dx, state.dy, state.dz);
        double speed = vel.length();
        if (speed < 0.001) {
            return;
        }
        Vec3 dir = vel.normalize();
        // Never longer than the way it has come, so the tail does not reach back into the caster's hand.
        double length = Math.min(state.jet.length(), Math.max(0.2, state.flown * speed));
        int rgb = state.spellColor & 0xFFFFFF;
        int glow = mixRgb(rgb, 0xFFFFFF, 0.25f);
        float width = state.jet.widthPx();
        BeamStyle style = new BeamStyle(width, width, rgb, glow, 1.0f, 0.6f, 2, 0.0f, false, 0.5f);
        new Laser().render(style, dir.scale(-length), Vec3.ZERO, 1.0f, 0,
                poseStack, collector, (int) state.flown, state.flown - (int) state.flown);
    }

    private static int mixRgb(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static class ProjectileState extends EntityRenderState {
        public int spellColor = 0xFFFFFFFF;
        public float dx, dy, dz;
        public Jet jet = DEFAULT_JET;
        public float flown;
    }
}
