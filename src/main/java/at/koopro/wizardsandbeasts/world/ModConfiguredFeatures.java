package at.koopro.wizardsandbeasts.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;

public class ModConfiguredFeatures {

    public static final ResourceKey<ConfiguredFeature<?, ?>> ELDER_TREE_KEY =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, Identifier.tryParse(WizardsAndBeastsMod.MODID + ":elder_tree"));
    public static final ResourceKey<ConfiguredFeature<?, ?>> YEW_TREE_KEY =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, Identifier.tryParse(WizardsAndBeastsMod.MODID + ":yew_tree"));
    public static final ResourceKey<ConfiguredFeature<?, ?>> HOLLY_TREE_KEY =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, Identifier.tryParse(WizardsAndBeastsMod.MODID + ":holly_tree"));
    public static final ResourceKey<ConfiguredFeature<?, ?>> ROWAN_TREE_KEY =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, Identifier.tryParse(WizardsAndBeastsMod.MODID + ":rowan_tree"));

    // New species use fromNamespaceAndPath rather than the tryParse above: tryParse is nullable and
    // feeds straight into ResourceKey.create, so a malformed id NPEs at class-init instead of failing
    // legibly. Converting the four existing keys is a separate mechanical pass (AUDIT_PUNCHLIST).
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
