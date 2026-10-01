package at.koopro.wizardsandbeasts.client.particle;

import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;

/**
 * A pixel smoke puff that dissipates in steps: a dense puff, a looser cloud, clumps breaking apart,
 * last wisps — the four frames of {@code particles/smoke_puff.json}, walked by age.
 *
 * <p>Replaces vanilla {@code LARGE_SMOKE} where the mod's own effects puffed smoke (visual style
 * report, B). The sprite is greyscale; the {@link SpellTintParticleOptions} colour gives it its
 * meaning ({@code MagicColours}), and its opacity drops in the same four steps as the frames rather
 * than fading smoothly — the rest of the mod's VFX has no smooth gradients either.
 */
public final class SmokePuffParticle extends SingleQuadParticle {

    /** Opacity per frame: dense, loosening, breaking, wisps. */
    private static final float[] ALPHA_STEPS = {0.90f, 0.78f, 0.58f, 0.36f};

    private final SpriteSet sprites;

    private SmokePuffParticle(ClientLevel level, double x, double y, double z,
                              double xd, double yd, double zd,
                              SpellTintParticleOptions options, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd, sprites.first());
        this.sprites = sprites;
        int c = options.argb();
        setColor(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
        setAlpha(ALPHA_STEPS[0]);
        // Bigger than a mote, smaller than vanilla's large smoke: a burst is a handful of these,
        // and each should read as one puff rather than as fog.
        quadSize = 0.22f + random.nextFloat() * 0.10f;
        lifetime = 18 + random.nextInt(9);
        friction = 0.90f;
        gravity = -0.015f;
        hasPhysics = false;
        // Keep the spawner's spread, damped: smoke drifts, it is not thrown.
        this.xd = xd * 0.5;
        this.yd = yd * 0.5 + 0.01;
        this.zd = zd * 0.5;
        setSpriteFromAge(sprites);
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
        int frame = Math.min(ALPHA_STEPS.length - 1, age * ALPHA_STEPS.length / Math.max(1, lifetime));
        setAlpha(ALPHA_STEPS[frame]);
    }

    public static ParticleProvider<SpellTintParticleOptions> provider(SpriteSet sprites) {
        return (options, level, x, y, z, xSpeed, ySpeed, zSpeed, random) ->
                new SmokePuffParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, options, sprites);
    }
}
