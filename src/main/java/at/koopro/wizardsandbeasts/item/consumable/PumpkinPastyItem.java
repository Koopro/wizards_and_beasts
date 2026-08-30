package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A pumpkin pasty. Travel food, and nothing else.
 *
 * <p>No combat buff on purpose: if every food in the mod carried a bonus, the magical sweets would
 * stop being special and the sensible thing to pack would always be whatever had the best numbers.
 * This is what you buy off the trolley.
 *
 * <p>The one exception is the one the trolley implies. Eaten <em>while travelling</em> — in a
 * minecart, or on a broom — it is worth ten seconds of Speed. Not because pastry is magic, but
 * because that is when somebody eats one.
 */
@NullMarked
public class PumpkinPastyItem extends ConsumedItem {

    private static final int NUTRITION = 5;
    private static final float SATURATION = 0.6f;
    private static final int EAT_TICKS = 20;

    /** Express energy. Ten seconds, amplifier 0 — a pasty, not a potion. */
    private static final int SPEED_TICKS = 200;

    public PumpkinPastyItem(Properties properties) {
        super(properties, EAT_TICKS, ItemUseAnimation.EAT);
    }

    @Override
    protected void onConsumed(ItemStack stack, Level level, Player player) {
        player.getFoodData().eat(NUTRITION, SATURATION);

        if (isTravelling(player.getVehicle())) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.SPEED, SPEED_TICKS, 0, false, true, true));
            player.displayClientMessage(
                    Component.translatable("item.wizards_and_beasts.pumpkin_pasty.express")
                            .withStyle(ChatFormatting.GOLD), true);
        }

        stack.consume(1, player);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    /**
     * Whether the eater is actually on their way somewhere.
     *
     * <p>Minecarts and brooms — the mod's two "you are going somewhere and cannot do much else"
     * vehicles. Deliberately not every vehicle: a pasty eaten on a pig is not the Hogwarts Express.
     */
    public static boolean isTravelling(@Nullable Entity vehicle) {
        return vehicle instanceof AbstractMinecart || vehicle instanceof BroomEntity;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.pumpkin_pasty.travel",
                        SPEED_TICKS / 20)
                .withStyle(ChatFormatting.GRAY));
    }
}
