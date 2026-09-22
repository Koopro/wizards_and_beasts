package at.koopro.wizardsandbeasts.spell;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The law files are the one register of what is forbidden.
 *
 * <p>Spell legality and spell acquisition used to disagree: {@code spell_law} classified spells and
 * the learning path read none of it, so a book could teach Avada Kedavra with no more friction than
 * Lumos. {@code SpellLawLearningGate} now reads the classification, which makes these files load
 * bearing in a way they were not before — a spell quietly dropping to {@code unrestricted} is now a
 * gate silently opening, not just a Ministry record that reads oddly.
 *
 * <p>These are the data-side guards. The behaviour they protect — that a student without the study is
 * refused, and that the refusal names why — is covered by {@code SpellLawLearningTests}.
 */
class SpellLawCoverageTest {

    private static final Path LAW_DIR =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "spell_law");
    private static final Path SPELL_DIR =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "spells");

    private static final Set<String> LEGAL_CLASSES =
            Set.of("unrestricted", "restricted", "dark", "unforgivable");

    private static Map<String, String> authoredLaws() throws IOException {
        Map<String, String> laws = new HashMap<>();
        try (Stream<Path> files = Files.walk(LAW_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject law = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                String name = file.getFileName().toString();
                laws.put(name.substring(0, name.length() - ".json".length()),
                        law.get("legal_class").getAsString());
            }
        }
        return laws;
    }

    @Test
    void everyAuthoredLawNamesAClassTheCodeUnderstands() throws IOException {
        List<String> unknown = new ArrayList<>();
        authoredLaws().forEach((spell, legalClass) -> {
            if (!LEGAL_CLASSES.contains(legalClass.toLowerCase(Locale.ROOT))) {
                unknown.add(spell + " -> " + legalClass);
            }
        });
        assertTrue(unknown.isEmpty(), "unrecognised legal_class values: " + unknown);
    }

    /**
     * Java-registered spells, which have a law file and deliberately no datapack definition.
     *
     * <p>The bespoke ones override {@code executeCast} — the Killing Curse is a lethal beam with a
     * love-protection exemption, the Imperius Curse owns a command UI and a resist contest — and
     * neither is expressible as an effect-component list. Their law still lives in the datapack with
     * everyone else's, which is the point: one register of what is forbidden, whatever implements it.
     */
    private static final Set<String> JAVA_REGISTERED = Set.of("avada_kedavra", "imperio");

    /**
     * Every law file must name a spell that exists, or the classification protects nothing and the
     * gate it now feeds will never fire.
     */
    @Test
    void everyAuthoredLawPointsAtARealSpell() throws IOException {
        List<String> orphans = new ArrayList<>();
        for (String spell : authoredLaws().keySet()) {
            if (JAVA_REGISTERED.contains(spell)) {
                continue;
            }
            if (!Files.exists(SPELL_DIR.resolve(spell + ".json"))) {
                orphans.add(spell);
            }
        }
        assertTrue(orphans.isEmpty(), "law files with no spell behind them: " + orphans);
    }

    /**
     * The converse, so the exemption above cannot rot: a name listed as Java-registered must really
     * have no datapack definition. If one ever grows a JSON file the exemption should go, not linger
     * as a hole in the check above.
     */
    @Test
    void theJavaRegisteredExemptionIsStillEarned() {
        List<String> nowDataDriven = new ArrayList<>();
        for (String spell : JAVA_REGISTERED) {
            if (Files.exists(SPELL_DIR.resolve(spell + ".json"))) {
                nowDataDriven.add(spell);
            }
        }
        assertTrue(nowDataDriven.isEmpty(),
                "these now ship a definition and no longer need exempting: " + nowDataDriven);
    }

    /**
     * The three curses the mod treats as Unforgivable must still be classified that way.
     *
     * <p>Pinned deliberately. {@code UnforgivableToll} now asks the law rather than keeping its own
     * list, which is the right direction and also means a single edit to one of these files would
     * quietly make the Killing Curse free to cast and free to learn.
     */
    @Test
    void theThreeUnforgivablesAreStillUnforgivable() throws IOException {
        Map<String, String> laws = authoredLaws();
        for (String curse : List.of("avada_kedavra", "crucio", "imperio")) {
            assertEquals("unforgivable", laws.get(curse),
                    curse + " must remain classified unforgivable: the cast toll, the learning gate"
                            + " and the Wizengamot all read this one field");
        }
    }

    /**
     * A dark spell is not an Unforgivable and must not drift into being one, or the learning gate
     * starts demanding curse control for a hex a schoolboy invented.
     */
    @Test
    void darkSpellsAreDarkAndNotUnforgivable() throws IOException {
        Map<String, String> laws = authoredLaws();
        for (String spell : List.of("morsmordre", "sectumsempra")) {
            assertEquals("dark", laws.get(spell), spell + " should be classified dark");
        }
    }

    /**
     * The ordinary duelling spells must stay out of the dark classes. Twelve spells are restricted,
     * including Stupefy and Expelliarmus, and the gate deliberately asks nothing extra of them —
     * a drift to {@code dark} would lock first-year duelling behind the Dark Arts web.
     */
    @Test
    void ordinaryDuellingStaysOutOfTheDarkClasses() throws IOException {
        Map<String, String> laws = authoredLaws();
        List<String> misfiled = new ArrayList<>();
        for (String spell : List.of("stupefy", "expelliarmus", "petrificus_totalus", "incendio")) {
            String legalClass = laws.get(spell);
            if ("dark".equals(legalClass) || "unforgivable".equals(legalClass)) {
                misfiled.add(spell + " -> " + legalClass);
            }
        }
        assertTrue(misfiled.isEmpty(),
                "these are regulated in use, not hidden knowledge: " + misfiled);
    }
}
