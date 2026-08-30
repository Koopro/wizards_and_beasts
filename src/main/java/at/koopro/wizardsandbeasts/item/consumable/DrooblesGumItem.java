package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.bubble.BubbleFloat;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * Droobles Best Blowing Gum, which blows bubbles that will lift you off the ground.
 *
 * <p>The chew is long — a bubble takes blowing, and while it grows everybody nearby watches it
 * inflate on your face. Then twenty-five seconds of being able to drift upward while you hold jump,
 * eight blocks above where you blew it and no further.
 *
 * <p><b>Deliberately a tool rather than flight.</b> It only lifts while the key is held, it stops at
 * a ceiling measured from where you started, and anything that hits you pops it — see
 * {@link BubbleFloat}, which owns all three limits. The forty-second cooldown is the fourth: a bubble
 * is a plan for crossing something, not a way to travel.
 */
@NullMarked
public class DrooblesGumItem extends ConsumedItem {

    /** Long enough to watch the bubble grow, short enough not to be a channel. */
    private static final int CHEW_TICKS = 40;

    public DrooblesGumItem(Properties properties) {
        super(properties, CHEW_TICKS, ItemUseAnimation.EAT);
    }

    @Override
    protected void onConsumed(ItemStack stack, Level level, Player player) {
        player.getFoodData().eat(1, 0.1f);

        // Slow Falling rides along for the whole float: the bubble is what lifts you, and this is
        // what stops the ride ending in a crater when it pops on the way down.
        player.addEffect(new MobEffectInstance(
                MobEffects.SLOW_FALLING, BubbleFloat.DURATION_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(
                ModEffects.BUBBLE_FLOAT, BubbleFloat.DURATION_TICKS, 0, false, true, true));
        // Anchored after the effect lands, so the ceiling is measured from where the bubble was
        // actually blown rather than from wherever the player happened to be a tick earlier.
        BubbleFloat.anchor(player);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.PLAYERS, 0.8f, 1.3f);
        player.getCooldowns().addCooldown(stack, BubbleFloat.COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.droobles.float",
                        BubbleFloat.DURATION_TICKS / 20, (int) BubbleFloat.MAX_RISE)
                .withStyle(ChatFormatting.AQUA));
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.droobles.pops")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
