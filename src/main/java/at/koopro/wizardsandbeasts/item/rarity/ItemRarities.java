package at.koopro.wizardsandbeasts.item.rarity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How rare each of the mod's objects is, as vanilla's rarity component: the name colour in every tooltip and
 * over the hotbar. Legendary (vanilla epic, the Hallows' violet) is kept for the one-of-a-kind: the Hallows, the
 * Horcruxes, the founders' relics, the Stone. Rare (aqua) is the remarkable made thing -- a Time-Turner, the
 * Marauder's Map, a forbidden book. Uncommon (yellow) is the magical everyday a shop might sell: spellbooks,
 * detectors, cores. Everything else stays common.
 *
 * <p>Set as default components (NeoForge's {@link ModifyDefaultComponentsEvent}), so it needs no change to any
 * registration and existing stacks pick it up. Wands are not here: their colour follows the wood
 * ({@code WandItem#getName}). A legendary item on the ground also catches the light
 * ({@code client.item.LegendaryGroundShimmer}).
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ItemRarities {

    private static final List<String> LEGENDARY = List.of(
            "resurrection_stone", "invisibility_cloak", "deathly_hallow_cloak", "philosophers_stone",
            "elixir_of_life", "sword_of_gryffindor", "sorting_hat", "riddles_diary", "marvolo_gaunts_ring",
            "slytherins_locket", "hufflepuffs_cup", "ravenclaws_diadem", "mirror_of_erised", "goblet_of_fire");

    private static final List<String> RARE = List.of(
            "time_turner", "marauders_map", "deluminator", "pensieve", "hermiones_beaded_bag", "triwizard_cup",
            "golden_egg", "tales_of_beedle_the_bard", "secrets_of_the_darkest_art", "moste_potente_potions",
            "hand_of_glory", "opal_necklace", "foe_glass", "two_way_mirror", "basilisk_fang", "golden_snitch",
            "vanishing_cabinet", "dark_mark_brand", "blood_pact_vial", "advanced_potion_making");

    private static final List<String> UNCOMMON = List.of(
            "remembrall", "sneakoscope", "portkey", "omnioculars", "extendable_ears", "secrecy_sensor",
            "probity_probe", "revealer", "memory_vial", "monster_book_of_monsters", "standard_book_of_spells",
            "magical_draughts_and_potions", "fantastic_beasts_and_where_to_find_them",
            "quidditch_through_the_ages", "one_thousand_magical_herbs_and_fungi",
            "beginners_guide_to_transfiguration", "unfogging_the_future", "a_history_of_magic",
            "hogwarts_a_history", "rise_and_fall_of_the_dark_arts", "phoenix_feather", "dragon_heartstring",
            "unicorn_hair", "thestral_tail_hair", "veela_hair", "troll_whisker", "wampus_cat_hair",
            "thunderbird_tail_feather", "rougarou_hair", "white_river_monster_spine", "da_galleon",
            "self_stirring_cauldron", "collapsible_cauldron", "essence_of_dittany", "gubraithian_fire");

    /** Every entry, by item id; package-visible so a test can hold the table to the registry. */
    static Map<String, Rarity> table() {
        Map<String, Rarity> out = new LinkedHashMap<>();
        UNCOMMON.forEach(id -> out.put(id, Rarity.UNCOMMON));
        RARE.forEach(id -> out.put(id, Rarity.RARE));
        LEGENDARY.forEach(id -> out.put(id, Rarity.EPIC));
        return out;
    }

    private ItemRarities() {}

    @SubscribeEvent
    static void onModifyDefaults(ModifyDefaultComponentsEvent event) {
        table().forEach((id, rarity) -> BuiltInRegistries.ITEM
                .getOptional(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, id))
                .ifPresent((Item item) -> event.modify(item, builder -> builder.set(DataComponents.RARITY, rarity))));
    }
}
