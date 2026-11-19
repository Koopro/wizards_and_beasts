package at.koopro.wizardsandbeasts.item.trinket;

import at.koopro.wizardsandbeasts.item.AnimatedItem;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/**
 * A Remembrall. It glows when its holder has forgotten something real ({@link Remembrall}), and looking into
 * it gives a vague hint of what, never more than Neville got. Checked every two seconds while carried.
 *
 * <p>It used to flash its glint on a timer, which meant nothing (documentation/CANON_AUDIT.md C-10); the glow
 * now only shows when there is something to remember.
 */
@NullMarked
public class RemembrallItem extends Item implements AnimatedItem {

    private static final int CHECK_INTERVAL = 40;

    public RemembrallItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (!(entity instanceof ServerPlayer player) || (level.getGameTime() + player.getId()) % CHECK_INTERVAL != 0) {
            return;
        }
        refresh(stack, player);
    }

    /** Re-reads what the holder has forgotten and stores it on the stack. */
    public static void refresh(ItemStack stack, ServerPlayer player) {
        Remembrall.Forgotten forgotten = Remembrall.check(player);
        String now = forgotten == null ? null : forgotten.name();
        if (!Objects.equals(now, stack.get(ModDataComponents.REMEMBRALL_FORGOTTEN.get()))) {
            if (now == null) {
                stack.remove(ModDataComponents.REMEMBRALL_FORGOTTEN.get());
            } else {
                stack.set(ModDataComponents.REMEMBRALL_FORGOTTEN.get(), now);
            }
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModDataComponents.REMEMBRALL_FORGOTTEN.get());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            ItemStack stack = player.getItemInHand(hand);
            refresh(stack, serverPlayer);
            String forgotten = stack.get(ModDataComponents.REMEMBRALL_FORGOTTEN.get());
            Component line = forgotten == null
                    ? Component.translatable("item.wizards_and_beasts.remembrall.clear").withStyle(ChatFormatting.GRAY)
                    : Component.translatable("item.wizards_and_beasts.remembrall.red."
                            + forgotten.toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.RED);
            serverPlayer.displayClientMessage(line, true);
        }
        return InteractionResult.SUCCESS;
    }
}
