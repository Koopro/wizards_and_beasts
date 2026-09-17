package at.koopro.wizardsandbeasts.ministry.trace;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * How word of an incident reaches the Ministry. Each channel is slow in its own way and knows only some of
 * the facts; nothing learns everything at the moment of the cast.
 */
@NullMarked
public enum ReportChannel implements StringRepresentable {

    /**
     * The Trace itself. Knows at once that magic happened around an underage wizard, and where — but not
     * who cast it when adult wizards are about ("the Ministry … relies on witches and wizards to enforce
     * underage wizardry restrictions within their homes", <i>Deathly Hallows</i> ch. 6).
     */
    TRACE,
    /** A Ministry official saw it happen, and knows who did it. */
    OFFICIAL,
    /**
     * Muggles saw something. The Obliviators and the Muggle-Worthy Excuse Committee hear of it after the
     * fact and learn a place and a time, not a name.
     */
    MUGGLE_REPORT,
    /**
     * The Department of Magical Law Enforcement following up an unattributed incident: interviewing
     * witnesses, examining the place. Either puts a name to it or lets the trail go cold.
     */
    INQUIRY;

    public static final Codec<ReportChannel> CODEC = StringRepresentable.fromEnum(ReportChannel::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
