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

    private ModConfiguredFeatures() {
    }
}
