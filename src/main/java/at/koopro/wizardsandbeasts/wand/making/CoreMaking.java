package at.koopro.wizardsandbeasts.wand.making;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

/**
 * What a core brings to making a wand.
 *
 * <p>Authored as the {@code making} block of a {@code wand_cores} definition. Absent means {@link #DEFAULT}, so a
 * datapack core pairs with every wood, as the shipped cores do.
 *
 * @param difficulty  how hard the core is to marry to a wood; added to the wood's own
 * @param lengthShift how much longer than the wood's own range a wand with this core is turned
 * @param affinity    the woods this core suits. Ollivander's best wands are cores "expertly enclosed in specially
 *                    selected and complementary wandwoods". A positive value calms the marrying and a negative one
 *                    makes it harder. A pair not named is neutral (0). No pairing is forbidden: Ollivander's core notes
 *                    call none impossible.
 */
@NullMarked
public record CoreMaking(float difficulty, float lengthShift, Map<Identifier, Integer> affinity, Pulse pulse) {

    public static final CoreMaking DEFAULT = new CoreMaking(1.0f, 0.5f, Map.of());

    public static final Codec<CoreMaking> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("difficulty").forGetter(CoreMaking::difficulty),
            Codec.FLOAT.optionalFieldOf("length_shift", 0.0f).forGetter(CoreMaking::lengthShift),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("affinity", Map.of())
                    .forGetter(CoreMaking::affinity),
            Pulse.CODEC.optionalFieldOf("pulse", Pulse.DEFAULT).forGetter(CoreMaking::pulse)
    ).apply(i, CoreMaking::new));

    /** A core with the {@link Pulse#DEFAULT} rhythm. */
    public CoreMaking(float difficulty, float lengthShift, Map<Identifier, Integer> affinity) {
        this(difficulty, lengthShift, affinity, Pulse.DEFAULT);
    }

    /**
     * The rhythm a core beats with while it is married to the wood, played as the marrying minigame
     * ({@link BenchCraft.Act#MARRY}): press on each pulse. Ollivander's core notes give the canon three:
     * unicorn hair "the most consistent" (slow and steady), dragon heartstring "somewhat temperamental" and prone
     * to accidents (flares: high jitter), phoenix feather "the hardest to tame" (fast, its tempo shifting: high
     * drift).
     *
     * @param tempo  ticks between pulses
     * @param jitter 0 to 1: how far a single pulse may come early or late
     * @param drift  0 to 1: how far the tempo wanders over the act
     */
    public record Pulse(int tempo, float jitter, float drift) {
        public static final Pulse DEFAULT = new Pulse(14, 0.3f, 0.2f);

        public static final Codec<Pulse> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(6, 40).fieldOf("tempo").forGetter(Pulse::tempo),
                Codec.floatRange(0f, 1f).optionalFieldOf("jitter", 0f).forGetter(Pulse::jitter),
                Codec.floatRange(0f, 1f).optionalFieldOf("drift", 0f).forGetter(Pulse::drift)
        ).apply(i, Pulse::new));

        /** Complementary woods calm a core: each point of affinity takes 30% off its jitter and drift. */
        public Pulse calmedBy(int affinity) {
            float k = Math.max(0f, Math.min(2f, 1f - 0.3f * affinity));
            return new Pulse(tempo, Math.min(1f, jitter * k), Math.min(1f, drift * k));
        }
    }

    public int affinityWith(Identifier wood) {
        return affinity.getOrDefault(wood, 0);
    }
}
