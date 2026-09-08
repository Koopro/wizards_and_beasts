package at.koopro.wizardsandbeasts.item.broom;

import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * A tin of broom polish. Servicing kit, and the only consumable a broom has.
 *
 * <p>It was a {@code SimpleTooltipItem} — an item whose entire behaviour was a line of hover text
 * saying what it would do if it did anything. It now repairs a quarter of a broom's durability and
 * leaves the handle slick for twenty minutes, during which the broom wanders half as much.
 *
 * <h2>Two ways to use it, because there are two places a broom can be</h2>
 * Holding the tin and a broom in the other hand services the broom in hand; right-clicking a broom
 * standing in the world services that one. A player who has just landed has the broom under them,
 * not in their inventory, and making them pick it up first to oil it is friction with nothing on the
 * other side of it.
 *
 * <p>Both paths go through {@link BroomPolish} so the two cannot drift, and both refuse a broom that
 * would gain nothing — a tin is not spent on a broom that is already sound and already slick.
 */
public class BroomPolishItem extends Item {

    public BroomPolishItem(Properties properties) {
        super(properties);
    }

    /** Long enough to read as work with a rag. Half the carve, because a tin is routine upkeep. */
    public static final int POLISH_TICKS = 40;

    /**
     * Starts servicing a broom held in the other hand.
     *
     * <p>The tin is not spent here. Servicing is a rub rather than a click, so this only opens the
     * channel; {@link #finishUsingItem} is what applies it, and re-checks everything first.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack broom = player.getItemInHand(otherHand(hand));

        if (!(broom.getItem() instanceof BroomItem)) {
            return InteractionResult.PASS;
        }
        if (!serviceable(broom, level)) {
            refuse(player, level);
            return InteractionResult.FAIL;
        }
        // CONSUME rather than SUCCESS: SUCCESS swings the arm, and a swing at the start of a rub
        // reads as the rub having already happened.
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return POLISH_TICKS;
    }

    /** {@link ItemUseAnimation#NONE}: the rub is {@code ItemUsePosePass}'s, not one of vanilla's five. */
    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    /**
     * Applies the tin once the rub is finished.
     *
     * <p>Everything is re-checked rather than trusted from {@link #use}. The broom is in the other
     * hand and the player has had {@value #POLISH_TICKS} ticks to swap it, stow it or fly it away,
     * and a tin spent on a broom that is no longer there would be spent on nothing.
     */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide() || !(entity instanceof Player player)) {
            return stack;
        }
        ItemStack broom = player.getItemInHand(otherHand(player.getUsedItemHand()));
        if (!(broom.getItem() instanceof BroomItem) || !serviceable(broom, level)) {
            return stack;
        }
        polish(broom, stack, player, level);
        return stack;
    }

    /**
     * The hand that is not holding the tin.
     *
     * <p>Deliberately only the other hand, not a scan of the inventory: which broom got the tin has
     * to be something the player decided, and an inventory scan would pick one for them.
     */
    private static InteractionHand otherHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    /** Whether this broom would gain anything at all from a tin. */
    public static boolean serviceable(ItemStack broom, Level level) {
        return BroomPolish.needsRepair(broom) || !BroomPolish.isPolished(broom, level);
    }

    /** Applies the tin, spends one, and says so. Server side only. */
    public static void polish(ItemStack broom, ItemStack tin, Player player, Level level) {
        if (!BroomPolish.apply(broom, level)) {
            return;
        }
        if (!player.getAbilities().instabuild) {
            tin.shrink(1);
        }
        level.playSound(null, player.blockPosition(), ModSounds.BROOM_POLISH_APPLY.get(),
                SoundSource.PLAYERS, 0.7f, 1.0f);
        PlayerFeedback.actionBar(player,
                Component.translatable("broom.wizards_and_beasts.polish.applied")
                        .withStyle(ChatFormatting.AQUA));
    }

    /** Told on the action bar rather than refused silently, so a wasted click explains itself. */
    private static void refuse(Player player, Level level) {
        if (!level.isClientSide()) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("broom.wizards_and_beasts.polish.not_needed")
                            .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.broom_polish.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }

}
