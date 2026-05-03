package at.koopro.wizardsandbeasts.datagen;

import at.koopro.wizardsandbeasts.type.profession.ProfessionNode;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;

public class ModLanguageProvider extends LanguageProvider {
    public ModLanguageProvider(PackOutput output) {
        super(output, WizardsAndBeastsMod.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        // Creative tab
        add("itemGroup." + WizardsAndBeastsMod.MODID + ".main", "Wizards & Beasts");

        // Debug tools
        add("item.wizards_and_beasts.debug_wand", "Debug Wand");
        add("item.wizards_and_beasts.morph_wand", "Morphologist's Wand");
        add("item.wizards_and_beasts.debug_wand.desc", "Developer testing wand for debug actions.");
        add("item.wizards_and_beasts.morph_wand.desc", "Prototype wand for morph and form testing.");

        // Elder
        add("block.wizards_and_beasts.elder_log", "Elder Log");
        add("block.wizards_and_beasts.stripped_elder_log", "Stripped Elder Log");
        add("block.wizards_and_beasts.elder_wood", "Elder Wood");
        add("block.wizards_and_beasts.stripped_elder_wood", "Stripped Elder Wood");
        add("block.wizards_and_beasts.elder_planks", "Elder Planks");
        add("block.wizards_and_beasts.elder_slab", "Elder Slab");
        add("block.wizards_and_beasts.elder_stairs", "Elder Stairs");
        add("block.wizards_and_beasts.elder_leaves", "Elder Leaves");
        add("block.wizards_and_beasts.elder_sapling", "Elder Sapling");

        // Yew
        add("block.wizards_and_beasts.yew_log", "Yew Log");
        add("block.wizards_and_beasts.stripped_yew_log", "Stripped Yew Log");
        add("block.wizards_and_beasts.yew_wood", "Yew Wood");
        add("block.wizards_and_beasts.stripped_yew_wood", "Stripped Yew Wood");
        add("block.wizards_and_beasts.yew_planks", "Yew Planks");
        add("block.wizards_and_beasts.yew_slab", "Yew Slab");
        add("block.wizards_and_beasts.yew_stairs", "Yew Stairs");
        add("block.wizards_and_beasts.yew_leaves", "Yew Leaves");
        add("block.wizards_and_beasts.yew_sapling", "Yew Sapling");

        // Holly
        add("block.wizards_and_beasts.holly_log", "Holly Log");
        add("block.wizards_and_beasts.stripped_holly_log", "Stripped Holly Log");
        add("block.wizards_and_beasts.holly_wood", "Holly Wood");
        add("block.wizards_and_beasts.stripped_holly_wood", "Stripped Holly Wood");
        add("block.wizards_and_beasts.holly_planks", "Holly Planks");
        add("block.wizards_and_beasts.holly_slab", "Holly Slab");
        add("block.wizards_and_beasts.holly_stairs", "Holly Stairs");
        add("block.wizards_and_beasts.holly_leaves", "Holly Leaves");
        add("block.wizards_and_beasts.holly_sapling", "Holly Sapling");

        // Wand
        add("item.wizards_and_beasts.wand", "Wand");
        add("item.wizards_and_beasts.wand.desc", "Channel and cast your selected spell.");
        add("item.wizards_and_beasts.phoenix_feather", "Phoenix Feather");
        add("item.wizards_and_beasts.phoenix_feather.desc", "Rare wand core material with bright magical affinity.");
        add("item.wizards_and_beasts.dragon_heartstring", "Dragon Heartstring");
        add("item.wizards_and_beasts.dragon_heartstring.desc", "Powerful wand core material with strong output.");
        add("item.wizards_and_beasts.unicorn_hair", "Unicorn Hair");
        add("item.wizards_and_beasts.unicorn_hair.desc", "Stable wand core material prized for consistency.");
        add("item.wizards_and_beasts.thestral_tail_hair", "Thestral Tail Hair");
        add("item.wizards_and_beasts.thestral_tail_hair.desc", "Unusual wand core tied to difficult magic.");
        add("item.wizards_and_beasts.veela_hair", "Veela Hair");
        add("item.wizards_and_beasts.veela_hair.desc", "Alluring but temperamental core known for swift casting.");
        add("item.wizards_and_beasts.troll_whisker", "Troll Whisker");
        add("item.wizards_and_beasts.troll_whisker.desc", "A stubborn core with brute force and rough control.");
        add("item.wizards_and_beasts.wampus_cat_hair", "Wampus Cat Hair");
        add("item.wizards_and_beasts.wampus_cat_hair.desc", "North American core linked to strong dueling focus.");
        add("item.wizards_and_beasts.thunderbird_tail_feather", "Thunderbird Tail Feather");
        add("item.wizards_and_beasts.thunderbird_tail_feather.desc", "Powerful long-range core with broad magical reach.");
        add("item.wizards_and_beasts.rougarou_hair", "Rougarou Hair");
        add("item.wizards_and_beasts.rougarou_hair.desc", "Wild and volatile core associated with difficult arts.");
        add("item.wizards_and_beasts.white_river_monster_spine", "White River Monster Spine");
        add("item.wizards_and_beasts.white_river_monster_spine.desc", "Rare spine core balancing reach and heavy spell output.");

        // Keybindings
        add("key.categories.wizards_and_beasts.spells", "Wizards & Beasts Spells");
        add("key.wizards_and_beasts.spell_up", "Spell Slot Up");
        add("key.wizards_and_beasts.spell_right", "Spell Slot Right");
        add("key.wizards_and_beasts.spell_down", "Spell Slot Down");
        add("key.wizards_and_beasts.spell_left", "Spell Slot Left");
        add("key.wizards_and_beasts.spell_menu", "Spell Menu");
        add("key.wizards_and_beasts.skill_menu", "Skill Tree");
        add("key.wizards_and_beasts.obscurial_toggle", "Toggle Obscurial Form");
        add("key.wizards_and_beasts.obscurial_stress_vent", "Obscurial Stress Vent");
        add("screen.wizards_and_beasts.skill_tree.title", "Skills");
        add("screen.wizards_and_beasts.skill_tree.points", "Skill Points");
        add("screen.wizards_and_beasts.skill_tree.hint", "Drag to pan, click node to unlock");
        add("screen.wizards_and_beasts.skill_tree.level", "Level");
        add("screen.wizards_and_beasts.skill_tree.cost", "Cost");
        add("screen.wizards_and_beasts.skill_tree.prereq", "Prereq");
        add("screen.wizards_and_beasts.skill_tree.prereq_met", "Met");
        add("screen.wizards_and_beasts.skill_tree.prereq_missing", "Missing");
        add("screen.wizards_and_beasts.skill_tree.click_unlock", "Click to unlock");
        add("screen.wizards_and_beasts.skill_tree.cannot_unlock", "Cannot unlock yet");
        add("screen.wizards_and_beasts.skill_tree.earned", "Earned");
        add("screen.wizards_and_beasts.skill_tree.spent", "Spent");
        add("screen.wizards_and_beasts.skill_tree.progress", "Tree");
        add("screen.wizards_and_beasts.skill_tree.need_points", "Need");
        add("screen.wizards_and_beasts.skill_tree.unlocked", "Unlocked");
        add("screen.wizards_and_beasts.skill_tree.maxed", "Maxed");
        add("screen.wizards_and_beasts.skill_access_denied.title", "Skill Access Denied");
        add("screen.wizards_and_beasts.skill_access_denied.hint", "This path is not available for your lineage.");
        add("screen.wizards_and_beasts.skill_access_denied.no_type", "Choose your type first.");
        add("screen.wizards_and_beasts.skill_access_denied.muggle_like", "Muggles and non-wand users cannot open this skill tree.");
        add("screen.wizards_and_beasts.skill_access_denied.generic_error", "Unable to open this screen for your current state.");
        add("screen.wizards_and_beasts.goblin_skills.title", "Goblin Mastery");
        add("screen.wizards_and_beasts.goblin_skills.placeholder", "Goblin-specific progression screen (coming next).");
        add("screen.wizards_and_beasts.goblin_skills.section_ledger", "Guild Ledger");
        add("screen.wizards_and_beasts.goblin_skills.hint", "Metals, contracts, and vault craft disciplines.");
        add("screen.wizards_and_beasts.elf_skills.title", "House-Elf Arts");
        add("screen.wizards_and_beasts.elf_skills.placeholder", "House-elf specific progression screen (coming next).");
        add("screen.wizards_and_beasts.elf_skills.section_threads", "Binding Threads");
        add("screen.wizards_and_beasts.elf_skills.hint", "Service, household wards, and quiet magic disciplines.");

        // Debug editor keybindings
        add("key.categories.wizards_and_beasts.debug", "Wizards & Beasts Debug");
        add("key.wizards_and_beasts.debug_toggle", "Toggle Model Debug Editor");
        add("key.wizards_and_beasts.debug_export", "Export Debug Transforms");
        add("key.wizards_and_beasts.debug_model_mode", "Switch Vanilla/GeckoLib Mode");

        // Marauder's Map items
        add("item.wizards_and_beasts.marauders_map", "Marauder's Map");
        add("item.wizards_and_beasts.marauders_map.desc", "A magical map that can track nearby movement.");
        add("item.wizards_and_beasts.parchment", "Parchment");
        add("item.wizards_and_beasts.parchment.desc", "Blank parchment used in wizarding crafting.");
        add("item.wizards_and_beasts.ink_bottle", "Ink Bottle");
        add("item.wizards_and_beasts.ink_bottle.desc", "A bottle of ink for writing magical notes.");
        add("item.wizards_and_beasts.broom", "Broom");
        add("item.wizards_and_beasts.broom.desc", "A flying broom for quick travel through the skies.");

        // Currency
        add("item.wizards_and_beasts.knut", "Knut");
        add("item.wizards_and_beasts.knut.desc", "A small bronze wizarding coin.");
        add("item.wizards_and_beasts.sickle", "Sickle");
        add("item.wizards_and_beasts.sickle.desc", "A silver wizarding coin of medium value.");
        add("item.wizards_and_beasts.galleon", "Galleon");
        add("item.wizards_and_beasts.galleon.desc", "A gold wizarding coin of high value.");
        add("item.wizards_and_beasts.leprechaun_gold", "Leprechaun Gold");
        add("item.wizards_and_beasts.leprechaun_gold.desc", "Shimmering coinage that fades after a short while.");
        add("item.wizards_and_beasts.dragot", "Dragot");
        add("item.wizards_and_beasts.dragot.desc", "An alternative wizarding currency for regional trade.");

        // Entities
        add("entity.wizards_and_beasts.goblin_teller", "Goblin Teller");
        add("entity.wizards_and_beasts.niffler", "Niffler");
        add("entity.wizards_and_beasts.form_mannequin", "Form Mannequin");
        add("item.wizards_and_beasts.goblin_teller_spawn_egg", "Goblin Teller Spawn Egg");
        add("item.wizards_and_beasts.niffler_spawn_egg", "Niffler Spawn Egg");
        add("item.wizards_and_beasts.goblin_teller_spawn_egg.desc", "Spawns a Goblin Teller for testing and encounters.");
        add("item.wizards_and_beasts.niffler_spawn_egg.desc", "Spawns a mischievous Niffler.");

        // Rowan
        add("block.wizards_and_beasts.rowan_log", "Rowan Log");
        add("block.wizards_and_beasts.stripped_rowan_log", "Stripped Rowan Log");
        add("block.wizards_and_beasts.rowan_wood", "Rowan Wood");
        add("block.wizards_and_beasts.stripped_rowan_wood", "Stripped Rowan Wood");
        add("block.wizards_and_beasts.rowan_planks", "Rowan Planks");
        add("block.wizards_and_beasts.rowan_slab", "Rowan Slab");
        add("block.wizards_and_beasts.rowan_stairs", "Rowan Stairs");
        add("block.wizards_and_beasts.rowan_leaves", "Rowan Leaves");
        add("block.wizards_and_beasts.rowan_sapling", "Rowan Sapling");

        // Wizarding World — items
        add("item.wizards_and_beasts.brew", "Brew");
        add("item.wizards_and_beasts.brew.desc", "A bottled brew infused with custom potion effects.");
        add("item.wizards_and_beasts.butterbeer", "Butterbeer");
        add("item.wizards_and_beasts.butterbeer.desc", "Sweet wizarding drink with a mild restorative kick.");
        add("item.wizards_and_beasts.pumpkin_juice", "Pumpkin Juice");
        add("item.wizards_and_beasts.pumpkin_juice.desc", "A hearty pumpkin drink to restore hunger.");
        add("item.wizards_and_beasts.chocolate_frog", "Chocolate Frog");
        add("item.wizards_and_beasts.chocolate_frog.desc", "A jumping confection loved by young witches and wizards.");
        add("item.wizards_and_beasts.famous_wizard_card", "Famous Wizard Card");
        add("item.wizards_and_beasts.famous_wizard_card.desc", "Collectible card featuring famous magical figures.");
        add("item.wizards_and_beasts.famous_wizard_card.blank", "Blank card");
        add("item.wizards_and_beasts.famous_wizard_card.variant.albus_dumbledore", "Albus Dumbledore");
        add("item.wizards_and_beasts.famous_wizard_card.variant.harry_potter", "Harry Potter");
        add("item.wizards_and_beasts.famous_wizard_card.variant.gilderoy_lockhart", "Gilderoy Lockhart");
        add("item.wizards_and_beasts.famous_wizard_card.variant.merlin", "Merlin");
        add("item.wizards_and_beasts.famous_wizard_card.variant.circe", "Circe");
        add("item.wizards_and_beasts.famous_wizard_card.variant.paracelsus", "Paracelsus");
        add("item.wizards_and_beasts.famous_wizard_card.variant.cliodna", "Cliodna");
        add("item.wizards_and_beasts.famous_wizard_card.variant.morgana_le_fay", "Morgana le Fay");
        add("item.wizards_and_beasts.bertie_botts_every_flavour_beans", "Bertie Bott's Every Flavour Beans");
        add("item.wizards_and_beasts.bertie_botts_every_flavour_beans.desc", "Candy beans with wildly unpredictable flavors.");
        add("item.wizards_and_beasts.droobles_best_blowing_gum", "Drooble's Best Blowing Gum");
        add("item.wizards_and_beasts.droobles_best_blowing_gum.desc", "Bubble gum that feels strangely buoyant.");
        add("item.wizards_and_beasts.firewhisky", "Firewhisky");
        add("item.wizards_and_beasts.firewhisky.desc", "Strong drink with power and side effects.");
        add("item.wizards_and_beasts.gillyweed", "Gillyweed");
        add("item.wizards_and_beasts.gillyweed.desc", "Aquatic herb that helps breathing underwater.");
        add("item.wizards_and_beasts.dirigible_plum", "Dirigible Plum");
        add("item.wizards_and_beasts.dirigible_plum.desc", "Odd fruit that can make you feel lighter.");
        add("item.wizards_and_beasts.treacle_tart", "Treacle Tart");
        add("item.wizards_and_beasts.treacle_tart.desc", "Sticky tart favored as a hearty wizarding dessert.");
        add("item.wizards_and_beasts.pumpkin_pasty", "Pumpkin Pasty");
        add("item.wizards_and_beasts.pumpkin_pasty.desc", "Warm pastry packed with sweetened pumpkin filling.");
        add("item.wizards_and_beasts.fizzing_whizzbee", "Fizzing Whizzbee");
        add("item.wizards_and_beasts.fizzing_whizzbee.desc", "Honey candy that gives you a light spring in your step.");
        add("item.wizards_and_beasts.peppermint_toad", "Peppermint Toad");
        add("item.wizards_and_beasts.peppermint_toad.desc", "Minty chocolate sweet that leaves you feeling quick on your feet.");
        add("item.wizards_and_beasts.dittany", "Dittany");
        add("item.wizards_and_beasts.dittany.desc", "Healing herb used in restorative remedies.");
        add("item.wizards_and_beasts.occamy_eggshell", "Occamy Eggshell");
        add("item.wizards_and_beasts.occamy_eggshell.desc", "Rare alchemical material from magical creatures.");
        add("item.wizards_and_beasts.bezoar", "Bezoar");
        add("item.wizards_and_beasts.bezoar.desc", "A powerful antidote against many poisons.");
        add("item.wizards_and_beasts.demiguise_hair", "Demiguise Hair");
        add("item.wizards_and_beasts.demiguise_hair.desc", "Elusive material used in advanced wizarding craft.");
        add("item.wizards_and_beasts.mooncalf_dung", "Mooncalf Dung");
        add("item.wizards_and_beasts.mooncalf_dung.desc", "Useful fertilizer for magical plants.");
        add("item.wizards_and_beasts.erumpent_horn", "Erumpent Horn");
        add("item.wizards_and_beasts.erumpent_horn.desc", "Volatile horn fragment used as a throwable item.");
        add("item.wizards_and_beasts.mandrake", "Mandrake");
        add("item.wizards_and_beasts.mandrake.desc", "Magical plant ingredient with potent properties.");
        add("item.wizards_and_beasts.mandrake_seeds", "Mandrake Seeds");
        add("item.wizards_and_beasts.remembrall", "Remembrall");
        add("item.wizards_and_beasts.remembrall.desc", "A glass orb that helps recall forgotten things.");
        add("item.wizards_and_beasts.omnioculars", "Omnioculars");
        add("item.wizards_and_beasts.omnioculars.desc", "Magical binoculars for enhanced long-range viewing.");
        add("item.wizards_and_beasts.deluminator", "Deluminator");
        add("item.wizards_and_beasts.deluminator.desc", "Device that manipulates nearby light sources.");
        add("item.wizards_and_beasts.deluminator.stored_lights", "Stored Lights: %s/%s");
        add("item.wizards_and_beasts.time_turner", "Time-Turner");
        add("item.wizards_and_beasts.time_turner.desc", "Experimental training artifact: channels temporal magic and nudges world time forward.");
        add("item.wizards_and_beasts.sneakoscope", "Sneakoscope");
        add("item.wizards_and_beasts.sneakoscope.desc", "Spins and warns when trouble is nearby.");
        add("item.wizards_and_beasts.portkey", "Portkey");
        add("item.wizards_and_beasts.portkey.desc", "Linked transport item. Sneak-use on a block to bind destination.");
        add("item.wizards_and_beasts.portkey.unlinked", "Sneak-use on a block to link a destination.");
        add("item.wizards_and_beasts.portkey.linked", "Linked: %s, %s, %s");
        add("item.wizards_and_beasts.invisibility_cloak", "Invisibility Cloak");
        add("item.wizards_and_beasts.invisibility_cloak.desc", "Wear in chest slot to blend from sight.");
        add("item.wizards_and_beasts.deathly_hallow_cloak", "Deathly Hallow Cloak");
        add("item.wizards_and_beasts.deathly_hallow_cloak.desc", "Legendary cloak that completely hides you from other players.");
        add("item.wizards_and_beasts.peruvian_instant_darkness_powder", "Peruvian Instant Darkness Powder");
        add("item.wizards_and_beasts.peruvian_instant_darkness_powder.desc", "Throwable powder that creates sudden visual disruption.");
        add("item.wizards_and_beasts.decoy_detonator", "Decoy Detonator");
        add("item.wizards_and_beasts.decoy_detonator.desc", "Thrown prank tool that distracts nearby targets.");
        add("item.wizards_and_beasts.extendable_ears", "Extendable Ears");
        add("item.wizards_and_beasts.extendable_ears.desc", "Magical ears used for sneaky long-range listening.");
        add("item.wizards_and_beasts.floo_powder", "Floo Powder");
        add("item.wizards_and_beasts.floo_powder.desc", "Teleportation powder used with connected Floo networks.");

        // Wizarding World — blocks
        add("block.wizards_and_beasts.devils_snare", "Devil's Snare");
        add("block.wizards_and_beasts.mandrake_crop", "Mandrake");
        add("block.wizards_and_beasts.mallowsweet", "Mallowsweet");
        add("block.wizards_and_beasts.gryffindor_banner", "Gryffindor Banner");
        add("block.wizards_and_beasts.slytherin_banner", "Slytherin Banner");
        add("block.wizards_and_beasts.ravenclaw_banner", "Ravenclaw Banner");
        add("block.wizards_and_beasts.hufflepuff_banner", "Hufflepuff Banner");
        add("block.wizards_and_beasts.floating_candle", "Floating Candle");
        add("block.wizards_and_beasts.brass_cauldron", "Brass Cauldron");
        add("block.wizards_and_beasts.wizarding_copper_cauldron", "Copper Cauldron");
        add("block.wizards_and_beasts.pewter_cauldron", "Pewter Cauldron");
        add("block.wizards_and_beasts.floo_grate", "Floo Grate");
        add("block.wizards_and_beasts.spell_teacher", "Spell Teacher");
        add("block.wizards_and_beasts.unlit_torch", "Unlit Torch");
        add("block.wizards_and_beasts.unlit_wall_torch", "Unlit Wall Torch");
        add("block.wizards_and_beasts.unlit_copper_torch", "Unlit Copper Torch");
        add("block.wizards_and_beasts.unlit_copper_wall_torch", "Unlit Copper Wall Torch");
        add("block.wizards_and_beasts.unlit_soul_torch", "Unlit Soul Torch");
        add("block.wizards_and_beasts.unlit_soul_wall_torch", "Unlit Soul Wall Torch");
        add("block.wizards_and_beasts.unlit_lantern", "Unlit Lantern");
        add("block.wizards_and_beasts.unlit_copper_lantern", "Unlit Copper Lantern");
        add("block.wizards_and_beasts.unlit_soul_lantern", "Unlit Soul Lantern");
        add("block.wizards_and_beasts.unlit_glowstone", "Unlit Glowstone");

        add("entity.wizards_and_beasts.wizarding_thrown", "Thrown wizarding item");
        add("subtitles.wizards_and_beasts.broom_crash", "Broom crashes");
        add("ui.wizards_and_beasts.type_selection.coming_soon", "Coming Soon (Alpha)");
        add("message.wizards_and_beasts.type_selection.coming_soon", "\u00A7cThis type is coming soon in alpha.");

        // Moving portraits (painting variants)
        add("painting.wizards_and_beasts.portrait_fat_lady.title", "The Fat Lady");
        add("painting.wizards_and_beasts.portrait_fat_lady.author", "Hogwarts");
        add("painting.wizards_and_beasts.portrait_gryffindor_common.title", "Gryffindor Common Room Portrait");
        add("painting.wizards_and_beasts.portrait_gryffindor_common.author", "Hogwarts");
        add("painting.wizards_and_beasts.portrait_slytherin_common.title", "Slytherin Dungeon Portrait");
        add("painting.wizards_and_beasts.portrait_slytherin_common.author", "Hogwarts");
        add("painting.wizards_and_beasts.portrait_ravenclaw_common.title", "Ravenclaw Tower Portrait");
        add("painting.wizards_and_beasts.portrait_ravenclaw_common.author", "Hogwarts");
        add("painting.wizards_and_beasts.portrait_hufflepuff_common.title", "Hufflepuff Cellar Portrait");
        add("painting.wizards_and_beasts.portrait_hufflepuff_common.author", "Hogwarts");
        add("painting.wizards_and_beasts.portrait_black_family.title", "The Black Family");
        add("painting.wizards_and_beasts.portrait_black_family.author", "Unknown");
        add("painting.wizards_and_beasts.portrait_sir_cadogan.title", "Sir Cadogan");
        add("painting.wizards_and_beasts.portrait_sir_cadogan.author", "Hogwarts");

        // Profession tree
        for (ProfessionNode node : ProfessionNode.values()) {
            add(node.getTranslationKey(), node.getDisplayName());
            add(node.getDescriptionTranslationKey(), node.getDescription());
        }
        add("screen.wizards_and_beasts.profession_tree.title", "Professions");
        add("screen.wizards_and_beasts.profession_tree.points", "Profession Points");
    }
}

