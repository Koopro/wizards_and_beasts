package at.koopro.wizardsandbeasts.datagen;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public class ModDataGenerators {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent.Client event) {
        event.createProvider(ModModelProvider::new);
        event.createProvider(ModLanguageProvider::new);

        event.createBlockAndItemTags(ModBlockTagsProvider::new, ModItemTagsProvider::new);
        event.createProvider(ModEntityTypeTagsProvider::new);

        event.createProvider(ModLootTableProvider::create);
        event.createProvider(ModRecipeProvider.Runner::new);
    }
}
