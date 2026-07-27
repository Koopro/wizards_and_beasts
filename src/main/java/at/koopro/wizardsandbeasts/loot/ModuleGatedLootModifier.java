package at.koopro.wizardsandbeasts.loot;

import at.koopro.wizardsandbeasts.module.ModuleContentIndex;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import org.jspecify.annotations.NullMarked;

/**
 * Drops any rolled stack whose module is switched off, from every loot table in the game.
 *
 * <p>One modifier rather than a condition on ~300 tables: loot is the one acquisition route that reaches
 * beyond our own files — vanilla chests carry our coins through {@link AddItemLootModifier}, and other
 * mods' tables may too. Filtering the rolled result catches all of them, and a table we have never heard
 * of needs no edit.
 *
 * <p><b>Block drops are exempt, and that is the whole point of the exemption.</b> A table rolled with a
 * {@code BLOCK_STATE} is someone breaking a placed block, and swallowing that drop would destroy content
 * already in the world — a Gringotts wall mined after an operator switched a module off would simply
 * vanish. Turning a module off stops content being <em>obtained</em>; it never takes away what a player
 * already built or carries.
 */
@NullMarked
public class ModuleGatedLootModifier extends LootModifier {

    public static final MapCodec<ModuleGatedLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).apply(instance, ModuleGatedLootModifier::new));

    public ModuleGatedLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (context.hasParameter(LootContextParams.BLOCK_STATE)) {
            return generatedLoot;
        }
        generatedLoot.removeIf(stack -> !ModuleContentIndex.isAccessible(stack.getItem()));
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
