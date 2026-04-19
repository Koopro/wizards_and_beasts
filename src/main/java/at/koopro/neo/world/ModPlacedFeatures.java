package at.koopro.neo.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import at.koopro.neo.Neo;

public class ModPlacedFeatures {

    public static final ResourceKey<PlacedFeature> ELDER_TREE_KEY =
            ResourceKey.create(Registries.PLACED_FEATURE, Identifier.tryParse(Neo.MODID + ":elder_tree"));
    public static final ResourceKey<PlacedFeature> YEW_TREE_KEY =
            ResourceKey.create(Registries.PLACED_FEATURE, Identifier.tryParse(Neo.MODID + ":yew_tree"));
    public static final ResourceKey<PlacedFeature> HOLLY_TREE_KEY =
            ResourceKey.create(Registries.PLACED_FEATURE, Identifier.tryParse(Neo.MODID + ":holly_tree"));
    public static final ResourceKey<PlacedFeature> ROWAN_TREE_KEY =
            ResourceKey.create(Registries.PLACED_FEATURE, Identifier.tryParse(Neo.MODID + ":rowan_tree"));

    private ModPlacedFeatures() {
    }
}
