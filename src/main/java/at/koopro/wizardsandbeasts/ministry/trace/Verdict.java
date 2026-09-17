package at.koopro.wizardsandbeasts.ministry.trace;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/** What a hearing decides, from lightest to heaviest. */
@NullMarked
public enum Verdict implements StringRepresentable {

    /** All charges cleared. */
    DISMISSED,
    /** A formal warning on the file. */
    WARNING,
    /** A fine against the wizard's vault. */
    FINE,
    /** The wand is held by the Ministry for a time. */
    WAND_CONFISCATION,
    /**
     * Referred to Azkaban: the arrestable offence is filed and the wand held for a long time. The sentence
     * itself is not served in-game yet — see {@code KNOWN_ISSUES}.
     */
    AZKABAN_REFERRAL;

    public static final Codec<Verdict> CODEC = StringRepresentable.fromEnum(Verdict::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
