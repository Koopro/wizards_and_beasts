package at.koopro.wizardsandbeasts.client.furniture;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.WoodFittings;
import net.minecraft.client.renderer.Sheets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** The wand woods' sign boards: each WoodType's sign and hanging-sign sprites join the sign sheet's materials. */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class WoodTypeSheets {

    private WoodTypeSheets() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> WoodFittings.WOODS.forEach(fittings -> Sheets.addWoodType(fittings.type())));
    }
}
