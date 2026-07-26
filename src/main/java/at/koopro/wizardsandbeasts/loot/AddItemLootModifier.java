package at.koopro.wizardsandbeasts.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import org.jspecify.annotations.NullMarked;

/**
 * Adds one stack of {@code item} to any loot table the modifier's conditions match.
 *
 * <p>Exists because several wizarding items are defined by lore as things you <em>find</em> — a bezoar
 * pulled from a goat's stomach, coins in a forgotten cache — and a mod loot table can only describe drops
 * from the mod's own blocks and mobs. A datapack override of a vanilla table would fight every other mod
 * that touches it; a global loot modifier is additive.
 *
 * <p>Nothing here is hard-coded: item, count range and chance all come from the JSON, so a pack can
 * retune or remove any of these drops.
 */
@NullMarked
public class AddItemLootModifier extends LootModifier {

    public static final MapCodec<AddItemLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).and(instance.group(
                    BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(m -> m.item),
                    Codec.INT.optionalFieldOf("min_count", 1).forGetter(m -> m.minCount),
                    Codec.INT.optionalFieldOf("max_count", 1).forGetter(m -> m.maxCount),
                    Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(m -> m.chance)
            )).apply(instance, AddItemLootModifier::new));

    private final Item item;
    private final int minCount;
    private final int maxCount;
    private final float chance;

    public AddItemLootModifier(LootItemCondition[] conditions, Item item, int minCount, int maxCount, float chance) {
        super(conditions);
        this.item = item;
        this.minCount = Math.max(1, minCount);
        this.maxCount = Math.max(this.minCount, maxCount);
        this.chance = chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (chance < 1.0f && context.getRandom().nextFloat() >= chance) {
            return generatedLoot;
        }
        int count = minCount == maxCount
                ? minCount
                : minCount + context.getRandom().nextInt(maxCount - minCount + 1);
        generatedLoot.add(new ItemStack(item, count));
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
