package at.koopro.wizardsandbeasts.card;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the deck and its art in step.
 *
 * <p>The roster lives in Java and the pictures live in {@code tools/wizard_cards.py}, which is two
 * lists that have to agree. They will not stay agreed on their own: adding a wizard to
 * {@link WizardCards} without rerunning the tool ships a card with no portrait and no case in the
 * item's select, which in game is a silent fall back to the blank card — the kind of bug you only
 * notice by pulling that exact wizard.
 */
class WizardCardAssetsTest {

    private static final Path ASSETS =
            Path.of("src", "main", "resources", "assets", "wizards_and_beasts");

    @Test
    void everyWizardHasArtAndAModel() {
        for (WizardCard card : WizardCards.ALL) {
            assertTrue(Files.exists(ASSETS.resolve("textures/item/card/" + card.id() + ".png")),
                    "no portrait for " + card.id() + " — rerun tools/wizard_cards.py");
            assertTrue(Files.exists(ASSETS.resolve("models/item/card/" + card.id() + ".json")),
                    "no model for " + card.id() + " — rerun tools/wizard_cards.py");
        }
    }

    @Test
    void theSelectDefinitionCoversTheWholeDeck() throws IOException {
        JsonObject model;
        try (Reader reader = Files.newBufferedReader(ASSETS.resolve("items/famous_wizard_card.json"))) {
            model = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("model");
        }
        assertEquals("minecraft:select", model.get("type").getAsString());
        assertEquals("minecraft:component", model.get("property").getAsString());
        assertEquals("wizards_and_beasts:wizard_card_id", model.get("component").getAsString());

        JsonArray cases = model.getAsJsonArray("cases");
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            selected.add(cases.get(i).getAsJsonObject().get("when").getAsString());
        }
        assertEquals(WizardCards.ALL.stream().map(WizardCard::id).toList(), selected,
                "select cases drifted from WizardCards.ALL — rerun tools/wizard_cards.py");
    }

    @Test
    void idsAreStableAndUnique() {
        assertEquals(WizardCards.ALL.size(),
                WizardCards.ALL.stream().map(WizardCard::id).distinct().count());
        for (WizardCard card : WizardCards.ALL) {
            assertTrue(card.id().matches("[a-z0-9_]+"), "bad id: " + card.id());
            assertTrue(!card.name().isBlank() && !card.achievement().isBlank(),
                    "card without a name or a deed: " + card.id());
        }
    }

    @Test
    void theRareCardsAreActuallyRare() {
        // The point of the tiers is that a Dumbledore is common and an Andros is not. If the
        // weights ever flatten out the collection stops being a collection.
        int commons = WizardCards.ALL.stream()
                .filter(c -> c.rarity() == CardRarity.COMMON).mapToInt(c -> c.rarity().weight()).sum();
        int legendaries = WizardCards.ALL.stream()
                .filter(c -> c.rarity() == CardRarity.LEGENDARY).mapToInt(c -> c.rarity().weight()).sum();
        assertTrue(commons > legendaries * 20,
                "commons " + commons + " vs legendaries " + legendaries + " of " + WizardCards.TOTAL_WEIGHT);
        assertEquals(WizardCards.TOTAL_WEIGHT,
                WizardCards.ALL.stream().mapToInt(c -> c.rarity().weight()).sum());
    }

    @Test
    void everyDrawLandsOnACard() {
        // The weighted scan subtracts until it goes negative; an off-by-one at either end of the
        // range would fall through to the last card, which is exactly the bug that hides.
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(1234L);
        int[] hits = new int[WizardCards.ALL.size()];
        for (int i = 0; i < 40_000; i++) {
            hits[WizardCards.ALL.indexOf(WizardCards.random(random))]++;
        }
        for (int i = 0; i < hits.length; i++) {
            assertTrue(hits[i] > 0, "never drew " + WizardCards.ALL.get(i).id());
        }
    }
}
