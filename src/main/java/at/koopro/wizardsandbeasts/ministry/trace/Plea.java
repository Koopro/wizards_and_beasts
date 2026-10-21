package at.koopro.wizardsandbeasts.ministry.trace;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * How the accused answers the charges.
 *
 * <p>Contesting puts the Ministry to its proof: a charge the evidence does not carry is dismissed. Pleading
 * guilty is evidence in itself and earns a lighter ruling, but gives up both the chance that the evidence is
 * thin and the right to appeal. Neither is the "right" answer — that depends on what the case file shows.
 */
@NullMarked
public enum Plea implements StringRepresentable {

    CONTEST,
    GUILTY;

    public static final Codec<Plea> CODEC = StringRepresentable.fromEnum(Plea::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
