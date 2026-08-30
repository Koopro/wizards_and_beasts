package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.pumpkinjuice.HogwartsComfort;
import at.koopro.wizardsandbeasts.pumpkinjuice.PumpkinJuice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffect;
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
 * Pumpkin juice: what is on the table at breakfast, and in every bag on the Hogwarts Express.
 *
 * <p>Written against {@link at.koopro.wizardsandbeasts.butterbeer.ButterbeerItem} rather than
 * alongside it. Butterbeer is the pub: warmth, calm, a softened view, and a mug you keep. This is
 * school: it clears your head of one small thing and makes the next minute of lessons and duelling
 * count for a little more. Same shelf, opposite moods, and a player should never be indifferent
 * about which one they packed.
 *
 * <h2>Why a real cooldown here and not on Butterbeer</h2>
 * Butterbeer's brake is an effect window, because refusing to let somebody drink reads as the game
 * being broken. This one is briefed with a straight fifteen-second cooldown and it is the right shape
 * for it: the reason to drink again quickly would be to reroll the random cure, and a window would
 * not stop that.
 */
@NullMarked
public class PumpkinJuiceItem extends ConsumedItem {

    private static final int DRINK_TICKS = 28;

    public PumpkinJuiceItem(Properties properties) {
        super(properties, DRINK_TICKS, ItemUseAnimation.DRINK);
    }

    @Override
    protected void onConsumed(ItemStack stack, Level level, Player player) {

        player.getFoodData().eat(PumpkinJuice.NUTRITION, PumpkinJuice.SATURATION);

        Holder<MobEffect> cleared = PumpkinJuice.clearOne(player, player.getRandom());
        if (cleared != null) {
            // Naming what went is the difference between a cure and a coincidence: the drinker needs
            // to learn that this shakes off small things, and which small thing it just took.
            player.displayClientMessage(
                    Component.translatable("item.wizards_and_beasts.pumpkin_juice.cleared",
                            Component.translatable(cleared.value().getDescriptionId()))
                            .withStyle(ChatFormatting.GREEN), true);
        }

        player.addEffect(new MobEffectInstance(
                ModEffects.HOGWARTS_COMFORT, PumpkinJuice.COMFORT_TICKS, 0, false, false, true));
        player.getCooldowns().addCooldown(stack, PumpkinJuice.COOLDOWN_TICKS);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.7f, 1.25f);
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.pumpkin_juice.comfort",
                        Math.round(HogwartsComfort.BONUS * 100.0f), PumpkinJuice.COMFORT_TICKS / 20)
                .withStyle(ChatFormatting.GOLD));
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.pumpkin_juice.clears")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
