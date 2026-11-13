package at.koopro.wizardsandbeasts.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * The option-free magic particles a ward and the Killing Curse send: {@code protego_shatter} (fragments of a
 * broken shield, thrown out by the spawner's spread and falling) and {@code ak_bypass_flash} (a short green
 * flash where the curse passes through a shield).
 *
 * <p>Both types were registered and sent by the server with sprites drawn, but had no client provider, so the
 * client dropped every one of them. The sprites are greyscale like every other mod particle; the colour is the
 * meaning from {@code MagicColours} (protection, death). Both glow: they are pieces of a spell, lit by it.
 */
public final class FragmentParticle extends SingleQuadParticle {

    public enum Style {
        /** Glass from a breached ward: drifts out, tumbles down, steps out of sight. */
        SHATTER(at.koopro.wizardsandbeasts.spell.core.MagicColours.PROTECTION, 0.10f, 16, 0.04f),
        /** The Killing Curse through a shield: a bright flash, gone in a few ticks. */
        FLASH(at.koopro.wizardsandbeasts.spell.core.MagicColours.DEATH, 0.22f, 5, 0.0f);

        final int argb;
        final float size;
        final int life;
        final float gravity;

        Style(int argb, float size, int life, float gravity) {
            this.argb = argb;
            this.size = size;
            this.life = life;
            this.gravity = gravity;
        }
    }

    private final Style style;
    private final float baseSize;

    private FragmentParticle(ClientLevel level, double x, double y, double z,
                             double xd, double yd, double zd, Style style, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd, sprites.get(level.getRandom()));
        this.style = style;
        int c = style.argb;
        setColor(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
        setAlpha(0.95f);
        baseSize = style.size * (0.8f + random.nextFloat() * 0.4f);
        quadSize = baseSize;
        lifetime = style.life + random.nextInt(Math.max(1, style.life / 2));
        gravity = style.gravity;
        friction = 0.92f;
        hasPhysics = style == Style.SHATTER;
        // The spawner's spread is the throw: keep it (vanilla's constructor adds its own jitter).
        this.xd = xd;
        this.yd = yd + (style == Style.SHATTER ? 0.03 : 0.0);
        this.zd = zd;
        roll = random.nextFloat() * Mth.TWO_PI;
        oRoll = roll;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        if (style == Style.SHATTER) {
            oRoll = roll;
            roll += 0.25f;
        }
        // Stepped fade, like the rest of the mod's VFX: full, then half, then gone.
        float t = (float) age / (float) lifetime;
        setAlpha(t < 0.6f ? 0.95f : 0.5f);
    }

    @Override
    public float getQuadSize(float partialTick) {
        if (style == Style.FLASH) {
            float t = Mth.clamp((age + partialTick) / lifetime, 0.0f, 1.0f);
            return baseSize * (1.0f + 0.6f * t);
        }
        return baseSize;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites, Style style) {
        return (options, level, x, y, z, xSpeed, ySpeed, zSpeed, random) ->
                new FragmentParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, style, sprites);
    }
}
