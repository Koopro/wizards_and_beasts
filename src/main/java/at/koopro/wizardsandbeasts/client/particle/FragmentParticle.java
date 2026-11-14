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
        FLASH(at.koopro.wizardsandbeasts.spell.core.MagicColours.DEATH, 0.22f, 5, 0.0f),
        /** Where any spell lands: a larger flash in the spell's own colour (the options carry it). */
        IMPACT(0xFFFFFFFF, 0.38f, 6, 0.0f),
        /** A cast leaving the wand: the impact flash at wand-tip size. */
        RELEASE(0xFFFFFFFF, 0.14f, 4, 0.0f),
        /** A firefly: tiny, its own light, two to three seconds of slow drifting and pulsing. */
        FIREFLY(0xFFFFFFFF, 0.14f, 50, 0.0f),
        /** A bog wisp: large and soft, six to nine seconds of hanging, swaying and a slow breath of light. */
        WISP(0xFFFFFFFF, 0.5f, 120, 0.0f),
        /** A pollen mote: a speck drifting slowly down, steady, gone in four to six seconds. */
        MOTE(0xFFFFFFFF, 0.1f, 80, 0.0f),
        /** Light catching gold: a still four-point star that swells and shrinks in half a second. */
        GLINT(0xFFFFFFFF, 0.2f, 9, 0.0f);

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
    /** A firefly's place in its pulse, so a swarm does not blink in step. */
    private final float phase;

    private FragmentParticle(ClientLevel level, double x, double y, double z,
                             double xd, double yd, double zd, Style style, int argb, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd, sprites.get(level.getRandom()));
        this.style = style;
        this.phase = level.getRandom().nextFloat() * 25f;
        int c = argb;
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
        if (style == Style.FIREFLY) {
            // Wander a little, and pulse: on, dim, on, never a steady dot.
            xd += (random.nextDouble() - 0.5) * 0.004;
            yd += (random.nextDouble() - 0.5) * 0.003;
            zd += (random.nextDouble() - 0.5) * 0.004;
            float pulse = 0.5f + 0.5f * Mth.sin((age + phase) * 0.25f);
            float fade = Math.min(1f, Math.min(age / 6f, (lifetime - age) / 8f));
            setAlpha(Math.max(0.05f, (0.25f + 0.75f * pulse) * fade));
            return;
        }
        if (style == Style.WISP) {
            // Hang and sway; brighten and dim like a slow breath; fade in and out over a second.
            xd += Mth.sin((age + phase) * 0.05f) * 0.0006;
            zd += Mth.cos((age + phase) * 0.043f) * 0.0006;
            yd += (random.nextDouble() - 0.5) * 0.0008;
            float breath = 0.5f + 0.5f * Mth.sin((age + phase) * 0.07f);
            float fade = Math.min(1f, Math.min(age / 20f, (lifetime - age) / 25f));
            setAlpha(Math.max(0.03f, (0.45f + 0.5f * breath) * fade));
            return;
        }
        if (style == Style.MOTE) {
            // Drift: a little sideways wander over a slow fall; a glint now and then as it turns in the sun.
            xd += (random.nextDouble() - 0.5) * 0.002;
            zd += (random.nextDouble() - 0.5) * 0.002;
            yd = Math.max(-0.006, yd - 0.0004);
            float glint = Mth.sin((age + phase) * 0.18f) > 0.85f ? 1f : 0.7f;
            float fade = Math.min(1f, Math.min(age / 10f, (lifetime - age) / 15f));
            setAlpha(glint * fade);
            return;
        }
        if (style == Style.GLINT) {
            // Held still and upright; the size does the work (getQuadSize).
            xd = yd = zd = 0;
            setAlpha(1.0f);
            return;
        }
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
        if (style == Style.FIREFLY || style == Style.WISP || style == Style.MOTE) {
            return baseSize;
        }
        if (style == Style.GLINT) {
            // Swell to full and shrink back: a twinkle, not a flash. Stepped to quarters so it stays pixel-crisp.
            float t = Mth.clamp((age + partialTick) / lifetime, 0.0f, 1.0f);
            float s = Mth.sin(t * Mth.PI);
            return baseSize * (Math.round(s * 4f) / 4f);
        }
        if (style != Style.SHATTER) {
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
                new FragmentParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, style, style.argb, sprites);
    }

    /** A firefly, coloured by its options, set drifting slowly. */
    /** A bog wisp, tinted by the spawner; starts almost still. */
    public static ParticleProvider<at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions> wispProvider(
            SpriteSet sprites) {
        return (options, level, x, y, z, xSpeed, ySpeed, zSpeed, random) -> {
            FragmentParticle p = new FragmentParticle(level, x, y, z, 0, 0, 0, Style.WISP,
                    options.argb() | 0xFF000000, sprites);
            p.friction = 0.96f;
            p.xd = (random.nextDouble() - 0.5) * 0.006;
            p.yd = (random.nextDouble() - 0.5) * 0.004;
            p.zd = (random.nextDouble() - 0.5) * 0.006;
            return p;
        };
    }

    /** A pollen mote, tinted by the spawner; starts with a slight fall. */
    public static ParticleProvider<at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions> moteProvider(
            SpriteSet sprites) {
        return (options, level, x, y, z, xSpeed, ySpeed, zSpeed, random) -> {
            FragmentParticle p = new FragmentParticle(level, x, y, z, 0, 0, 0, Style.MOTE,
                    options.argb() | 0xFF000000, sprites);
            p.friction = 0.98f;
            p.xd = (random.nextDouble() - 0.5) * 0.01;
            p.yd = -0.002;
            p.zd = (random.nextDouble() - 0.5) * 0.01;
            return p;
        };
    }

    /** A glint on gold, tinted by the spawner; still and upright, so the star's arms stay square to the screen. */
    public static ParticleProvider<at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions> glintProvider(
            SpriteSet sprites) {
        return (options, level, x, y, z, xSpeed, ySpeed, zSpeed, random) -> {
            FragmentParticle p = new FragmentParticle(level, x, y, z, 0, 0, 0, Style.GLINT,
                    options.argb() | 0xFF000000, sprites);
            p.roll = 0f;
            p.oRoll = 0f;
            p.lifetime = Style.GLINT.life + random.nextInt(4);
            return p;
        };
    }

    public static ParticleProvider<at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions> fireflyProvider(
            SpriteSet sprites) {
        return (options, level, x, y, z, xSpeed, ySpeed, zSpeed, random) -> {
            FragmentParticle p = new FragmentParticle(level, x, y, z, 0, 0, 0, Style.FIREFLY,
                    options.argb() | 0xFF000000, sprites);
            p.xd = (random.nextDouble() - 0.5) * 0.02;
            p.yd = (random.nextDouble() - 0.4) * 0.01;
            p.zd = (random.nextDouble() - 0.5) * 0.02;
            return p;
        };
    }

    /**
     * The impact flash: colour from the options, so one type serves every spell. A non-zero {@code ySpeed} asks
     * for the small wand-tip release flash instead (the speed itself is not used; the flash does not move).
     */
    public static ParticleProvider<at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions> impactProvider(
            SpriteSet sprites) {
        return (options, level, x, y, z, xSpeed, ySpeed, zSpeed, random) ->
                new FragmentParticle(level, x, y, z, 0, 0, 0, ySpeed > 0 ? Style.RELEASE : Style.IMPACT,
                        options.argb() | 0xFF000000, sprites);
    }
}
