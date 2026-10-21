package at.koopro.wizardsandbeasts.bestiary.journal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.Optional;

/**
 * A bestiary entry's optional {@code journal} block: art that does not sit at the conventional path, and the caption
 * under its signature illustration.
 *
 * <pre>{@code
 * "journal": {
 *   "art": { "signature": "wizards_and_beasts:textures/gui/bestiary/journal/signature/mooncalf_dance.png" },
 *   "signature_caption": "bestiary.wizards_and_beasts.mooncalf.signature_caption"
 * }
 * }</pre>
 *
 * Neither is needed: a plate is found at {@link IllustrationStage#conventionalTexture} when it exists, and the
 * journal falls back stage by stage to whatever art is there (documentation/BESTIARY_ILLUSTRATION_SPEC.md).
 *
 * @param art              stage → texture, overriding the conventional path
 * @param signatureCaption lang key for the line under the signature illustration
 */
@NullMarked
public record JournalArt(Map<IllustrationStage, Identifier> art, Optional<String> signatureCaption) {

    public static final JournalArt NONE = new JournalArt(Map.of(), Optional.empty());

    public static final Codec<JournalArt> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(IllustrationStage.CODEC, Identifier.CODEC).optionalFieldOf("art", Map.of())
                    .forGetter(JournalArt::art),
            Codec.STRING.optionalFieldOf("signature_caption").forGetter(JournalArt::signatureCaption)
    ).apply(instance, JournalArt::new));

    /** The texture for a stage: the override, else the convention. Whether it exists is the client's question. */
    public Identifier textureFor(IllustrationStage stage, Identifier entryId) {
        return art.getOrDefault(stage, stage.conventionalTexture(entryId));
    }
}
