package at.koopro.wizardsandbeasts.standing.deed;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

/**
 * The moments at which a deed can be counted.
 *
 * <p>A closed set on purpose. Every value here is a seam that already existed and is already used by
 * another system, so adding standing to the mod added no new event plumbing and no new tick work:
 * the spell path already reported the Trace and charged corruption from the same line, the Ministry
 * already funnelled every crime through one method, and the Bestiary already fired an event on tier
 * advancement. A datapack naming a trigger that is not here is a parse error, not a deed that never
 * fires — a silent no-op is the worst failure mode for content nobody can see working.
 */
@NullMarked
public enum DeedTrigger implements StringRepresentable {

    /**
     * A spell was cast successfully. {@code match} is the spell id, with or without a namespace.
     * Fires after the cast has actually resolved, so a refused or fizzled cast counts for nothing.
     */
    SPELL_CAST("spell_cast"),

    /**
     * The Ministry registered an offence. {@code match} is the offence's serialized name
     * ({@code crucio}, {@code unlicensed_apparition}, …).
     */
    OFFENCE("offence"),

    /**
     * A Bestiary entry advanced a discovery tier. {@code match} is the entry id; the tier reached is
     * matched separately by {@code minTier}, because "studied anything" and "mastered a Nundu" are
     * both things content wants to say.
     */
    BESTIARY_TIER("bestiary_tier");

    public static final Codec<DeedTrigger> CODEC = StringRepresentable.fromEnum(DeedTrigger::values);

    private final String serializedName;

    DeedTrigger(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
