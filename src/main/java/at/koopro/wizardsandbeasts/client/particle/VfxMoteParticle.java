package at.koopro.wizardsandbeasts.client.particle;

import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;

/**
 * A tinted, full-bright mote that goes where it is thrown: drawn toward a caster by Accio, blown away by Depulso,
 * gathered into a wand tip by Nox. The family tint particles pick their own drift and ignore the spawner's velocity,
 * which is right for bursts and wrong for anything that has to move with intent (spell VFX renaissance, 2026-11-15).
 *
 * <p>Its sprite steps through the frames of {@code particles/vfx_mote.json} by age, a pixel twinkle rather than a
 * random still frame, and its opacity drops in steps like the rest of the mod's particles.
 */
public final class VfxMoteParticle extends SingleQuadParticle {

    private static final float[] ALPHA_STEPS = {1.0f, 0.9f, 0.7f, 0.45f};

    private final SpriteSet sprites;

    private VfxMoteParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                            SpellTintParticleOptions options, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd, sprites.first());
        this.sprites = sprites;
        int c = options.argb();
        setColor(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
        setAlpha(ALPHA_STEPS[0]);
        quadSize = 0.13f + random.nextFloat() * 0.06f;
        lifetime = 12 + random.nextInt(6);
        friction = 0.94f;
        gravity = 0f;
        hasPhysics = false;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        setSpriteFromAge(sprites);
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
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
                new VfxMoteParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, options, sprites);
    }
}
