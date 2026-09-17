package at.koopro.wizardsandbeasts.ministry.trace;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * Where an open case against a wizard stands. A record or a warning is answered by letter and opens no case;
 * these are the stages that take time.
 */
@NullMarked
public enum CaseStage implements StringRepresentable {

    /** The Department is looking into it. Ends in a summons. */
    INVESTIGATING,
    /** A hearing has been called. The wizard is expected to answer before the deadline. */
    SUMMONED,
    /** Aurors hold the case and are looking for the wizard where they were last reported. */
    AURORS_ASSIGNED;

    public static final Codec<CaseStage> CODEC = StringRepresentable.fromEnum(CaseStage::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
