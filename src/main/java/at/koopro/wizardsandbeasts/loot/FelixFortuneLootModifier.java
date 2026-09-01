package at.koopro.wizardsandbeasts.loot;

import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.felix.FelixFortune;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import org.jspecify.annotations.NullMarked;


/**
 * A chest that rolled badly is sometimes rolled again.
 *
 * <h2>What "poor" means</h2>
 * <p>{@link #POOR_STACK_COUNT} stacks or fewer. Deliberately a count and not a value judgement: the
 * mod has no notion of what an item is worth, and inventing one would mean a table of prices that
 * every modpack would immediately disagree with. "The chest was nearly empty" is a thing every player
 * recognises and no datapack has to configure.
 *
 * <p>The re-roll <b>replaces</b> the poor result rather than adding to it. Adding would make Felix
 * "sometimes two chests", which is a loot multiplier; replacing makes it "that chest was better than
 * it looked", which is luck.
 *
 * <h2>Re-entrancy</h2>
 * <p>Rolling the table again runs the whole global-loot-modifier chain again, including this one. A
 * {@link ThreadLocal} guard stops the second roll from re-rolling — without it a run of unlucky rolls
 * recurses until the stack gives out, and the crash would be miles from the cause. Thread-local
 * rather than a field because loot generation is not promised to be single-threaded.
 *
 * <h2>Ordering</h2>
 * <p>Must be listed <b>before</b> {@code module_gated} in {@code global_loot_modifiers.json}, for the
 * same reason {@code bestiary_harvest} is: that modifier strips stacks belonging to disabled modules
 * and can only strip what is already there.
 */
@NullMarked
public class FelixFortuneLootModifier extends LootModifier {

    public static final MapCodec<FelixFortuneLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).apply(instance, FelixFortuneLootModifier::new));

    /** At or below this many stacks, a chest counts as a disappointment. */
    private static final int POOR_STACK_COUNT = 2;

    /** True while this modifier is already re-rolling, on this thread. */
    private static final ThreadLocal<Boolean> REROLLING = ThreadLocal.withInitial(() -> false);

    public FelixFortuneLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot,
                                                 LootContext context) {
        if (REROLLING.get()) {
            return generatedLoot;
        }
        if (generatedLoot.size() > POOR_STACK_COUNT) {
            return generatedLoot;
        }
        Entity looter = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
        if (!(looter instanceof ServerPlayer player)) {
            return generatedLoot;
        }
        if (!FelixFortune.rollLootReroll(player)) {
            return generatedLoot;
        }

        Identifier tableId = context.getQueriedLootTableId();
        ServerLevel level = context.getLevel();
        if (level.getServer() == null) {
            return generatedLoot;
        }
        LootTable table = level.getServer().reloadableRegistries().getLootTable(
                net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.LOOT_TABLE, tableId));
        if (table == LootTable.EMPTY) {
            return generatedLoot;
        }

        ObjectArrayList<ItemStack> second = new ObjectArrayList<>();
        REROLLING.set(true);
        try {
            // The Consumer overload, not the ObjectArrayList one: the list-returning variants take
            // LootParams and would build a fresh context, losing the luck, the looter and the seed
            // that this roll is supposed to be a second attempt at.
            table.getRandomItems(context, second::add);
        } finally {
            // Cleared rather than set false: a thread that will never roll loot again should not keep
            // a map entry alive for the life of the server.
            REROLLING.remove();
        }

        if (second.isEmpty()) {
            return generatedLoot;
        }

        FelixFortune.announce(player, "felix.wizards_and_beasts.second_look", NoticeKind.SUCCESS);
        FelixFortune.sparkle(level, player, 12);
        return second;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
