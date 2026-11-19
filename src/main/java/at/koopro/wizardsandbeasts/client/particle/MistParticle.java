package at.koopro.wizardsandbeasts.client.particle;

import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import org.jspecify.annotations.NullMarked;

/**
 * A sheet of ground mist: large, faint and slow, lying a hand's breadth to a knee's height over the ground and
 * drifting with no wind to speak of. Lit by the world, but never darker than a little moonlight, so a sheet under a
 * dark canopy still reads as pale vapour instead of a grey card.
 *
 * <p>A single sheet is almost nothing; a few overlapping ones read as a layer. Opacity rises and falls in five steps
 * (the mod's VFX has no smooth gradients) and never above {@link #PEAK}, so a dozen sheets in a line of sight still
 * leave the ground visible through them.
 */
@NullMarked
public final class MistParticle extends SingleQuadParticle {

    /** Highest opacity a sheet reaches: faint by design. */
    static final float PEAK = 0.16f;
    /** Moonlight on mist: it never goes darker than this packed light, so a sheet under a canopy still reads pale. */
    private static final int MOONLIT = (7 << 20) | (7 << 4);
    /** Opacity across the sheet's life, in steps: gathering, there, thinning. */
    private static final float[] STEPS = {0.3f, 0.7f, 1f, 0.7f, 0.3f};

    private MistParticle(ClientLevel level, double x, double y, double z, SpellTintParticleOptions options,
                         SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0, sprites.get(level.getRandom()));
        int c = options.argb();
        setColor(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
        quadSize = 1.4f + random.nextFloat() * 1.0f;
        lifetime = 160 + random.nextInt(100);
        gravity = 0f;
        friction = 0.995f;
        hasPhysics = false;
        xd = (random.nextDouble() - 0.5) * 0.006;
        yd = 0;
        zd = (random.nextDouble() - 0.5) * 0.006;
        setAlpha(PEAK * STEPS[0]);
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        int world = super.getLightColor(partialTick);
        int block = Math.max(world & 0xFFFF, MOONLIT & 0xFFFF);
        int sky = Math.max(world >>> 16, MOONLIT >>> 16);
        return (sky << 16) | block;
    }

    @Override
    public void tick() {
        super.tick();
        int step = Math.min(STEPS.length - 1, age * STEPS.length / Math.max(1, lifetime));
        setAlpha(PEAK * STEPS[step]);
    }

    public static ParticleProvider<SpellTintParticleOptions> provider(SpriteSet sprites) {
        return (options, level, x, y, z, xSpeed, ySpeed, zSpeed, random) ->
                new MistParticle(level, x, y, z, options, sprites);
    }
}
