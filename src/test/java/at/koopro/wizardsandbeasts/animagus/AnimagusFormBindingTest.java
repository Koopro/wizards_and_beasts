package at.koopro.wizardsandbeasts.animagus;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stored form id on {@code PlayerAbilityData} predates the datapack registry and stays a plain
 * {@code String}: every live reader keys on it and it sits in every existing save. These tests pin
 * the translation between that vocabulary and the registry's, because getting it wrong means a
 * player's saved form silently resolves to a different animal's physics.
 */
class AnimagusFormBindingTest {

    @Test
    void legacyPrefixedId_resolvesToTheDatapackKey() {
        assertEquals(Optional.of(Identifier.fromNamespaceAndPath("wizards_and_beasts", "cat")),
                AnimagusFormBinding.toFormKey("animagus_cat"));
        assertEquals(Optional.of(Identifier.fromNamespaceAndPath("wizards_and_beasts", "falcon")),
                AnimagusFormBinding.toFormKey("animagus_falcon"));
    }

    @Test
    void alreadyNamespacedId_passesThroughUnchanged() {
        assertEquals(Optional.of(Identifier.fromNamespaceAndPath("wizards_and_beasts", "rat")),
                AnimagusFormBinding.toFormKey("wizards_and_beasts:rat"));
    }

    @Test
    void bareBeastName_isAccepted() {
        assertEquals(Optional.of(Identifier.fromNamespaceAndPath("wizards_and_beasts", "dog")),
                AnimagusFormBinding.toFormKey("dog"));
    }

    @Test
    void nullBlankAndEmpty_resolveToNothing() {
        assertTrue(AnimagusFormBinding.toFormKey(null).isEmpty());
        assertTrue(AnimagusFormBinding.toFormKey("").isEmpty());
        assertTrue(AnimagusFormBinding.toFormKey("   ").isEmpty());
        assertTrue(AnimagusFormBinding.toFormKey("animagus_").isEmpty(),
                "a bare prefix names no beast and must not resolve to the namespace root");
    }

    @Test
    void roundTrip_storedToKeyToStored_isStable() {
        for (String stored : new String[]{"animagus_rat", "animagus_cat", "animagus_dog", "animagus_falcon"}) {
            Identifier key = AnimagusFormBinding.toFormKey(stored).orElseThrow();
            assertEquals(stored, AnimagusFormBinding.toStoredId(key));
        }
    }

    @Test
    void formsWithNoDatapackDefinition_resolveToAKeyButNotADefinition() {
        // animagus_stag and friends are shipped forms with no JSON yet. They must produce a key
        // (so a query can name them) while resolving to no definition (so nothing loads the wrong
        // physics on their behalf).
        Identifier key = AnimagusFormBinding.toFormKey("animagus_stag").orElseThrow();
        assertEquals("wizards_and_beasts:stag", key.toString());
        assertTrue(AnimagusFormRegistry.get(key) == null,
                "the registry is empty outside a datapack load; nothing may fabricate a definition");
    }
}
