package at.koopro.wizardsandbeasts.ministry.trace;

import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped spell law reads the way the brief and canon say it should: not all illegal magic is equal. */
class SpellLawDataTest {

    private static final Path ROOT = Path.of("src", "main", "resources", "data", "wizards_and_beasts");

    private static SpellLaw law(String spell) throws IOException {
        String json = Files.readString(ROOT.resolve("spell_law").resolve(spell + ".json"));
        return SpellLaw.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test
    void everyShippedFileParsesAndNamesARealSpell() throws IOException {
        List<String> bespoke = List.of("imperio", "avada_kedavra");
        try (Stream<Path> files = Files.list(ROOT.resolve("spell_law"))) {
            for (Path file : files.toList()) {
                String spell = file.getFileName().toString().replace(".json", "");
                law(spell);
                assertTrue(bespoke.contains(spell) || Files.exists(ROOT.resolve("spells").resolve(spell + ".json")),
                        spell + " has law but no spell");
            }
        }
    }

    @Test
    void theBriefsExamplesAreGradedApart() throws IOException {
        assertEquals(LegalClass.UNRESTRICTED, law("lumos").legalClass());
        assertEquals(LegalClass.UNRESTRICTED, law("accio").legalClass());
        assertEquals(LegalClass.UNRESTRICTED, law("reparo").legalClass());
        assertEquals(LegalClass.RESTRICTED, law("stupefy").legalClass());
        assertEquals(Optional.of(MagicalOffence.IMPERIO), law("imperio").offence());
        assertEquals(Optional.of(MagicalOffence.CRUCIO), law("crucio").offence());
        assertEquals(Optional.of(MagicalOffence.AVADA_KEDAVRA), law("avada_kedavra").offence());
        assertTrue(law("avada_kedavra").visibility() > law("imperio").visibility(),
                "a flash of green light is seen; the Imperius Curse is not");
    }

    @Test
    void everyUnforgivableIsACrimeInItselfAndNothingElseIs() throws IOException {
        try (Stream<Path> files = Files.list(ROOT.resolve("spell_law"))) {
            for (Path file : files.toList()) {
                SpellLaw law = law(file.getFileName().toString().replace(".json", ""));
                assertEquals(law.legalClass() == LegalClass.UNFORGIVABLE, law.offence().isPresent(), file.toString());
            }
        }
    }

    @Test
    void anUnauthoredSpellIsNeverUnforgivable() {
        for (SpellCategory category : SpellCategory.values()) {
            assertTrue(SpellLaw.defaultFor(category).legalClass() != LegalClass.UNFORGIVABLE);
        }
    }
}
