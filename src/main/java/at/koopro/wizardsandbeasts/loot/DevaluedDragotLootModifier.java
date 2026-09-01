package at.koopro.wizardsandbeasts.loot;

import at.koopro.wizardsandbeasts.currency.dragot.DragotPurse;
import at.koopro.wizardsandbeasts.currency.dragot.DragotRates;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.LootModifier;
import org.jspecify.annotations.NullMarked;

/**
 * Stamps a fraction of the Dragots coming out of any loot table as devalued.
 *
 * <p>Unlike {@link AddItemLootModifier} this adds nothing — it <em>rewrites</em> what a table already
 * produced. That is the only way to make bad money feel like bad money: the coin has to arrive mixed
 * in with the good ones, from the same chest, in the same handful. A separate "devalued dragot" item
 * in its own loot pool would be visible before it was ever spent.
 *
 * <p><b>Rolled per coin, not per stack.</b> A stack of forty Dragots is forty chances at
 * {@link DragotRates#DEVALUED_LOOT_CHANCE}, and the bad ones are split off into their own stack —
 * which they would do on their own anyway, since a differing data component prevents merging. Rolling
 * per stack instead would make a big haul no likelier to contain a dud than a single coin, and would
 * spoil an entire stack when it hit.
 */
@NullMarked
public class DevaluedDragotLootModifier extends LootModifier {

    public static final MapCodec<DevaluedDragotLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).and(
                    Codec.FLOAT.optionalFieldOf("chance", DragotRates.DEVALUED_LOOT_CHANCE)
                            .forGetter(m -> m.chance)
            ).apply(instance, DevaluedDragotLootModifier::new));

    private final float chance;

    public DevaluedDragotLootModifier(LootItemCondition[] conditions, float chance) {
        super(conditions);
        this.chance = Math.max(0.0f, Math.min(1.0f, chance));
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (chance <= 0.0f) {
            return generatedLoot;
        }
        RandomSource random = context.getRandom();
        ObjectArrayList<ItemStack> spoiled = null;

        for (ItemStack stack : generatedLoot) {
            if (!DragotPurse.isDragot(stack) || DragotPurse.isDevalued(stack)) {
                continue;
            }
            int bad = 0;
            for (int coin = 0; coin < stack.getCount(); coin++) {
                if (random.nextFloat() < chance) {
                    bad++;
                }
            }
            if (bad == 0) {
                continue;
            }
            // Split rather than mutate: the good remainder must stay a good stack, and the whole point
            // is that the two do not merge back together.
            ItemStack devalued = stack.copyWithCount(bad);
            DragotPurse.devalue(devalued);
            stack.shrink(bad);
            if (spoiled == null) {
                spoiled = new ObjectArrayList<>();
            }
            spoiled.add(devalued);
        }

        if (spoiled != null) {
            // A stack shrunk to nothing is dropped here rather than left as an empty entry, which some
            // loot consumers would happily hand to a player as an invisible item.
            generatedLoot.removeIf(ItemStack::isEmpty);
            generatedLoot.addAll(spoiled);
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends net.neoforged.neoforge.common.loot.IGlobalLootModifier> codec() {
        return CODEC;
    }
}
