package at.koopro.wizardsandbeasts.client.particle;

import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.spell.core.SpellFamily;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

final class FamilyTintParticle extends SingleQuadParticle {

    private final float baseSize;

    private FamilyTintParticle(ClientLevel level, double x, double y, double z,
                               double xd, double yd, double zd,
                               SpellTintParticleOptions options, SpellFamily family,
                               SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, xd, yd, zd, sprites.get(random));

        int c = options.argb();
        float fr = ((c >> 16) & 0xFF) / 255.0f;
        float fg = ((c >> 8) & 0xFF) / 255.0f;
        float fb = (c & 0xFF) / 255.0f;
        setColor(fr, fg, fb);
        float fa = ((c >>> 24) & 0xFF) / 255.0f;
        setAlpha(fa <= 1.0e-4f ? 0.92f : Mth.clamp(fa, 0.35f, 1.0f));

        baseSize = switch (family) {
            case FIRE -> 0.11f;
            case ICE -> 0.12f;
            case ELECTRIC -> 0.10f;
            case WATER -> 0.09f;
            case LIGHT -> 0.13f;
            case DARK -> 0.11f;
            case ARCANE -> 0.11f;
        };
        quadSize = baseSize;

        lifetime = random.nextInt(7) + 7;
        friction = 0.96f;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    static FamilyTintParticle create(ClientLevel level, double x, double y, double z,
                                     SpellTintParticleOptions options, SpellFamily family,
                                     SpriteSet sprites, RandomSource random) {
        double vx = (random.nextDouble() - 0.5) * 0.015;
        double vy = switch (family) {
            case FIRE -> 0.018 + random.nextDouble() * 0.012;
            case WATER -> -0.012 - random.nextDouble() * 0.01;
            case LIGHT -> 0.004 + random.nextDouble() * 0.006;
            default -> (random.nextDouble() - 0.5) * 0.012;
        };
        double vz = (random.nextDouble() - 0.5) * 0.015;
        return new FamilyTintParticle(level, x, y, z, vx, vy, vz, options, family, sprites, random);
    }

    @Override
    public float getQuadSize(float scaleFactor) {
        float t = ((float) age + scaleFactor) / (float) lifetime;
        return baseSize * (1.0f - 0.35f * Mth.clamp(t, 0.0f, 1.0f));
    }

    @Override
    public void tick() {
        super.tick();
        if (age > lifetime / 2) {
            setAlpha(alpha * 0.94f);
        }
    }
}
