package at.koopro.wizardsandbeasts.ministry.data;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

/**
 * A position held inside the Ministry. Authority is ordinal-ordered: anything a lower rank may do, a higher
 * one may also do, which keeps the command permission checks to a single comparison.
 */
@NullMarked
public enum MinistryRank implements StringRepresentable {

    /** Not employed by the Ministry. */
    NONE("none"),
    /** Memory modification. May issue notices. */
    OBLIVIATOR("obliviator"),
    /** Dark-wizard catchers. May arrest and release. */
    AUROR("auror"),
    /** Sets policy on individuals: may pardon. */
    MAGICAL_LAW_ENFORCEMENT("magical_law_enforcement"),
    /** May appoint and dismiss. */
    MINISTER("minister");

    public static final Codec<MinistryRank> CODEC = StringRepresentable.fromEnum(MinistryRank::values);

    private final String serializedName;

    MinistryRank(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public Component displayName() {
        return Component.translatable("ministry.wizards_and_beasts.rank." + serializedName);
    }

    /** True if this rank carries at least the authority of {@code required}. */
    public boolean atLeast(MinistryRank required) {
        return ordinal() >= required.ordinal();
    }

    public boolean mayArrest() {
        return atLeast(AUROR);
    }

    public boolean mayPardon() {
        return atLeast(MAGICAL_LAW_ENFORCEMENT);
    }

    public boolean mayAppoint() {
        return atLeast(MINISTER);
    }

    public boolean mayIssueNotices() {
        return atLeast(OBLIVIATOR);
    }
}
