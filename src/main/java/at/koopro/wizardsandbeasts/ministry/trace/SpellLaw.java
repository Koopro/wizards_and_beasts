package at.koopro.wizardsandbeasts.ministry.trace;

import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * A spell's standing in law, authored in {@code data/<namespace>/spell_law/<spell>.json}.
 *
 * <p>{@code visibility} is how much of it a bystander can see, 0–3: a Confundus Charm shows nothing, Lumos a
 * light, Stupefy a red bolt, Bombarda a blast. It decides how far a Muggle can be and still notice, and whether
 * what they saw is a breach of secrecy at all.
 *
 * <p>{@code offence} names the crime a conviction files, for the classes that are crimes in themselves.
 *
 * @param legalClass what the law thinks of the spell itself
 * @param visibility 0 (unseen) to 3 (spectacular)
 * @param offence    the offence a conviction for this spell files, if the spell is a crime in itself
 */
@NullMarked
public record SpellLaw(LegalClass legalClass, int visibility, Optional<MagicalOffence> offence) {

    public static final int MAX_VISIBILITY = 3;

    public static final Codec<SpellLaw> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LegalClass.CODEC.fieldOf("legal_class").forGetter(SpellLaw::legalClass),
            Codec.intRange(0, MAX_VISIBILITY).fieldOf("visibility").forGetter(SpellLaw::visibility),
            MagicalOffence.CODEC.optionalFieldOf("offence").forGetter(SpellLaw::offence)
    ).apply(instance, SpellLaw::new));

    public SpellLaw {
        visibility = Math.max(0, Math.min(MAX_VISIBILITY, visibility));
    }

    /**
     * The reading for a spell nobody authored a file for, from its category. Conservative on purpose: an
     * unauthored combat spell is restricted and visible, never Unforgivable, because a crime has to be named.
     */
    public static SpellLaw defaultFor(@Nullable SpellCategory category) {
        if (category == null) {
            return new SpellLaw(LegalClass.UNRESTRICTED, 1, Optional.empty());
        }
        return switch (category) {
            case UTILITY -> new SpellLaw(LegalClass.UNRESTRICTED, 1, Optional.empty());
            case DEFENSE -> new SpellLaw(LegalClass.UNRESTRICTED, 2, Optional.empty());
            case COMBAT -> new SpellLaw(LegalClass.RESTRICTED, 2, Optional.empty());
            case DARK_ARTS -> new SpellLaw(LegalClass.DARK, 2, Optional.empty());
        };
    }
}
