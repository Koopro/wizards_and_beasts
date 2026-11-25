package at.koopro.wizardsandbeasts.wand.making;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * What a wood brings to making a wand: how hard it is to work, and the lengths it is turned to.
 *
 * <p>Authored as the {@code making} block of a {@code wand_woods} definition. A wood without one still makes wands
 * with every core, because {@link #forRarity} stands in. A datapack that adds a wood never has to author a pairing.
 *
 * @param difficulty how hard the wood is to turn, bore and seal; added to the core's own (see {@link WandmakingRules})
 * @param lengthMin  shortest length the wood is turned to, in inches, before the core shifts it
 * @param lengthMax  longest length, in inches. Ollivander's notes: "most wands will be in the range of between nine
 *                   and fourteen inches"
 */
@NullMarked
public record WoodMaking(float difficulty, float lengthMin, float lengthMax) {

    public static final Codec<WoodMaking> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("difficulty").forGetter(WoodMaking::difficulty),
            Codec.FLOAT.fieldOf("length_min").forGetter(WoodMaking::lengthMin),
            Codec.FLOAT.fieldOf("length_max").forGetter(WoodMaking::lengthMax)
    ).apply(i, WoodMaking::new));

    /**
     * The stand-in for a wood with no {@code making} block, chosen by its {@code rarity}. The difficulties are taken
     * from the shipped woods and rounded: common 2.2 (willow 2.2, rowan 2.5), uncommon 2.6 (holly 2.0 to ash 3.0),
     * rare 3.6 (yew 3.2 to blackthorn 3.9), legendary 6.7 (elder).
     */
    public static WoodMaking forRarity(String rarity) {
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "common" -> new WoodMaking(2.2f, 9.0f, 12.5f);
            case "uncommon" -> new WoodMaking(2.6f, 9.5f, 13.0f);
            case "rare" -> new WoodMaking(3.6f, 10.0f, 13.5f);
            case "legendary" -> new WoodMaking(6.7f, 12.0f, 14.0f);
            default -> new WoodMaking(3.0f, 9.5f, 13.0f);
        };
    }
}
