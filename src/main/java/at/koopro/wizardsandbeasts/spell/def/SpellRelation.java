package at.koopro.wizardsandbeasts.spell.def;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * How a spell relates to the one it descends from.
 *
 * <p><b>This gates nothing.</b> It is presentation metadata, so a spell list can render family
 * structure — "Lumos Maxima is the stronger Lumos" — instead of a flat alphabetical wall. Nothing in
 * the cast pipeline, the learning gates or Gamp's Law reads it, and nothing should start.
 *
 * <p>The rank gate is {@link SpellDefinition.SpellRequirementDef}, which already carries
 * {@code prerequisiteId} + {@code minProficiency} and predates this enum. A {@code RANK_OF} spell
 * expresses its gate through an ordinary requirement block exactly like any other spell; a branch on
 * {@code relation} inside the cast path would be a second, silent gating mechanism disagreeing with
 * the first.
 *
 * @see SpellDefinition#relation()
 * @see SpellDefinition#relatedSpell()
 */
public enum SpellRelation implements StringRepresentable {
    /** Stands alone. The default, and what every spell that omits the field means. */
    BASE("base"),
    /** Same effect, greater magnitude. Gated on the parent at a proficiency threshold. */
    RANK_OF("rank_of"),
    /** Same effect, restricted target class. Learned independently of the parent. */
    NARROW_OF("narrow_of"),
    /** Ends or reverses the spell it names. */
    COUNTER_OF("counter_of");

    public static final Codec<SpellRelation> CODEC = StringRepresentable.fromEnum(SpellRelation::values);

    private final String serializedName;

    SpellRelation(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public @NonNull String getSerializedName() {
        return serializedName;
    }
}
