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
public record CoreMaking(float difficulty, float lengthShift, Map<Identifier, Integer> affinity) {

    public static final CoreMaking DEFAULT = new CoreMaking(1.0f, 0.5f, Map.of());

    public static final Codec<CoreMaking> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("difficulty").forGetter(CoreMaking::difficulty),
            Codec.FLOAT.optionalFieldOf("length_shift", 0.0f).forGetter(CoreMaking::lengthShift),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("affinity", Map.of())
                    .forGetter(CoreMaking::affinity)
    ).apply(i, CoreMaking::new));

    public int affinityWith(Identifier wood) {
        return affinity.getOrDefault(wood, 0);
    }
}
