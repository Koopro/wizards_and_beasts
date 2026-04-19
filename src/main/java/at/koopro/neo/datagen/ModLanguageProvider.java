package at.koopro.neo.datagen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

import at.koopro.neo.Neo;

public class ModLanguageProvider extends LanguageProvider {
    public ModLanguageProvider(PackOutput output) {
        super(output, Neo.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        // Creative tab
        add("itemGroup." + Neo.MODID + ".main", "Neo Arcanum");

        // Debug tools
        add("item.neo.debug_wand", "Debug Wand");
        add("item.neo.morph_wand", "Morphologist's Wand");

        // Elder
        add("block.neo.elder_log", "Elder Log");
        add("block.neo.stripped_elder_log", "Stripped Elder Log");
        add("block.neo.elder_wood", "Elder Wood");
        add("block.neo.stripped_elder_wood", "Stripped Elder Wood");
        add("block.neo.elder_planks", "Elder Planks");
        add("block.neo.elder_slab", "Elder Slab");
        add("block.neo.elder_stairs", "Elder Stairs");
        add("block.neo.elder_leaves", "Elder Leaves");
        add("block.neo.elder_sapling", "Elder Sapling");

        // Yew
        add("block.neo.yew_log", "Yew Log");
        add("block.neo.stripped_yew_log", "Stripped Yew Log");
        add("block.neo.yew_wood", "Yew Wood");
        add("block.neo.stripped_yew_wood", "Stripped Yew Wood");
        add("block.neo.yew_planks", "Yew Planks");
        add("block.neo.yew_slab", "Yew Slab");
        add("block.neo.yew_stairs", "Yew Stairs");
        add("block.neo.yew_leaves", "Yew Leaves");
        add("block.neo.yew_sapling", "Yew Sapling");

        // Holly
        add("block.neo.holly_log", "Holly Log");
        add("block.neo.stripped_holly_log", "Stripped Holly Log");
        add("block.neo.holly_wood", "Holly Wood");
        add("block.neo.stripped_holly_wood", "Stripped Holly Wood");
        add("block.neo.holly_planks", "Holly Planks");
        add("block.neo.holly_slab", "Holly Slab");
        add("block.neo.holly_stairs", "Holly Stairs");
        add("block.neo.holly_leaves", "Holly Leaves");
        add("block.neo.holly_sapling", "Holly Sapling");

        // Wand
        add("item.neo.wand", "Wand");
        add("item.neo.phoenix_feather", "Phoenix Feather");
        add("item.neo.dragon_heartstring", "Dragon Heartstring");
        add("item.neo.unicorn_hair", "Unicorn Hair");
        add("item.neo.thestral_tail_hair", "Thestral Tail Hair");

        // Keybindings
        add("key.categories.neo.spells", "Neo Spells");
        add("key.neo.spell_up", "Spell Slot Up");
        add("key.neo.spell_right", "Spell Slot Right");
        add("key.neo.spell_down", "Spell Slot Down");
        add("key.neo.spell_left", "Spell Slot Left");
        add("key.neo.spell_menu", "Spell Menu");

        // Debug editor keybindings
        add("key.categories.neo.debug", "Neo Debug");
        add("key.neo.debug_toggle", "Toggle Model Debug Editor");
        add("key.neo.debug_export", "Export Debug Transforms");
        add("key.neo.debug_model_mode", "Switch Vanilla/GeckoLib Mode");

        // Marauder's Map items
        add("item.neo.marauders_map", "Marauder's Map");
        add("item.neo.parchment", "Parchment");
        add("item.neo.ink_bottle", "Ink Bottle");
        add("item.neo.broom", "Broom");

        // Currency
        add("item.neo.knut", "Knut");
        add("item.neo.sickle", "Sickle");
        add("item.neo.galleon", "Galleon");
        add("item.neo.leprechaun_gold", "Leprechaun Gold");
        add("item.neo.dragot", "Dragot");

        // Entities
        add("entity.neo.goblin_teller", "Goblin Teller");
        add("entity.neo.niffler", "Niffler");
        add("entity.neo.form_mannequin", "Form Mannequin");
        add("item.neo.goblin_teller_spawn_egg", "Goblin Teller Spawn Egg");
        add("item.neo.niffler_spawn_egg", "Niffler Spawn Egg");

        // Rowan
        add("block.neo.rowan_log", "Rowan Log");
        add("block.neo.stripped_rowan_log", "Stripped Rowan Log");
        add("block.neo.rowan_wood", "Rowan Wood");
        add("block.neo.stripped_rowan_wood", "Stripped Rowan Wood");
        add("block.neo.rowan_planks", "Rowan Planks");
        add("block.neo.rowan_slab", "Rowan Slab");
        add("block.neo.rowan_stairs", "Rowan Stairs");
        add("block.neo.rowan_leaves", "Rowan Leaves");
        add("block.neo.rowan_sapling", "Rowan Sapling");

        // Wizarding World — items
        add("item.neo.brew", "Brew");
        add("item.neo.butterbeer", "Butterbeer");
        add("item.neo.pumpkin_juice", "Pumpkin Juice");
        add("item.neo.chocolate_frog", "Chocolate Frog");
        add("item.neo.famous_wizard_card", "Famous Wizard Card");
        add("item.neo.famous_wizard_card.blank", "Blank card");
        add("item.neo.famous_wizard_card.variant.albus_dumbledore", "Albus Dumbledore");
        add("item.neo.famous_wizard_card.variant.harry_potter", "Harry Potter");
        add("item.neo.famous_wizard_card.variant.gilderoy_lockhart", "Gilderoy Lockhart");
        add("item.neo.famous_wizard_card.variant.merlin", "Merlin");
        add("item.neo.famous_wizard_card.variant.circe", "Circe");
        add("item.neo.famous_wizard_card.variant.paracelsus", "Paracelsus");
        add("item.neo.famous_wizard_card.variant.cliodna", "Cliodna");
        add("item.neo.famous_wizard_card.variant.morgana_le_fay", "Morgana le Fay");
        add("item.neo.bertie_botts_every_flavour_beans", "Bertie Bott's Every Flavour Beans");
        add("item.neo.droobles_best_blowing_gum", "Drooble's Best Blowing Gum");
        add("item.neo.firewhisky", "Firewhisky");
        add("item.neo.gillyweed", "Gillyweed");
        add("item.neo.dirigible_plum", "Dirigible Plum");
        add("item.neo.dittany", "Dittany");
        add("item.neo.occamy_eggshell", "Occamy Eggshell");
        add("item.neo.bezoar", "Bezoar");
        add("item.neo.demiguise_hair", "Demiguise Hair");
        add("item.neo.mooncalf_dung", "Mooncalf Dung");
        add("item.neo.erumpent_horn", "Erumpent Horn");
        add("item.neo.mandrake", "Mandrake");
        add("item.neo.mandrake_seeds", "Mandrake Seeds");
        add("item.neo.remembrall", "Remembrall");
        add("item.neo.omnioculars", "Omnioculars");
        add("item.neo.deluminator", "Deluminator");
        add("item.neo.sneakoscope", "Sneakoscope");
        add("item.neo.portkey", "Portkey");
        add("item.neo.portkey.unlinked", "Sneak-use on a block to link a destination.");
        add("item.neo.portkey.linked", "Linked: %s, %s, %s");
        add("item.neo.peruvian_instant_darkness_powder", "Peruvian Instant Darkness Powder");
        add("item.neo.decoy_detonator", "Decoy Detonator");
        add("item.neo.extendable_ears", "Extendable Ears");
        add("item.neo.floo_powder", "Floo Powder");

        // Wizarding World — blocks
        add("block.neo.devils_snare", "Devil's Snare");
        add("block.neo.mandrake_crop", "Mandrake");
        add("block.neo.mallowsweet", "Mallowsweet");
        add("block.neo.gryffindor_banner", "Gryffindor Banner");
        add("block.neo.slytherin_banner", "Slytherin Banner");
        add("block.neo.ravenclaw_banner", "Ravenclaw Banner");
        add("block.neo.hufflepuff_banner", "Hufflepuff Banner");
        add("block.neo.floating_candle", "Floating Candle");
        add("block.neo.brass_cauldron", "Brass Cauldron");
        add("block.neo.wizarding_copper_cauldron", "Copper Cauldron");
        add("block.neo.pewter_cauldron", "Pewter Cauldron");
        add("block.neo.floo_grate", "Floo Grate");

        add("entity.neo.wizarding_thrown", "Thrown wizarding item");

        // Moving portraits (painting variants)
        add("painting.neo.portrait_fat_lady.title", "The Fat Lady");
        add("painting.neo.portrait_fat_lady.author", "Hogwarts");
        add("painting.neo.portrait_slytherin_common.title", "Slytherin Dungeon Portrait");
        add("painting.neo.portrait_slytherin_common.author", "Hogwarts");
        add("painting.neo.portrait_black_family.title", "The Black Family");
        add("painting.neo.portrait_black_family.author", "Unknown");
        add("painting.neo.portrait_sir_cadogan.title", "Sir Cadogan");
        add("painting.neo.portrait_sir_cadogan.author", "Hogwarts");
    }
}

