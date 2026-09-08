package at.koopro.wizardsandbeasts.card;

import org.jspecify.annotations.NullMarked;

/**
 * One card in the deck.
 *
 * <p>The English {@code name} and {@code achievement} live here rather than only in the language
 * file because {@code ModLanguageProvider} generates its entries from this list — one roster, one
 * place to add a wizard, and no way to ship a card whose name nobody wrote.
 *
 * @param id          the value stored in the {@code wizard_card_id} component; also the art and
 *                    model file name, and the {@code when} case in the item's select definition
 * @param rarity      draw weight and frame colour
 * @param name        display name, e.g. "Morgana le Fay"
 * @param achievement the one line the real cards print under the portrait
 */
@NullMarked
public record WizardCard(String id, CardRarity rarity, String name, String achievement) {

    /** Key for the wizard's name, kept on the old {@code .variant.} prefix so saved cards read the same. */
    public String nameKey() {
        return "item.wizards_and_beasts.famous_wizard_card.variant." + id;
    }

    /** Key for the achievement line under the portrait. */
    public String achievementKey() {
        return "item.wizards_and_beasts.famous_wizard_card.deed." + id;
    }
}
