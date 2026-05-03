package at.koopro.wizardsandbeasts;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import at.koopro.wizardsandbeasts.brew.def.BrewReloadListener;
import at.koopro.wizardsandbeasts.brew.def.BrewingRecipeReloadListener;
import at.koopro.wizardsandbeasts.event.RegisterBrewsEvent;
import at.koopro.wizardsandbeasts.event.RegisterSpellsEvent;
import at.koopro.wizardsandbeasts.network.ModNetwork;
import at.koopro.wizardsandbeasts.registry.EntityAttributes;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import at.koopro.wizardsandbeasts.spell.Spells;
import at.koopro.wizardsandbeasts.spell.def.SpellReloadListener;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.ModCreativeTabs;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.registry.ModFeatures;
import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import at.koopro.wizardsandbeasts.registry.ModItems;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.wand.WandAttachments;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.recipe.WandmakingRecipeSerializer;
import at.koopro.wizardsandbeasts.wand.recipe.WandmakingRecipeType;
import at.koopro.wizardsandbeasts.wand.registry.WandDatapackRegistries;

// The value here should match the entry in META-INF/neoforge.mods.toml
@Mod(WizardsAndBeastsMod.MODID)
public class WizardsAndBeastsMod {
    public static final String MODID = "wizards_and_beasts";

    /** Debug flag: force beam rendering without holding right-click. */
    public static volatile boolean debugForceBeam = false;

    private static final Logger LOGGER = LogUtils.getLogger();

    public WizardsAndBeastsMod(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        ModFeatures.FEATURES.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        ModAttachments.ATTACHMENTS.register(modEventBus);
        WandAttachments.ATTACHMENTS.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        WandComponents.DATA_COMPONENTS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        WandmakingRecipeType.RECIPE_TYPES.register(modEventBus);
        WandmakingRecipeSerializer.RECIPE_SERIALIZERS.register(modEventBus);

        modEventBus.addListener(ModNetwork::register);
        modEventBus.addListener(EntityAttributes::registerAll);
        modEventBus.addListener(WandDatapackRegistries::registerDatapackRegistries);

        // Defer Spells.init() until FMLCommonSetupEvent so addon mods get a
        // chance to register Java spells via RegisterSpellsEvent before the
        // two-phase init resolves cross-references. The brew registration
        // event is fired here too so addon brews are available before any
        // server starts a reload.
        modEventBus.addListener((FMLCommonSetupEvent event) -> {
            modEventBus.post(new RegisterSpellsEvent());
            Spells.init();
            modEventBus.post(new RegisterBrewsEvent());
        });

        // Datapack-driven JSON content: spells, brews, and brewing recipes
        // all load on every server resource reload. Order doesn't matter
        // for correctness (cross-references are resolved by id at use time),
        // but recipe lookup needs the brew registry populated, so the
        // brew listener is added before the recipe listener.
        NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent event) -> {
            event.addListener(
                    Identifier.fromNamespaceAndPath(MODID, "spell_reload_listener"),
                    new SpellReloadListener());
            event.addListener(
                    Identifier.fromNamespaceAndPath(MODID, "brew_reload_listener"),
                    new BrewReloadListener());
            event.addListener(
                    Identifier.fromNamespaceAndPath(MODID, "brewing_recipe_reload_listener"),
                    new BrewingRecipeReloadListener());
        });

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        SkillTrees.init();

        LOGGER.info("Initializing Wizards & Beasts mod.");
    }

}

