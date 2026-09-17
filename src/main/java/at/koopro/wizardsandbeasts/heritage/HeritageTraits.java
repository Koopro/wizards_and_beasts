package at.koopro.wizardsandbeasts.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The words behind the trait ids a lineage or a condition carries.
 *
 * <p>This is what the selection screen and the character sheet read. A heritage is not a stat spread, so the answer
 * to "what does being a goblin mean?" is a list of named things — <i>Goblin-wrought craft</i>, <i>Goblin property
 * law</i> — each with a sentence, sorted into the three headings the player sees:
 *
 * <ul>
 *   <li>{@link Kind#TRAIT} — what this character can do, or cannot: Apparating without a wand, casting at all;</li>
 *   <li>{@link Kind#AFFINITY} — what their magic leans towards: runes, the stars, the household, living creatures;</li>
 *   <li>{@link Kind#CHARACTERISTIC} — what they are, body and standing: a spell-resistant hide, an allure, a debt of
 *       service, the moon's claim.</li>
 * </ul>
 *
 * <p>An id with no entry here is still a legal tag — code may read it — but it is invisible to the player, which is
 * the one thing the old tag set got wrong: {@code vault_access} and {@code divination_sight} were read by nothing and
 * shown to nobody. Held in a catalog rather than on the enum constants so a datapack-declared trait can join later
 * without either enum growing a display concern.
 */
@NullMarked
public final class HeritageTraits {

    /** Which heading a trait is printed under. */
    public enum Kind {
        TRAIT("traits"),
        AFFINITY("affinities"),
        CHARACTERISTIC("characteristics");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        /** Key for the heading itself, e.g. {@code gui.wizards_and_beasts.heritage.affinities}. */
        public String getHeadingKey() {
            return "gui." + WizardsAndBeastsMod.MODID + ".heritage." + id;
        }
    }

    /** One named trait: its heading, its name and its sentence. */
    public record Trait(String id, Kind kind) {

        public String getNameKey() {
            return "trait." + WizardsAndBeastsMod.MODID + "." + id;
        }

        public String getDescriptionKey() {
            return getNameKey() + ".desc";
        }
    }

    private static final Map<String, Trait> CATALOG = new LinkedHashMap<>();

    private static void put(String id, Kind kind) {
        CATALOG.put(id, new Trait(id, kind));
    }

    static {
        // ── Wizardkind lineages: culture and standing, never strength ──
        put("old_family", Kind.CHARACTERISTIC);      // doors open on a name; so do old prejudices
        put("two_worlds", Kind.CHARACTERISTIC);      // at home in both, fully trusted by neither
        put("muggle_raised", Kind.CHARACTERISTIC);   // arrived knowing nothing of this world and learned it anyway
        put("wizard_raised", Kind.CHARACTERISTIC);
        put("no_wand", Kind.TRAIT);
        put("no_casting", Kind.TRAIT);
        put("creature_kinship", Kind.AFFINITY);      // squibs and half-giants: beasts trust them

        // ── Goblins ──
        put("goblin_craft", Kind.AFFINITY);          // metalwork wizards cannot match and will not return
        put("goblin_property", Kind.TRAIT);          // the maker owns it; Gringotts keeps the ledger
        put("goblin_rebellions", Kind.CHARACTERISTIC);
        put("rune_affinity", Kind.AFFINITY);

        // ── House-elves ──
        put("innate_apparition", Kind.TRAIT);        // no wand, and wards do not hold them
        put("household_magic", Kind.AFFINITY);
        put("bound_service", Kind.CHARACTERISTIC);   // freed by a gift of clothing, and not before

        // ── Veela ──
        put("allure", Kind.CHARACTERISTIC);

        // ── Giants ──
        put("spell_resistant_hide", Kind.CHARACTERISTIC);

        // ── Centaurs ──
        put("star_reading", Kind.AFFINITY);
        put("forest_law", Kind.CHARACTERISTIC);      // their own laws, and no Ministry's

        // ── Vampires ──
        put("sunlight_weakness", Kind.CHARACTERISTIC);
        put("blood_hunger", Kind.CHARACTERISTIC);

        // ── Merpeople ──
        put("water_dwelling", Kind.TRAIT);
        put("mermish", Kind.AFFINITY);

        // ── Shared with conditions ──
        put("transformation", Kind.TRAIT);
        put("moon_sensitive", Kind.CHARACTERISTIC);
        put("obscurus_form", Kind.CHARACTERISTIC);
    }

    private HeritageTraits() {}

    /** The trait behind an id, or null when nothing describes it to the player. */
    public static @Nullable Trait byId(String id) {
        return CATALOG.get(id);
    }

    /** Every trait the catalog knows, in declaration order. */
    public static List<Trait> all() {
        return List.copyOf(CATALOG.values());
    }

    /**
     * The traits a lineage carries, under one heading. Used by the selection screen, which has a lineage in hand and
     * no character yet.
     */
    public static List<Trait> of(@Nullable HeritageVariant variant, Kind kind) {
        return variant == null ? List.of() : describe(variant.getTags(), kind);
    }

    /**
     * The traits a character carries, under one heading: their lineage's and their condition's together. This is the
     * character sheet's question — a bitten wizard is a half-blood <i>and</i> moon-sensitive.
     */
    public static List<Trait> of(@Nullable PlayerHeritageData data, Kind kind) {
        if (data == null) {
            return List.of();
        }
        List<Trait> traits = new ArrayList<>(of(data.getSelectedHeritageVariant(), kind));
        ConditionOrigin condition = data.getCondition();
        if (condition != null) {
            for (Trait trait : describe(condition.traits(), kind)) {
                if (!traits.contains(trait)) {
                    traits.add(trait);
                }
            }
        }
        return traits;
    }

    private static List<Trait> describe(Set<String> ids, Kind kind) {
        List<Trait> traits = new ArrayList<>();
        for (Trait trait : CATALOG.values()) {
            if (trait.kind() == kind && ids.contains(trait.id())) {
                traits.add(trait);
            }
        }
        return traits;
    }
}
