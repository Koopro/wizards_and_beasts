package at.koopro.neo.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import at.koopro.neo.Neo;
import at.koopro.neo.world.tree.ElderTreeFeature;
import at.koopro.neo.world.tree.HollyTreeFeature;
import at.koopro.neo.world.tree.RowanTreeFeature;
import at.koopro.neo.world.tree.YewTreeFeature;

public class ModFeatures {

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, Neo.MODID);

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
