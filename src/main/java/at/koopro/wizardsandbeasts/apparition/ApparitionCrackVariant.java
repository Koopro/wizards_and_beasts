package at.koopro.wizardsandbeasts.apparition;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import com.mojang.serialization.Codec;

/**
 * Which crack an observer hears. Derived on the server and sent as a decision, never as the inputs behind it.
 *
 * <p>Deliberately not "elf heritage" and not "proficiency". Proficiency is a private stat that has no business
 * reaching other players' clients, and broadcasting true heritage would out anyone wearing another face — the
 * disguise systems exist, and an elf crack from a player who looks human is both an information leak and a
 * canon break. The server resolves both into one enum and observers learn nothing else.
 */
@NullMarked
public enum ApparitionCrackVariant implements StringRepresentable {

    /** The ordinary crack. */
    WIZARD("wizard"),

    /** Higher and snappier. Keyed to the caster's <i>apparent</i> form, never their true heritage. */
    ELF("elf"),

    /** A practised wizard barely disturbs the air. Still audible — Apparition is never silent. */
    MUFFLED("muffled");

    public static final Codec<ApparitionCrackVariant> CODEC =
            StringRepresentable.fromEnum(ApparitionCrackVariant::values);

    private final String serializedName;

    ApparitionCrackVariant(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
