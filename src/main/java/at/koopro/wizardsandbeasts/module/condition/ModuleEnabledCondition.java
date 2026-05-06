package at.koopro.wizardsandbeasts.module.condition;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.common.conditions.ICondition;

public record ModuleEnabledCondition(Module module) implements ICondition {
    public static final MapCodec<ModuleEnabledCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.xmap(Module::valueOf, Module::name).fieldOf("module").forGetter(ModuleEnabledCondition::module)
    ).apply(instance, ModuleEnabledCondition::new));

    @Override
    public boolean test(IContext context) {
        return ModuleManager.isEnabled(module);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
