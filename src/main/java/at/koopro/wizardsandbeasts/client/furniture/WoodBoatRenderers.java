package at.koopro.wizardsandbeasts.client.furniture;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.WoodBoats;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.object.boat.BoatModel;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The wand woods' boats through vanilla's boat renderer. The renderer reads its texture from the layer's id, so the
 * layer {@code wizards_and_beasts:boat/elder} draws {@code textures/entity/boat/elder.png}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class WoodBoatRenderers {

    private WoodBoatRenderers() {}

    static ModelLayerLocation layer(String kind, String wood) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, kind + "/" + wood), "main");
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (WoodBoats.Boats boats : WoodBoats.ALL) {
            event.registerLayerDefinition(layer("boat", boats.wood()), BoatModel::createBoatModel);
            event.registerLayerDefinition(layer("chest_boat", boats.wood()), BoatModel::createChestBoatModel);
        }
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        for (WoodBoats.Boats boats : WoodBoats.ALL) {
            event.registerEntityRenderer(boats.boat().get(), context -> new BoatRenderer(context, layer("boat", boats.wood())));
            event.registerEntityRenderer(boats.chestBoat().get(),
                    context -> new BoatRenderer(context, layer("chest_boat", boats.wood())));
        }
    }
}
