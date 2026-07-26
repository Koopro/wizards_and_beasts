package at.koopro.wizardsandbeasts.wand.resonance;

import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The wizard side of wood affinity: which personality traits a heritage variant reads as.
 *
 * <p>Wand woods advertise the wizards they favour in {@code personality_affinity} — a vocabulary of
 * character words ({@code determined}, {@code guardian}, {@code seeker}, …). Nothing on the wizard
 * ever spoke that vocabulary: {@link HeritageVariant} carries ids ({@code pure_blood}) and capability
 * tags ({@code enhanced_bond}), so the two sets never intersected and wood affinity scored a hard 0
 * for every player who had chosen a heritage. On a 0.4 weight that alone put the 0.65 match threshold
 * out of reach, i.e. no wand could bond and nothing could be cast.
 *
 * <p>This table is that missing half. Traits are drawn only from the vocabulary the shipped woods
 * actually use, so every variant resonates with two to four of the ten woods — enough for the wand to
 * still choose the wizard, not so few that a wizard can be left wandless.
 *
 * <p>Datapacks may keep using variant ids and capability tags in {@code personality_affinity}; those
 * are matched too (see {@link WandResonanceSystem#traitsOf}).
 */
public final class WizardPersonality {

    private static final Map<HeritageVariant, Set<String>> TRAITS = new EnumMap<>(HeritageVariant.class);

    static {
        // Wizardkind
        put(HeritageVariant.PURE_BLOOD, "principled", "ambitious", "destined");
        put(HeritageVariant.HALF_BLOOD, "adaptable", "curious", "steadfast");
        put(HeritageVariant.MUGGLE_BORN, "curious", "determined", "exceptional");
        put(HeritageVariant.SQUIB, "perceptive", "patient", "survivor");
        put(HeritageVariant.ADOPTED_MAGICAL, "adaptable", "empathetic", "seeker");

        // Werewolf
        put(HeritageVariant.WEREWOLF_BITTEN, "survivor", "resolute", "selfless");
        put(HeritageVariant.WEREWOLF_BORN, "unyielding", "intense", "fated");
        put(HeritageVariant.WEREWOLF_SAVAGE, "hardened", "intense", "survivor");

        // Obscurial
        put(HeritageVariant.SUPPRESSED, "complex", "patient", "seeker");
        put(HeritageVariant.UNLEASHED, "intense", "unyielding", "exceptional");

        // Goblin
        put(HeritageVariant.GOBLIN_COMMON, "perceptive", "principled", "resolute");
        put(HeritageVariant.GOBLIN_WARRIOR, "hardened", "unyielding", "determined");
        put(HeritageVariant.GOBLIN_RUNE, "inventive", "focused", "curious");

        // House-Elf
        put(HeritageVariant.ELF_BOUND, "selfless", "steadfast", "patient");
        put(HeritageVariant.ELF_FREE, "adaptable", "seeker", "gentle");
        put(HeritageVariant.ELF_HALL, "guardian", "steadfast", "nurturing");

        // Veela
        put(HeritageVariant.VEELA_FULL, "intense", "exceptional", "ambitious");
        put(HeritageVariant.VEELA_HALF, "empathetic", "complex", "adaptable");
        put(HeritageVariant.VEELA_QUARTER, "perceptive", "gentle", "curious");

        // Giant
        put(HeritageVariant.GIANT_FULL, "unyielding", "hardened", "intense");
        put(HeritageVariant.GIANT_HALF, "gentle", "guardian", "steadfast");
        put(HeritageVariant.GIANT_CLAN, "guardian", "principled", "patient");

        // Centaur
        put(HeritageVariant.CENTAUR_FOREST, "guardian", "patient", "visionary");
        put(HeritageVariant.CENTAUR_WAR, "resolute", "determined", "unyielding");
        put(HeritageVariant.CENTAUR_STARGAZER, "visionary", "seeker", "perceptive");

        // Vampire
        put(HeritageVariant.VAMPIRE_TURNED, "complex", "survivor", "fated");
        put(HeritageVariant.VAMPIRE_BORN, "ambitious", "destined", "hardened");
        put(HeritageVariant.VAMPIRE_DHAMPIR, "complex", "adaptable", "seeker");

        // Merpeople
        put(HeritageVariant.MERPEOPLE_MERROW, "guardian", "steadfast", "principled");
        put(HeritageVariant.MERPEOPLE_SELKIE, "adaptable", "empathetic", "curious");
        put(HeritageVariant.MERPEOPLE_SIREN, "intense", "visionary", "unyielding");
    }

    private WizardPersonality() {
    }

    private static void put(HeritageVariant variant, String... traits) {
        TRAITS.put(variant, Set.of(traits));
    }

    /** Personality traits for {@code variant}, or an empty set for an unmapped one. */
    public static Set<String> of(@Nullable HeritageVariant variant) {
        if (variant == null) {
            return Set.of();
        }
        return TRAITS.getOrDefault(variant, Set.of());
    }

    /** Every trait word in the table — used by tests to assert the woods stay reachable. */
    public static Set<String> vocabulary() {
        Set<String> all = new HashSet<>();
        TRAITS.values().forEach(all::addAll);
        return all;
    }
}
