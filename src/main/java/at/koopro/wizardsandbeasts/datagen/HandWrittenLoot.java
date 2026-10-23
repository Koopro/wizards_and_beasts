package at.koopro.wizardsandbeasts.datagen;

import java.util.Set;

/**
 * Entity loot tables written by hand in {@code src/main/resources/data/wizards_and_beasts/loot_table/entities/}.
 * Datagen writes no table for these ids, so each creature's drops live in exactly one file.
 *
 * <p>Before 2026-10-23 datagen also wrote all of these, with different drops -- unicorn hair, demiguise hair and an
 * erumpent horn on a kill -- and the hand-written file won only because {@code processResources} copies
 * {@code src/main} first and drops the duplicate. A change to either list could silently undo the other.
 * {@code HandWrittenLootTest} keeps this set equal to the folder.
 */
public final class HandWrittenLoot {

    public static final Set<String> ENTITY_TABLES = Set.of(
            "acromantula", "antipodean_opaleye", "augurey", "baby_niffler", "basilisk", "bowtruckle", "bundimun",
            "chinese_fireball", "common_welsh_green", "cornish_pixie", "demiguise", "erumpent", "ghoul",
            "golden_snidget", "granian", "hebridean_black", "hidebehind", "hippogriff", "horned_serpent",
            "hungarian_horntail", "lobalug", "matagot", "mooncalf", "murtlap", "niffler", "norwegian_ridgeback",
            "occamy", "peruvian_vipertooth", "phoenix", "pukwudgie", "romanian_longhorn", "rougarou", "runespoor",
            "streeler", "swedish_short_snout", "thestral", "thunderbird", "troll", "ukrainian_ironbelly",
            "unicorn", "wampus_cat", "werewolf", "yeti");

    private HandWrittenLoot() {}
}
