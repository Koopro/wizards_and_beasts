package at.koopro.wizardsandbeasts.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;

public class ModPlacedFeatures {

    public static final ResourceKey<PlacedFeature> ELDER_TREE_KEY =
            key("elder_tree");
    public static final ResourceKey<PlacedFeature> YEW_TREE_KEY =
            key("yew_tree");
    public static final ResourceKey<PlacedFeature> HOLLY_TREE_KEY =
            key("holly_tree");
    public static final ResourceKey<PlacedFeature> ROWAN_TREE_KEY =
            key("rowan_tree");

    private static ResourceKey<PlacedFeature> key(String path) {
        return ResourceKey.create(Registries.PLACED_FEATURE,
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path));
    }

    private ModPlacedFeatures() {
    }
}
