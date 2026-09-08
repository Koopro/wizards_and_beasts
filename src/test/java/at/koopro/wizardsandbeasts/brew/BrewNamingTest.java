package at.koopro.wizardsandbeasts.brew;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The id-to-key derivation that lets a bottle name itself without a registry.
 *
 * <p>Pure by design and pure by necessity: {@code Item.getName} runs on both sides, and the brew
 * catalogue is server-owned datapack content. See {@link BrewNaming} for why deriving beats looking up.
 */
class BrewNamingTest {

    @Test
    void aNamespacedIdBecomesTheConventionalKey() {
        assertEquals("brew.wizards_and_beasts.felix_felicis.name",
                BrewNaming.nameKey("wizards_and_beasts:felix_felicis"));
        assertEquals("brew.wizards_and_beasts.felix_felicis.desc",
                BrewNaming.descKey("wizards_and_beasts:felix_felicis"));
    }

    @Test
    void aBareIdTakesTheModNamespace() {
        // Must agree with Brews.byId, which does the same fallback. If they disagreed, a recipe naming
        // a bare id would brew a potion the cauldron announces under one name and the bottle under
        // another.
        assertEquals(BrewNaming.nameKey("wizards_and_beasts:wiggenweld_potion"),
                BrewNaming.nameKey("wiggenweld_potion"));
    }

    @Test
    void aForeignNamespaceKeepsItsOwn() {
        // An addon's brew belongs in the addon's lang file, not ours.
        assertEquals("brew.someaddon.moonshine.name", BrewNaming.nameKey("someaddon:moonshine"));
    }

    @Test
    void theLegacyNamespaceIsRemappedLikeEverywhereElse() {
        assertEquals("brew.wizards_and_beasts.skele_gro.name",
                BrewNaming.nameKey("WizardsAndBeastsMod:skele_gro"));
    }

    @Test
    void nameAndDescriptionAreDistinctKeys() {
        assertNotEquals(BrewNaming.nameKey("wizards_and_beasts:veritaserum"),
                BrewNaming.descKey("wizards_and_beasts:veritaserum"));
    }

    @Test
    void aPathWithNoNamespaceSeparatorIsNotTruncated() {
        // Guards the substring arithmetic: indexOf returning -1 must mean "no namespace", not "cut at 0".
        assertEquals("brew.wizards_and_beasts.amortentia.name", BrewNaming.nameKey("amortentia"));
    }
}
