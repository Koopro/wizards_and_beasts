package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.comfort.HomeComfort;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * Treacle tart. Still the best plain meal in the mod, and now a little more than that.
 *
 * <p>Ninety seconds of Home Comfort: one heart trickled out over the whole duration — slower than
 * eating almost anything else, deliberately — and fear passing thirty percent faster while it holds.
 *
 * <p>It stays a <em>food</em>. No combat buff, nothing that competes with a potion. The magical
 * sweets are supposed to be the special ones; this is what you eat because you like it, and it
 * happens to help a bit when things are bad. See {@link HomeComfort}.
 */
@NullMarked
public class TreacleTartItem extends ConsumedItem {

    /** Filling, and it takes a moment. */
    private static final int NUTRITION = 7;
    private static final float SATURATION = 0.9f;
    private static final int EAT_TICKS = 24;

    public TreacleTartItem(Properties properties) {
        super(properties, EAT_TICKS, ItemUseAnimation.EAT);
    }

    @Override
    protected void onConsumed(ItemStack stack, Level level, Player player) {
        player.getFoodData().eat(NUTRITION, SATURATION);
        player.addEffect(new MobEffectInstance(
                ModEffects.HOME_COMFORT, HomeComfort.DURATION_TICKS, 0, false, true, true));
        stack.consume(1, player);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.treacle_tart.favourite")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.treacle_tart.comfort",
                        HomeComfort.DURATION_TICKS / 20,
                        Math.round((1.0f - HomeComfort.FEAR_SCALE) * 100.0f))
                .withStyle(ChatFormatting.GRAY));
    }
}
