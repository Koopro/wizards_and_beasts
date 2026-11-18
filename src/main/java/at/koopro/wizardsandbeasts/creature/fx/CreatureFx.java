package at.koopro.wizardsandbeasts.creature.fx;

import at.koopro.wizardsandbeasts.creature.profile.CreatureActivity;
import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A species' own visual effects, declared in its definition JSON and drawn on the client only:
 *
 * <pre>{@code "fx": {"footfall": 1.0, "emitters": [
 *     {"particle": "wizards_and_beasts:smoke_puff", "color": "#8A8A8A", "rate": 0.08, "where": "head", "when": "resting"}]}}</pre>
 *
 * <ul>
 *   <li>{@code footfall}: how heavily it walks. Above zero, every stride kicks up a little of the block under it
 *   (vanilla's block-crumb particle), more for a heavier and wider creature. Dragons, giants, the Erumpent.</li>
 *   <li>{@code emitters}: a particle, an optional colour (for the mod's tinted particles), a chance per tick, where
 *   on the body it rises ({@code body}, {@code head}, {@code back}, {@code feet}) and when ({@code always},
 *   {@code moving}, {@code still}, {@code resting} = resting or asleep, {@code sleeping}, {@code threaten}), or
 *   {@code appear}: a burst of a dozen, once, the first time the client sees the creature (a fairy arriving in a
 *   sparkle, a Boggart out of the dark).</li>
 * </ul>
 *
 * <p>Cosmetic only and client side: {@link #tick} is called from the entity's own tick on the client, and
 * {@code ClientLevel.addParticle} already thins it by distance and by the player's particle setting.
 */
@NullMarked
public record CreatureFx(float footfall, List<Emitter> emitters) {

    public static final CreatureFx NONE = new CreatureFx(0f, List.of());

    public record Emitter(Identifier particle, String color, float rate, String where, String when) {
        static final Codec<Emitter> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("particle").forGetter(Emitter::particle),
                Codec.STRING.optionalFieldOf("color", "#FFFFFF").forGetter(Emitter::color),
                Codec.FLOAT.optionalFieldOf("rate", 0.05f).forGetter(Emitter::rate),
                Codec.STRING.optionalFieldOf("where", "body").forGetter(Emitter::where),
                Codec.STRING.optionalFieldOf("when", "always").forGetter(Emitter::when)
        ).apply(i, Emitter::new));
    }

    public static final Codec<CreatureFx> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("footfall", 0f).forGetter(CreatureFx::footfall),
            Emitter.CODEC.listOf().optionalFieldOf("emitters", List.of()).forGetter(CreatureFx::emitters)
    ).apply(i, CreatureFx::new));

    public boolean isEmpty() {
        return footfall <= 0f && emitters.isEmpty();
    }

    // ── drawing ─────────────────────────────────────────────────────────────

    private static final Map<String, ParticleOptions> OPTIONS = new ConcurrentHashMap<>();

    /** Whether an emitter's {@code when} holds for a creature doing {@code activity}, moving or not. */
    public static boolean applies(String when, CreatureActivity activity, boolean moving) {
        return switch (when.toLowerCase(Locale.ROOT)) {
            case "moving" -> moving;
            case "still" -> !moving;
            case "resting" -> activity == CreatureActivity.REST || activity == CreatureActivity.SLEEP;
            case "sleeping" -> activity == CreatureActivity.SLEEP;
            case "threaten" -> activity == CreatureActivity.THREATEN;
            case "appear" -> false;      // a one-off burst on arrival, never a per-tick chance (see tick)
            default -> true;
        };
    }

    /**
     * Steps between footfalls for a creature this wide moving this fast (blocks per tick): one stride is about its
     * width and a bit, so a dragon's steps come slow and a galloping horse's quick. Never under 5 or over 24 ticks.
     */
    public static int strideTicks(float width, double speed) {
        if (speed <= 1.0e-4) {
            return Integer.MAX_VALUE;
        }
        return Mth.clamp((int) Math.round(width * 1.2 / speed), 5, 24);
    }

    /** One client tick of effects. {@code strideClock} is the creature's own counter, returned advanced. */
    public int tick(Mob mob, CreatureActivity activity, int strideClock) {
        Level level = mob.level();
        RandomSource random = mob.getRandom();
        double dx = mob.getX() - mob.xo;
        double dz = mob.getZ() - mob.zo;
        double speed = Math.sqrt(dx * dx + dz * dz);
        boolean moving = speed > 0.01;
        if (footfall > 0f && moving && mob.onGround()) {
            if (++strideClock >= strideTicks(mob.getBbWidth(), speed)) {
                strideClock = 0;
                footfall(mob, level, random);
            }
        } else {
            strideClock = 0;
        }
        if (mob.tickCount == 2) {
            for (Emitter e : emitters) {
                ParticleOptions options = e.when().equals("appear") ? options(e) : null;
                for (int i = 0; options != null && i < 12; i++) {
                    Vec3 at = place(mob, "body", random);
                    level.addParticle(options, at.x, at.y, at.z, (random.nextDouble() - 0.5) * 0.12,
                            random.nextDouble() * 0.08, (random.nextDouble() - 0.5) * 0.12);
                }
            }
        }
        for (Emitter e : emitters) {
            if (random.nextFloat() >= e.rate() || !applies(e.when(), activity, moving)) {
                continue;
            }
            ParticleOptions options = options(e);
            if (options == null) {
                continue;
            }
            Vec3 at = place(mob, e.where(), random);
            double up = e.where().equals("feet") ? 0.0 : 0.02 + random.nextDouble() * 0.02;
            level.addParticle(options, at.x, at.y, at.z, (random.nextDouble() - 0.5) * 0.02, up,
                    (random.nextDouble() - 0.5) * 0.02);
        }
        return strideClock;
    }

    @SuppressWarnings("unchecked")
    private void footfall(Mob mob, Level level, RandomSource random) {
        BlockPos below = mob.blockPosition().below();
        BlockState state = level.getBlockState(below);
        if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) {
            return;
        }
        BlockParticleOption crumb = new BlockParticleOption(ParticleTypes.BLOCK, state);
        float w = mob.getBbWidth();
        int count = Math.max(2, Math.round(footfall * (2 + w * 3)));
        float yaw = mob.getYRot() * Mth.DEG_TO_RAD;
        // the foot that lands: alternate sides, fore or hind at random
        double side = (random.nextBoolean() ? 1 : -1) * w * 0.35;
        double fore = (random.nextBoolean() ? 1 : -1) * w * 0.4;
        double fx = mob.getX() + Mth.cos(yaw) * side - Mth.sin(yaw) * fore;
        double fz = mob.getZ() + Mth.sin(yaw) * side + Mth.cos(yaw) * fore;
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = random.nextDouble() * w * 0.2;
            level.addParticle(crumb, fx + Math.cos(a) * r, mob.getY() + 0.05, fz + Math.sin(a) * r,
                    Math.cos(a) * 0.08, 0.06 + random.nextDouble() * 0.08 * footfall, Math.sin(a) * 0.08);
        }
        // and a low puff of dust in the ground's own colour, lightened: crumbs of stone vanish against stone, a
        // puff still reads (and is what a heavy foot really raises on dry ground)
        int ground = state.getMapColor(level, below).col;
        int dust = 0xC0000000 | lighten(ground);
        SpellTintParticleOptions puff = new SpellTintParticleOptions(
                (ParticleType<SpellTintParticleOptions>) (ParticleType<?>) at.koopro.wizardsandbeasts.registry.ModParticles.SMOKE_PUFF.get(), dust);
        int puffs = Math.max(1, Math.round(footfall * w * 1.5f));
        for (int i = 0; i < puffs; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            level.addParticle(puff, fx + Math.cos(a) * w * 0.25, mob.getY() + 0.1, fz + Math.sin(a) * w * 0.25,
                    Math.cos(a) * 0.03, 0.01, Math.sin(a) * 0.03);
        }
    }

    private static Vec3 place(Mob mob, String where, RandomSource random) {
        float w = mob.getBbWidth();
        float h = mob.getBbHeight();
        double jx = (random.nextDouble() - 0.5) * w * 0.6;
        double jz = (random.nextDouble() - 0.5) * w * 0.6;
        return switch (where.toLowerCase(Locale.ROOT)) {
            case "head" -> {
                Vec3 look = Vec3.directionFromRotation(0, mob.getYHeadRot());
                yield new Vec3(mob.getX() + look.x * w * 0.6, mob.getY() + h * 0.78, mob.getZ() + look.z * w * 0.6);
            }
            case "back" -> new Vec3(mob.getX() + jx * 0.6, mob.getY() + h * 0.95, mob.getZ() + jz * 0.6);
            case "feet" -> new Vec3(mob.getX() + jx, mob.getY() + 0.1, mob.getZ() + jz);
            default -> new Vec3(mob.getX() + jx, mob.getY() + h * (0.2 + random.nextDouble() * 0.6), mob.getZ() + jz);
        };
    }

    @SuppressWarnings("unchecked")
    private static @Nullable ParticleOptions options(Emitter e) {
        String key = e.particle() + "|" + e.color();
        ParticleOptions cached = OPTIONS.get(key);
        if (cached != null) {
            return cached;
        }
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getValue(e.particle());
        ParticleOptions made;
        if (type instanceof SimpleParticleType simple) {
            made = simple;
        } else if (type != null && "wizards_and_beasts".equals(e.particle().getNamespace())) {
            made = new SpellTintParticleOptions((ParticleType<SpellTintParticleOptions>) type, argb(e.color()));
        } else {
            return null;     // a particle that needs options this cannot build: drawn nowhere, not a crash
        }
        OPTIONS.put(key, made);
        return made;
    }

    /** Halfway to white: dust is paler than the ground it rises from. */
    static int lighten(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return ((r + 255) / 2 << 16) | ((g + 255) / 2 << 8) | ((b + 255) / 2);
    }

    static int argb(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        int rgb = (int) Long.parseLong(h, 16);
        return h.length() > 6 ? rgb : 0xFF000000 | rgb;
    }
}
