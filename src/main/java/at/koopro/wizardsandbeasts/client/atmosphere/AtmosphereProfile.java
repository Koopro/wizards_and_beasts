package at.koopro.wizardsandbeasts.client.atmosphere;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One kind of place and the air it has: how to recognise it from the blocks round the camera, and what it shows and
 * sounds like once recognised. Loaded from {@code assets/<ns>/atmosphere/<id>.json} on the resource reload cycle, so
 * a resource pack can retune or add places without code.
 *
 * <p>Recognition is by sampling ({@link PlaceSense}): each tick a few random blocks near the camera are tested against
 * {@link #blocks} rules, the weighted share of hits plus {@link #biomeWeight} (when the camera stands in
 * {@link #biome}) is one frame (with {@link #biomeRequired}, nothing counts outside the biome), and {@link PlaceScores} turns the frames into an eased strength with hysteresis
 * ({@link #enter}, {@link #leave}, {@link #full}). {@link #dimension} restricts a place to one dimension (a pocket
 * like Diagon Alley is recognised by its key alone); {@link #when} gates the whole place by time, weather and sky.
 */
@NullMarked
public record AtmosphereProfile(
        Conditions when,
        Optional<ResourceKey<Level>> dimension,
        List<BlockRule> blocks,
        Optional<TagKey<Biome>> biome,
        double biomeWeight,
        boolean biomeRequired,
        double enter,
        double leave,
        double full,
        List<Emitter> emitters,
        Optional<Fog> fog,
        List<SoundCue> sounds,
        Optional<Bed> bed) {

    /** A colour written {@code "#RRGGBB"}, read as opaque ARGB. Declared before CODEC: the nested codecs read it. */
    static final Codec<Integer> COLOUR = Codec.STRING.comapFlatMap(s -> {
        String hex = s.startsWith("#") ? s.substring(1) : s;
        if (hex.length() != 6) {
            return DataResult.error(() -> "colour must be #RRGGBB: " + s);
        }
        try {
            return DataResult.success(0xFF000000 | Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "colour must be #RRGGBB: " + s);
        }
    }, c -> String.format(Locale.ROOT, "#%06X", c & 0xFFFFFF));

    public static final Codec<AtmosphereProfile> CODEC = RecordCodecBuilder.<AtmosphereProfile>create(i -> i.group(
            Conditions.CODEC.optionalFieldOf("when", Conditions.ANY).forGetter(AtmosphereProfile::when),
            ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("dimension").forGetter(AtmosphereProfile::dimension),
            BlockRule.CODEC.listOf().optionalFieldOf("blocks", List.of()).forGetter(AtmosphereProfile::blocks),
            TagKey.hashedCodec(Registries.BIOME).optionalFieldOf("biome").forGetter(AtmosphereProfile::biome),
            Codec.DOUBLE.optionalFieldOf("biome_weight", 0.0).forGetter(AtmosphereProfile::biomeWeight),
            Codec.BOOL.optionalFieldOf("biome_required", false).forGetter(AtmosphereProfile::biomeRequired),
            Codec.DOUBLE.optionalFieldOf("enter", 0.08).forGetter(AtmosphereProfile::enter),
            Codec.DOUBLE.optionalFieldOf("leave", 0.04).forGetter(AtmosphereProfile::leave),
            Codec.DOUBLE.optionalFieldOf("full", 0.2).forGetter(AtmosphereProfile::full),
            Emitter.CODEC.listOf().optionalFieldOf("emitters", List.of()).forGetter(AtmosphereProfile::emitters),
            Fog.CODEC.optionalFieldOf("fog").forGetter(AtmosphereProfile::fog),
            SoundCue.CODEC.listOf().optionalFieldOf("sounds", List.of()).forGetter(AtmosphereProfile::sounds),
            Bed.CODEC.optionalFieldOf("bed").forGetter(AtmosphereProfile::bed)
    ).apply(i, AtmosphereProfile::new)).validate(AtmosphereProfile::validate);

    private static DataResult<AtmosphereProfile> validate(AtmosphereProfile p) {
        if (!(p.leave >= 0 && p.leave < p.enter && p.enter <= p.full)) {
            return DataResult.error(() -> "need 0 <= leave < enter <= full");
        }
        if (p.blocks.isEmpty() && p.biome.isEmpty() && p.dimension.isEmpty()) {
            return DataResult.error(() -> "a place needs blocks, a biome or a dimension to be recognised by");
        }
        return DataResult.success(p);
    }

    /** A [min, max] pair. */
    public record Range(float min, float max) {
        static final Codec<Range> CODEC = Codec.FLOAT.listOf(2, 2).comapFlatMap(
                l -> l.get(0) <= l.get(1) ? DataResult.success(new Range(l.get(0), l.get(1)))
                        : DataResult.error(() -> "range min > max: " + l),
                r -> List.of(r.min, r.max));

        public float pick(net.minecraft.util.RandomSource random) {
            return min + (max - min) * random.nextFloat();
        }
    }

    /** An x, y, z triple in blocks. */
    public record Triple(float x, float y, float z) {
        static final Codec<Triple> CODEC = Codec.FLOAT.listOf(3, 3).xmap(
                l -> new Triple(l.get(0), l.get(1), l.get(2)), t -> List.of(t.x, t.y, t.z));
    }

    // ---- conditions ----------------------------------------------------------------------------------------------

    public enum Time implements StringRepresentable {
        ANY, DAY, NIGHT;
        static final Codec<Time> CODEC = StringRepresentable.fromEnum(Time::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Weather implements StringRepresentable {
        ANY, CLEAR, RAIN, THUNDER;
        static final Codec<Weather> CODEC = StringRepresentable.fromEnum(Weather::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Sky implements StringRepresentable {
        ANY, OPEN, COVERED;
        static final Codec<Sky> CODEC = StringRepresentable.fromEnum(Sky::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** When something applies: time of day, weather, whether the camera is under open sky. */
    public record Conditions(Time time, Weather weather, Sky sky) {
        public static final Conditions ANY = new Conditions(Time.ANY, Weather.ANY, Sky.ANY);
        static final Codec<Conditions> CODEC = RecordCodecBuilder.create(i -> i.group(
                Time.CODEC.optionalFieldOf("time", Time.ANY).forGetter(Conditions::time),
                Weather.CODEC.optionalFieldOf("weather", Weather.ANY).forGetter(Conditions::weather),
                Sky.CODEC.optionalFieldOf("sky", Sky.ANY).forGetter(Conditions::sky)
        ).apply(i, Conditions::new));

        public boolean test(Ambience a) {
            boolean timeOk = switch (time) {
                case ANY -> true;
                case DAY -> !a.night();
                case NIGHT -> a.night();
            };
            boolean weatherOk = switch (weather) {
                case ANY -> true;
                case CLEAR -> !a.raining();
                case RAIN -> a.raining();
                case THUNDER -> a.thundering();
            };
            boolean skyOk = switch (sky) {
                case ANY -> true;
                case OPEN -> a.skyOpen();
                case COVERED -> !a.skyOpen();
            };
            return timeOk && weatherOk && skyOk;
        }
    }

    /** The state of the world round the camera this tick, as conditions see it. */
    public record Ambience(boolean night, boolean raining, boolean thundering, boolean skyOpen) {
    }

    // ---- recognition ---------------------------------------------------------------------------------------------

    /** Blocks in this tag count toward the place, each sampled hit by {@code weight}. */
    public record BlockRule(TagKey<Block> tag, double weight) {
        static final Codec<BlockRule> CODEC = RecordCodecBuilder.create(i -> i.group(
                TagKey.hashedCodec(Registries.BLOCK).fieldOf("tag").forGetter(BlockRule::tag),
                Codec.DOUBLE.optionalFieldOf("weight", 1.0).forGetter(BlockRule::weight)
        ).apply(i, BlockRule::new));
    }

    // ---- what it shows -------------------------------------------------------------------------------------------

    /** Where an emitter puts its particles. */
    public enum Where implements StringRepresentable {
        /** At a block the sampler recently matched, plus {@code offset}, scattered by {@code spread}. */
        ANCHOR,
        /** In open air within reach, where the light is between {@code min_light} and {@code max_light}. */
        AIR,
        /** Over the ground (leaves ignored) within reach, {@code height} above it. */
        GROUND;
        static final Codec<Where> CODEC = StringRepresentable.fromEnum(Where::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A source of particles. {@code rate} is particles per tick at full strength and FULL atmosphere (0.25 = one every
     * four ticks); fractions accumulate. {@code particle} must be one of the mod's tinted particle types
     * ({@link AtmosphereParticles}); vanilla's keep their vanilla meaning.
     */
    public record Emitter(Identifier particle, int colour, Where where, float rate, int reach, int minLight,
                          int maxLight, Triple offset, Triple spread, Range height, Conditions when) {
        static final Codec<Emitter> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("particle").forGetter(Emitter::particle),
                COLOUR.optionalFieldOf("colour", 0xFFFFFFFF).forGetter(Emitter::colour),
                Where.CODEC.fieldOf("where").forGetter(Emitter::where),
                Codec.floatRange(0f, 8f).fieldOf("rate").forGetter(Emitter::rate),
                Codec.intRange(1, 24).optionalFieldOf("reach", 10).forGetter(Emitter::reach),
                Codec.intRange(0, 15).optionalFieldOf("min_light", 0).forGetter(Emitter::minLight),
                Codec.intRange(0, 15).optionalFieldOf("max_light", 15).forGetter(Emitter::maxLight),
                Triple.CODEC.optionalFieldOf("offset", new Triple(0.5f, 1.0f, 0.5f)).forGetter(Emitter::offset),
                Triple.CODEC.optionalFieldOf("spread", new Triple(0.4f, 0.3f, 0.4f)).forGetter(Emitter::spread),
                Range.CODEC.optionalFieldOf("height", new Range(0.2f, 1.8f)).forGetter(Emitter::height),
                Conditions.CODEC.optionalFieldOf("when", Conditions.ANY).forGetter(Emitter::when)
        ).apply(i, Emitter::new));
    }

    /**
     * The fog drawn toward a colour by {@code tint} (0..1) and its far plane pulled in to {@code far} of normal, both
     * at full strength. Kept small on purpose: the world should still be seen.
     */
    public record Fog(int colour, float tint, float far, Conditions when) {
        static final Codec<Fog> CODEC = RecordCodecBuilder.create(i -> i.group(
                COLOUR.fieldOf("colour").forGetter(Fog::colour),
                Codec.floatRange(0f, 0.6f).optionalFieldOf("tint", 0.15f).forGetter(Fog::tint),
                Codec.floatRange(0.4f, 1f).optionalFieldOf("far", 1f).forGetter(Fog::far),
                Conditions.CODEC.optionalFieldOf("when", Conditions.ANY).forGetter(Fog::when)
        ).apply(i, Fog::new));
    }

    /** Where a one-shot sound plays from. */
    public enum SoundAt implements StringRepresentable {
        /** At a block the sampler recently matched: the shelf, the cauldron. */
        ANCHOR,
        /** Somewhere round the listener, 4 to 10 blocks off. */
        AROUND;
        static final Codec<SoundAt> CODEC = StringRepresentable.fromEnum(SoundAt::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** A sparse one-shot: one play every {@code interval} seconds (picked anew each time), quietly. */
    public record SoundCue(Identifier sound, SoundAt at, Range interval, float volume, Range pitch, Conditions when) {
        static final Codec<SoundCue> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("sound").forGetter(SoundCue::sound),
                SoundAt.CODEC.optionalFieldOf("at", SoundAt.AROUND).forGetter(SoundCue::at),
                Range.CODEC.fieldOf("interval").forGetter(SoundCue::interval),
                Codec.floatRange(0f, 1f).optionalFieldOf("volume", 0.3f).forGetter(SoundCue::volume),
                Range.CODEC.optionalFieldOf("pitch", new Range(0.95f, 1.05f)).forGetter(SoundCue::pitch),
                Conditions.CODEC.optionalFieldOf("when", Conditions.ANY).forGetter(SoundCue::when)
        ).apply(i, SoundCue::new));
    }

    /** A room tone: a quiet loop that fades with the place's strength. Capped low so it is felt more than heard. */
    public record Bed(Identifier sound, float volume, Conditions when) {
        static final Codec<Bed> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("sound").forGetter(Bed::sound),
                Codec.floatRange(0f, 0.25f).optionalFieldOf("volume", 0.12f).forGetter(Bed::volume),
                Conditions.CODEC.optionalFieldOf("when", Conditions.ANY).forGetter(Bed::when)
        ).apply(i, Bed::new));
    }
}
