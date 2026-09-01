package at.koopro.wizardsandbeasts.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * The slipstream a broom sheds at speed — one class, three palettes.
 *
 * <p>What separates a Cleansweep's dust from a Firebolt's embers is colour, size, lifetime and how
 * it moves; none of that needs its own class, and three near-identical particles would be three
 * places to fix the next time the trail is retuned. All three share the {@code spell_mote} sprite
 * rather than shipping three PNGs differing only in hue.
 *
 * <p>Deliberately short-lived and small. A trail is read at speed and from behind, where a long-lived
 * particle turns into a wall the rider is looking through — the Firebolt's embers live barely half a
 * second precisely because it is the broom most likely to be flown flat out.
 */
public final class BroomTrailParticle extends SingleQuadParticle {

    /** What a given trail type looks like. Colours are the wood and metal each family already uses. */
    public enum Style {
        /** Cleansweep and the generic broom: pale kicked-up dust, drifts and dies. */
        DUST(0.62f, 0.56f, 0.45f, 0.10f, 10, 4, 0.94f, 0.0006f),
        /** Nimbus: bright metallic sparks off the collar, tight and quick. */
        GOLD(0.85f, 0.69f, 0.29f, 0.08f, 8, 3, 0.90f, 0.0002f),
        /** Firebolt: embers, hottest and shortest-lived of the three, and they rise. */
        EMBER(0.91f, 0.46f, 0.23f, 0.09f, 7, 3, 0.88f, -0.0016f);

        private final float red;
        private final float green;
        private final float blue;
        private final float size;
        private final int baseLifetime;
        private final int lifetimeJitter;
        private final float friction;
        /** Positive sinks, negative rises. Embers rise; dust falls. */
        private final float gravity;

        Style(float red, float green, float blue, float size, int baseLifetime, int lifetimeJitter,
              float friction, float gravity) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.size = size;
            this.baseLifetime = baseLifetime;
            this.lifetimeJitter = lifetimeJitter;
            this.friction = friction;
            this.gravity = gravity;
        }
    }

    private BroomTrailParticle(ClientLevel level, double x, double y, double z,
                               double xd, double yd, double zd,
                               Style style, SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, xd, yd, zd, sprites.get(random));

        setColor(style.red, style.green, style.blue);
        setAlpha(0.85f);
        // Jittered so a stream of them does not pulse in lockstep, which reads as a strobe rather
        // than a trail.
        quadSize = style.size * (0.75f + random.nextFloat() * 0.5f);
        lifetime = style.baseLifetime + random.nextInt(style.lifetimeJitter + 1);
        friction = style.friction;
        gravity = style.gravity;
        hasPhysics = false;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        // Fade out over the back half of the life rather than vanishing mid-air. Cheap, and it is
        // the difference between a trail that dissolves and one that blinks off.
        float remaining = 1.0f - (age / (float) lifetime);
        setAlpha(0.85f * Math.min(1.0f, remaining * 2.0f));
    }

    /** A provider bound to one palette, for {@code RegisterParticleProvidersEvent}. */
    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites, Style style) {
        return (options, level, x, y, z, xSpeed, ySpeed, zSpeed, random) ->
                new BroomTrailParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, style, sprites, random);
    }

}
