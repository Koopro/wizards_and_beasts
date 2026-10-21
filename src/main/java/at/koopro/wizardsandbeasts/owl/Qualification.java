package at.koopro.wizardsandbeasts.owl;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * The two levels of wizarding qualification.
 *
 * <p>The O.W.L. certifies what a candidate has practised; the N.E.W.T. is the advanced paper in a subject the
 * candidate already passed well (O.W.L. Exceeds Expectations or better, {@code education.ExamRules#newtSeat}).
 * Elite careers, the top rank of a career track and the skill web's capstones ask for N.E.W.T.s; nothing in
 * ordinary play asks for either. Saved by name.
 */
public enum Qualification implements StringRepresentable {
    OWL,
    NEWT;

    public static final Codec<Qualification> CODEC = StringRepresentable.fromEnum(Qualification::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "education.level." + getSerializedName();
    }
}
