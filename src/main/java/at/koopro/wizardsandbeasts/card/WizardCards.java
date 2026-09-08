package at.koopro.wizardsandbeasts.card;

import net.minecraft.util.RandomSource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The deck: twenty-four witches and wizards, each with their own card.
 *
 * <p><b>Order is not cosmetic.</b> {@link #ALL} is the order the cards are indexed in, and the
 * index is shown on the card's own tooltip ("14 of 24") the way a real set numbers itself.
 * Appending is safe; reordering renumbers every card a player already owns, so append.
 *
 * <p>Ids are never reused or renamed either — an id is what sits in the {@code wizard_card_id}
 * component of every card already in a chest, and a rename turns those into blanks.
 *
 * <p>The eight ids the mod shipped before this roster (Dumbledore, Harry, Lockhart, Merlin, Circe,
 * Paracelsus, Cliodna, Morgana) are all still here under their original ids for that reason.
 */
@NullMarked
public final class WizardCards {

    public static final List<WizardCard> ALL = List.of(
            // --- Common: the ones everybody has six of. -----------------------------------
            new WizardCard("albus_dumbledore", CardRarity.COMMON, "Albus Dumbledore",
                    "Defeated the dark wizard Grindelwald in 1945; discovered the twelve uses of dragon's blood."),
            new WizardCard("gilderoy_lockhart", CardRarity.COMMON, "Gilderoy Lockhart",
                    "Five-time winner of Witch Weekly's Most-Charming-Smile Award."),
            new WizardCard("hengist_of_woodcroft", CardRarity.COMMON, "Hengist of Woodcroft",
                    "Fled persecution to found Hogsmeade, the only all-magical settlement in Britain."),
            new WizardCard("alberic_grunnion", CardRarity.COMMON, "Alberic Grunnion",
                    "Inventor of the Dungbomb."),
            new WizardCard("bertie_bott", CardRarity.COMMON, "Bertie Bott",
                    "Confectioner who put every flavour into a bean, earwax included."),
            new WizardCard("glanmore_peakes", CardRarity.COMMON, "Glanmore Peakes",
                    "Slayer of the Sea Serpent of Cromer."),
            new WizardCard("wendelin_the_weird", CardRarity.COMMON, "Wendelin the Weird",
                    "Enjoyed being burnt so much she let herself be caught forty-seven times in disguise."),
            new WizardCard("uric_the_oddball", CardRarity.COMMON, "Uric the Oddball",
                    "Wore a jellyfish for a hat and slept in a room hung with forty-seven bells."),

            // --- Uncommon: names a collector recognises but does not have spare. ----------
            new WizardCard("paracelsus", CardRarity.UNCOMMON, "Paracelsus",
                    "Alchemist and healer, commemorated by a bust in a Hogwarts corridor."),
            new WizardCard("cliodna", CardRarity.UNCOMMON, "Cliodna",
                    "Druidess and Animagus; pioneered the use of moondew in potion-making."),
            new WizardCard("newt_scamander", CardRarity.UNCOMMON, "Newt Scamander",
                    "Magizoologist; author of Fantastic Beasts and Where to Find Them."),
            new WizardCard("bowman_wright", CardRarity.UNCOMMON, "Bowman Wright",
                    "Metal-charmer who forged the first Golden Snitch."),
            new WizardCard("artemisia_lufkin", CardRarity.UNCOMMON, "Artemisia Lufkin",
                    "First witch to hold the office of Minister for Magic."),
            new WizardCard("gunhilda_of_gorsemoor", CardRarity.UNCOMMON, "Gunhilda of Gorsemoor",
                    "Healer who devised the cure for dragon pox."),
            new WizardCard("cassandra_vablatsky", CardRarity.UNCOMMON, "Cassandra Vablatsky",
                    "Celebrated Seer; author of Unfogging the Future."),
            new WizardCard("adalbert_waffling", CardRarity.UNCOMMON, "Adalbert Waffling",
                    "Magical theoretician; laid down the fundamental laws in Magical Theory."),

            // --- Rare: the ones you trade for. -------------------------------------------
            new WizardCard("merlin", CardRarity.RARE, "Merlin",
                    "The most famous wizard of all time; the Order of Merlin bears his name."),
            new WizardCard("circe", CardRarity.RARE, "Circe",
                    "Sorceress of Aeaea, who turned sailors into swine."),
            new WizardCard("morgana_le_fay", CardRarity.RARE, "Morgana le Fay",
                    "Dark witch and Animagus; the great adversary of Merlin."),
            new WizardCard("ptolemy", CardRarity.RARE, "Ptolemy",
                    "Astronomer who charted the heavens long before Muggles claimed the maps."),
            new WizardCard("cornelius_agrippa", CardRarity.RARE, "Cornelius Agrippa",
                    "Imprisoned by Muggles who mistook his magic for madness."),
            new WizardCard("andros_the_invincible", CardRarity.RARE, "Andros the Invincible",
                    "The only wizard known to have produced a Patronus the size of a giant."),

            // --- Legendary: roughly one pull in eighty, between the two of them. ----------
            new WizardCard("nicolas_flamel", CardRarity.LEGENDARY, "Nicolas Flamel",
                    "The only known maker of the Philosopher's Stone; lived six hundred and sixty-five years."),
            new WizardCard("harry_potter", CardRarity.LEGENDARY, "Harry Potter",
                    "The Boy Who Lived. Printed after the Battle of Hogwarts, over his objection."));

    private static final Map<String, WizardCard> BY_ID = index();

    /** Sum of every card's weight — the denominator {@link #random} draws against. */
    public static final int TOTAL_WEIGHT = ALL.stream().mapToInt(c -> c.rarity().weight()).sum();

    private WizardCards() {}

    private static Map<String, WizardCard> index() {
        Map<String, WizardCard> byId = new LinkedHashMap<>();
        for (WizardCard card : ALL) {
            if (byId.put(card.id(), card) != null) {
                throw new IllegalStateException("duplicate wizard card id: " + card.id());
            }
        }
        return Map.copyOf(byId);
    }

    /** The card with this id, or null for an id no longer in the deck (an old save, a bad command). */
    public static @Nullable WizardCard byId(@Nullable String id) {
        return id == null ? null : BY_ID.get(id);
    }

    /** Position in the printed set, 1-based, or 0 for a card that is not in it. */
    public static int number(WizardCard card) {
        return ALL.indexOf(card) + 1;
    }

    /**
     * One card, drawn by weight.
     *
     * <p>Linear scan over twenty-four entries: the deck is small enough that a prefix-sum table
     * would cost more to keep correct than it saves.
     */
    public static WizardCard random(RandomSource random) {
        int roll = random.nextInt(TOTAL_WEIGHT);
        for (WizardCard card : ALL) {
            roll -= card.rarity().weight();
            if (roll < 0) {
                return card;
            }
        }
        return ALL.getLast();
    }
}
