package at.koopro.wizardsandbeasts.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;

public class ModConfiguredFeatures {

    public static final ResourceKey<ConfiguredFeature<?, ?>> ELDER_TREE_KEY =
            key("elder_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> YEW_TREE_KEY =
            key("yew_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> HOLLY_TREE_KEY =
            key("holly_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> ROWAN_TREE_KEY =
            key("rowan_tree");

    public static final ResourceKey<ConfiguredFeature<?, ?>> ASH_TREE_KEY = key("ash_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> BLACKTHORN_TREE_KEY = key("blackthorn_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> HAWTHORN_TREE_KEY = key("hawthorn_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> WALNUT_TREE_KEY = key("walnut_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> WILLOW_TREE_KEY = key("willow_tree");

    private static ResourceKey<ConfiguredFeature<?, ?>> key(String path) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE,
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path));
    }

    private ModConfiguredFeatures() {
    }
}
