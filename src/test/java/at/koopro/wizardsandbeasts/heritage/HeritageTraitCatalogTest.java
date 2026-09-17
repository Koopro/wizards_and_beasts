package at.koopro.wizardsandbeasts.heritage;

import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trait catalog is what the player reads instead of a stat block, so the two failure modes that matter are a
 * trait nobody describes and a description nobody shows.
 *
 * <p>Both were real before: {@code vault_access} and {@code divination_sight} were declared on lineages, read by no
 * code and shown in no screen, so being a goblin "granted" something that did not exist.
 */
class HeritageTraitCatalogTest {

    private static final Path LANG =
            Path.of("src", "main", "resources", "assets", "wizards_and_beasts", "lang", "en_us.json");

    private static Set<String> declaredTraitIds() {
        Set<String> ids = new HashSet<>();
        for (HeritageVariant variant : HeritageVariant.values()) {
            ids.addAll(variant.getTags());
        }
        for (ConditionOrigin origin : ConditionOrigin.values()) {
            ids.addAll(origin.traits());
        }
        return ids;
    }

    @Test
    void everyTraitALineageOrConditionDeclaresHasWords() {
        List<String> undescribed = new ArrayList<>();
        for (String id : declaredTraitIds()) {
            if (HeritageTraits.byId(id) == null) {
                undescribed.add(id);
            }
        }
        assertTrue(undescribed.isEmpty(),
                "these traits are carried by somebody and described to nobody: " + undescribed);
    }

    @Test
    void everyCatalogedTraitIsActuallyCarried() {
        Set<String> declared = declaredTraitIds();
        List<String> orphans = new ArrayList<>();
        for (HeritageTraits.Trait trait : HeritageTraits.all()) {
            if (!declared.contains(trait.id())) {
                orphans.add(trait.id());
            }
        }
        assertTrue(orphans.isEmpty(), "these traits are described and carried by no lineage or condition: " + orphans);
    }

    @Test
    void everyTraitAndConditionHasAnEnglishNameAndSentence() throws IOException {
        String lang = Files.readString(LANG);
        List<String> missing = new ArrayList<>();
        for (HeritageTraits.Trait trait : HeritageTraits.all()) {
            for (String key : List.of(trait.getNameKey(), trait.getDescriptionKey())) {
                if (!lang.contains('"' + key + '"')) {
                    missing.add(key);
                }
            }
        }
        for (MagicalCondition condition : MagicalCondition.values()) {
            for (String key : List.of(condition.getTranslationKey(), condition.getDescriptionTranslationKey())) {
                if (!lang.contains('"' + key + '"')) {
                    missing.add(key);
                }
            }
        }
        for (ConditionOrigin origin : ConditionOrigin.values()) {
            for (String key : List.of(origin.getTranslationKey(), origin.getDescriptionTranslationKey())) {
                if (!lang.contains('"' + key + '"')) {
                    missing.add(key);
                }
            }
        }
        for (HeritageTraits.Kind kind : HeritageTraits.Kind.values()) {
            if (!lang.contains('"' + kind.getHeadingKey() + '"')) {
                missing.add(kind.getHeadingKey());
            }
        }
        assertTrue(missing.isEmpty(), "untranslated: " + missing);
    }

    @Test
    void eachHeritageSaysSomethingAboutItself() {
        for (Heritage heritage : Heritage.values()) {
            List<HeritageTraits.Trait> described = new ArrayList<>();
            for (HeritageVariant variant : heritage.getSubtypes()) {
                for (HeritageTraits.Kind kind : HeritageTraits.Kind.values()) {
                    described.addAll(HeritageTraits.of(variant, kind));
                }
            }
            if (heritage == Heritage.WIZARDKIND) {
                // The baseline. Blood status is culture, so its lineages carry standing and nothing else; that is
                // the point of Wizardkind having no bonus at all.
                assertFalse(described.isEmpty(), "even the baseline says which family a character comes from");
                continue;
            }
            assertFalse(described.isEmpty(),
                    heritage.getId() + " has nothing to say about itself in words, which is how a heritage "
                            + "becomes a row of numbers again");
        }
    }

    @Test
    void aCharactersTraitsAreTheirLineagesAndTheirConditionsTogether() {
        PlayerHeritageData data = new PlayerHeritageData();
        data.setSelectedHeritage(Heritage.WIZARDKIND);
        data.setSelectedHeritageVariant(HeritageVariant.PURE_BLOOD);
        data.setCondition(ConditionOrigin.CRADLE_BITTEN);

        List<HeritageTraits.Trait> characteristics =
                HeritageTraits.of(data, HeritageTraits.Kind.CHARACTERISTIC);
        assertTrue(characteristics.contains(HeritageTraits.byId("old_family")));
        assertTrue(characteristics.contains(HeritageTraits.byId("moon_sensitive")));

        List<HeritageTraits.Trait> traits = HeritageTraits.of(data, HeritageTraits.Kind.TRAIT);
        assertTrue(traits.contains(HeritageTraits.byId("transformation")));
        assertNotNull(HeritageTraits.byId("transformation"));
    }

    @Test
    void headingsAreDistinctAndEveryTraitSitsUnderExactlyOne() {
        for (HeritageTraits.Trait trait : HeritageTraits.all()) {
            int found = 0;
            for (HeritageTraits.Kind kind : HeritageTraits.Kind.values()) {
                if (trait.kind() == kind) {
                    found++;
                }
            }
            assertTrue(found == 1, trait.id() + " sits under " + found + " headings");
        }
    }
}
