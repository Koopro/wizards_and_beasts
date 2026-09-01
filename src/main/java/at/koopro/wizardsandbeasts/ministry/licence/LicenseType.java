package at.koopro.wizardsandbeasts.ministry.licence;

import at.koopro.wizardsandbeasts.owl.OWLSubject;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * What a Ministry licence is a licence <em>for</em>.
 *
 * <p>Each type names the O.W.L. subject an examiner would look at before endorsing a higher rank on
 * it — the Ministry does not hand out an Apparition endorsement to someone who failed Charms — which
 * is what {@link LicenceRules} reads when ink goes on the scroll. The mapping lives on the enum
 * rather than in the upgrade code so adding a licence type cannot forget to answer the question.
 */
@NullMarked
public enum LicenseType implements StringRepresentable {

    /** Apparating at all. Without one, {@code UNLICENSED_APPARITION} goes on your file. */
    APPARITION("apparition", OWLSubject.CHARMS),
    /** Riding the brooms the Ministry considers too fast for an unsupervised wizard. */
    BROOM("broom", OWLSubject.CHARMS),
    /**
     * Registration as an Animagus.
     *
     * <p>Spelled with the canon {@code ANIMAGUS} rather than the brief's {@code ANIMAGE}, which reads
     * as a slip: the mod already carries an Animagus vocabulary everywhere else
     * ({@code AnimagusAbilityService}, {@code MagicalOffence.UNREGISTERED_ANIMAGUS}), and a second
     * spelling on the wire would have been permanent.
     */
    ANIMAGUS("animagus", OWLSubject.TRANSFIGURATION),
    /** Auror training. Also what gets you through a Ministry door without a fine. */
    AUROR_TRAINEE("auror_trainee", OWLSubject.DEFENCE_AGAINST_DARK_ARTS),
    /** Keeping, breeding or transporting XXXX-class beasts. */
    DANGEROUS_CREATURES("dangerous_creatures", OWLSubject.CARE_OF_MAGICAL_CREATURES),
    /** Ordinary access to Ministry premises, for people who do not work there. */
    MINISTRY_ACCESS("ministry_access", OWLSubject.HISTORY_OF_MAGIC),
    /** Buying and selling the ingredients the Ministry controls. */
    RESTRICTED_SUBSTANCES("restricted_substances", OWLSubject.POTIONS);

    public static final Codec<LicenseType> CODEC = StringRepresentable.fromEnum(LicenseType::values);

    /** Highest rank the Ministry will endorse. Rank 0 is a bare licence; rank 3 is a full warrant. */
    public static final int MAX_RANK = 3;

    private final String serializedName;
    private final OWLSubject examinedSubject;

    LicenseType(String serializedName, OWLSubject examinedSubject) {
        this.serializedName = serializedName;
        this.examinedSubject = examinedSubject;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /** The O.W.L. an examiner weighs when endorsing a higher rank on this licence. */
    public OWLSubject examinedSubject() {
        return examinedSubject;
    }

    public Component displayName() {
        return Component.translatable("ministry.wizards_and_beasts.licence.type." + serializedName);
    }

    public static @Nullable LicenseType byName(String name) {
        for (LicenseType type : values()) {
            if (type.serializedName.equals(name)) {
                return type;
            }
        }
        return null;
    }
}
