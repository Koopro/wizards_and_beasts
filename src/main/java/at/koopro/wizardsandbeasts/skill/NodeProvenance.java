package at.koopro.wizardsandbeasts.skill;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/**
 * Where a skill node's content comes from: attested canon, or an advancement this mod invented.
 *
 * <p><b>Why a node needs this at all.</b> A skill web that teaches <i>Lumos</i> and, three nodes later,
 * "Sustained Lumos" is mixing a spell every reader knows with a training step no book ever named. Presented
 * identically, the second one reads as canon the player simply had not heard of — which is the one thing a
 * lore-faithful mod must not do. So each node says which it is, the screen prints it, and
 * {@code SkillNodeProvenanceTest} refuses a node that claims an attestation it cannot have.
 *
 * <p>This is the same ruling {@code CreatureProfile}'s provenance and {@code heritage_appearance}'s
 * {@code Provenance} already make for creatures and bodies, and it uses the same vocabulary as
 * {@link at.koopro.wizardsandbeasts.spell.def.SpellCanonTier} for spells.
 *
 * <p>Exactly one arm is present. A node that teaches a canon spell may leave the field out entirely and
 * inherit the spell's own {@code canonTier}, which is why absence is legal and means "ask the spell".
 *
 * @param citation      where this is attested, e.g. {@code "Order of the Phoenix, ch. 18"}
 * @param modAdvancement true when this node is a training step of the mod's own devising
 */
@NullMarked
public record NodeProvenance(Optional<String> citation, boolean modAdvancement) {

    public static final Codec<NodeProvenance> CODEC = RecordCodecBuilder.<NodeProvenance>create(instance ->
            instance.group(
                    Codec.STRING.optionalFieldOf("citation").forGetter(NodeProvenance::citation),
                    Codec.BOOL.optionalFieldOf("modAdvancement", false).forGetter(NodeProvenance::modAdvancement)
            ).apply(instance, NodeProvenance::new)).validate(NodeProvenance::validate);

    /** An attested practice, with the source named. */
    public static NodeProvenance canon(String citation) {
        return new NodeProvenance(Optional.of(citation), false);
    }

    /** A training step the mod invented, honestly labelled as one. */
    public static NodeProvenance invented() {
        return new NodeProvenance(Optional.empty(), true);
    }

    /** True when this node is attested somewhere. */
    public boolean isCanon() {
        return citation.isPresent();
    }

    private static DataResult<NodeProvenance> validate(NodeProvenance provenance) {
        if (provenance.modAdvancement && provenance.citation.isPresent()) {
            return DataResult.error(() -> "a node is either attested or the mod's own invention, not both");
        }
        if (!provenance.modAdvancement && provenance.citation.isEmpty()) {
            return DataResult.error(() ->
                    "provenance declares neither a citation nor modAdvancement; leave the field out entirely to "
                            + "inherit the taught spell's canon tier");
        }
        if (provenance.citation.isPresent() && provenance.citation.get().isBlank()) {
            return DataResult.error(() -> "a citation must name a source");
        }
        return DataResult.success(provenance);
    }
}
