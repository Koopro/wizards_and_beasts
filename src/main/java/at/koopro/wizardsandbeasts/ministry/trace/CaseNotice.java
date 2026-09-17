package at.koopro.wizardsandbeasts.ministry.trace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * A letter the Ministry has written and not yet delivered, because its recipient was offline when the
 * decision was made. Delivered at the next sweep they are online for; nothing is lost to a logout.
 *
 * @param kind    the notice, read as the {@code ministry.wizards_and_beasts.case.<kind>} lang keys
 * @param spellId the spell it concerns, or empty
 * @param ticks   a duration the letter quotes, or 0
 */
@NullMarked
public record CaseNotice(String kind, String spellId, long ticks) {

    public static final Codec<CaseNotice> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("kind").forGetter(CaseNotice::kind),
            Codec.STRING.optionalFieldOf("spell", "").forGetter(CaseNotice::spellId),
            Codec.LONG.optionalFieldOf("ticks", 0L).forGetter(CaseNotice::ticks)
    ).apply(instance, CaseNotice::new));
}
