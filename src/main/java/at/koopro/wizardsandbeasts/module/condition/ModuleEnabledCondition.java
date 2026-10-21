package at.koopro.wizardsandbeasts.module.condition;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.common.conditions.ICondition;

public record ModuleEnabledCondition(Module module) implements ICondition {
    public static final MapCodec<ModuleEnabledCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.comapFlatMap(ModuleEnabledCondition::parse, Module::name).fieldOf("module").forGetter(ModuleEnabledCondition::module)
    ).apply(instance, ModuleEnabledCondition::new));

    /** A typo'd module name is a codec error on that one recipe, not an exception out of the datapack load. */
    public static DataResult<Module> parse(String name) {
        try {
            return DataResult.success(Module.valueOf(name));
        } catch (IllegalArgumentException unknown) {
            return DataResult.error(() -> "Unknown module: " + name);
        }
    }

    @Override
    public boolean test(IContext context) {
        return ModuleManager.isEnabled(module);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
