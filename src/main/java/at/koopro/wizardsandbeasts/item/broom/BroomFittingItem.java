package at.koopro.wizardsandbeasts.item.broom;

import at.koopro.wizardsandbeasts.broom.fitting.BroomFitting;
import at.koopro.wizardsandbeasts.broom.fitting.BroomWorkshop;
import at.koopro.wizardsandbeasts.broom.fitting.FittingStat;
import at.koopro.wizardsandbeasts.registry.BroomItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One broom fitting, ready to go on. Use it with a broom in the other hand, or on a broom parked in the world.
 * What it does is {@link BroomFitting}'s; this is the thing a shop sells and a rank hands out.
 */
public class BroomFittingItem extends Item {

    private final BroomFitting fitting;

    public BroomFittingItem(Properties properties, BroomFitting fitting) {
        super(properties);
        this.fitting = fitting;
    }

    public BroomFitting fitting() {
        return fitting;
    }

    /** One of the item that carries {@code fitting}. */
    public static ItemStack stackOf(BroomFitting fitting) {
        return new ItemStack(BroomItemRegistry.fittingItem(fitting));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack broom = player.getItemInHand(hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!(broom.getItem() instanceof BroomItem)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) {
            return BroomWorkshop.fit(server, broom, player.getItemInHand(hand))
                    ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("broom.wizards_and_beasts.fitting.slot",
                Component.translatable(fitting.slot().translationKey())).withStyle(ChatFormatting.GRAY));
        for (Map.Entry<FittingStat, Float> modifier : fitting.modifiers().entrySet()) {
            tooltip.accept(statLine(modifier.getKey(), modifier.getValue()));
        }
        tooltip.accept(Component.translatable("broom.wizards_and_beasts.fitting.min_tier",
                fitting.minTier().displayName()).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("broom.wizards_and_beasts.fitting.how").withStyle(ChatFormatting.DARK_GRAY,
                ChatFormatting.ITALIC));
    }

    /** "+6% Top speed", coloured by whether the change helps the rider. */
    public static Component statLine(FittingStat stat, float multiplier) {
        int percent = Math.round((multiplier - 1.0f) * 100.0f);
        boolean good = (percent > 0) == stat.higherIsBetter();
        return Component.literal(String.format(Locale.ROOT, "%+d%% ", percent))
                .append(Component.translatable(stat.translationKey()))
                .withStyle(good ? ChatFormatting.GREEN : ChatFormatting.RED);
    }
}
