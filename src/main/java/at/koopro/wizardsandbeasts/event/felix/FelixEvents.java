package at.koopro.wizardsandbeasts.event.felix;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.felix.FelixFortune;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Where luck actually touches the world: the clock, the near miss, and the generous ore.
 *
 * <p>The chest re-roll lives in {@code FelixFortuneLootModifier} instead, because loot generation is
 * not an event — it is a table being rolled, and the only place to get between a player and a bad
 * roll is a global loot modifier.
 *
 * <p>Every handler here returns immediately for a player who is not lucky, which is all of them
 * almost all of the time. The state read is a single attachment lookup, so the cost of Felix existing
 * for a server where nobody has drunk any is one field read per player tick.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class FelixEvents {

    private FelixEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FelixFortune.tick(player);
        }
    }

    /**
     * The hit that happens to miss.
     *
     * <p>Cancelled rather than reduced. A reduced hit still knocks you back, still plays the hurt
     * animation, and still reads as "I took damage" — the fiction here is that the blow did not land,
     * and the only honest expression of that is the event not happening.
     *
     * <p>Runs on the damage event rather than on {@code LivingDamageEvent.Post} so nothing downstream
     * — armour durability, thorns, mob aggro bookkeeping — sees a hit that Felix decided against.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (FelixFortune.rollNearDeathSave(player, event.getAmount())) {
            event.setCanceled(true);
        }
    }

    /**
     * An ore that gives one more than it should.
     *
     * <p>Ores only, not every block: Felix making cobblestone drop twice is noise, and the moment a
     * player notices is the moment a diamond comes out in a pair. Read off {@code #c:ores} rather
     * than a hand-written list so modded ores are included without this file knowing about them.
     *
     * <p>Duplicates <b>one</b> drop, not each of them, and never more than once per block. Fortune V
     * on everything you touch is a different potion.
     */
    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) {
            return;
        }
        BlockState state = event.getState();
        if (!state.is(BlockTags.COAL_ORES) && !state.is(BlockTags.IRON_ORES)
                && !state.is(BlockTags.GOLD_ORES) && !state.is(BlockTags.COPPER_ORES)
                && !state.is(BlockTags.DIAMOND_ORES) && !state.is(BlockTags.EMERALD_ORES)
                && !state.is(BlockTags.LAPIS_ORES) && !state.is(BlockTags.REDSTONE_ORES)) {
            return;
        }
        if (event.getDrops().isEmpty() || !FelixFortune.rollExtraOreDrop(player)) {
            return;
        }

        ServerLevel level = event.getLevel();
        BlockPos pos = event.getPos();
        // Snapshot before adding: the list being iterated is the list being appended to.
        List<ItemEntity> bonus = new ArrayList<>(1);
        ItemEntity first = event.getDrops().iterator().next();
        ItemEntity copy = new ItemEntity(level,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, first.getItem().copy());
        copy.setDefaultPickUpDelay();
        bonus.add(copy);
        event.getDrops().addAll(bonus);

        FelixFortune.announce(player, "felix.wizards_and_beasts.rich_seam",
                at.koopro.wizardsandbeasts.feedback.NoticeKind.SUCCESS);
        FelixFortune.sparkle(level, player, 8);
    }
}
