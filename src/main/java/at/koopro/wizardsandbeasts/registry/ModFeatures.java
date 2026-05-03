package at.koopro.wizardsandbeasts.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.world.tree.ElderTreeFeature;
import at.koopro.wizardsandbeasts.world.tree.HollyTreeFeature;
import at.koopro.wizardsandbeasts.world.tree.RowanTreeFeature;
import at.koopro.wizardsandbeasts.world.tree.YewTreeFeature;

public class ModFeatures {

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, WizardsAndBeastsMod.MODID);

    public static final DeferredHolder<Feature<?>, ElderTreeFeature> ELDER_TREE =
            FEATURES.register("elder_tree", () -> new ElderTreeFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, YewTreeFeature> YEW_TREE =
            FEATURES.register("yew_tree", () -> new YewTreeFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, HollyTreeFeature> HOLLY_TREE =
            FEATURES.register("holly_tree", () -> new HollyTreeFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, RowanTreeFeature> ROWAN_TREE =
            FEATURES.register("rowan_tree", () -> new RowanTreeFeature(NoneFeatureConfiguration.CODEC));

    private ModFeatures() {
    }
}
