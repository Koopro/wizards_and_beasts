package at.koopro.wizardsandbeasts.module.condition;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModConditions {
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, WizardsAndBeastsMod.MODID);

    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<ModuleEnabledCondition>> MODULE_ENABLED =
            CONDITION_CODECS.register("module_enabled", () -> ModuleEnabledCondition.CODEC);

    private ModConditions() {
    }
}
