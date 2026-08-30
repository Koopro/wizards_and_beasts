package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.toad.PeppermintToad;
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
 * A Peppermint Toad, which goes on hopping after you have eaten it.
 *
 * <p>Novelty candy and priced as such: twelve seconds of ordinary Speed, a small green hop out of
 * your stomach every three seconds, and one step of your nausea settled by the mint. Nothing here is
 * meant to compete with a Whizzbee or a potion — this is the cheap sweet somebody eats because it is
 * funny.
 *
 * <p>The nausea rule is deliberately one <em>level</em> rather than a cure: a bad case takes several
 * toads, which is a better joke than one toad fixing everything. See {@link PeppermintToad}.
 */
@NullMarked
public class PeppermintToadItem extends ConsumedItem {

    private static final int NUTRITION = 2;
    private static final float SATURATION = 0.3f;
    private static final int EAT_TICKS = 16;
    private static final int COOLDOWN_TICKS = 10;

    public PeppermintToadItem(Properties properties) {
        super(properties, EAT_TICKS, ItemUseAnimation.EAT);
    }

    @Override
    protected void onConsumed(ItemStack stack, Level level, Player player) {
        player.getFoodData().eat(NUTRITION, SATURATION);

        // Settled before the hop is applied, so the toad never immediately un-settles you.
        int settled = PeppermintToad.settleStomach(player);
        if (settled >= 0) {
            player.displayClientMessage(
                    Component.translatable("item.wizards_and_beasts.peppermint_toad.settled")
                            .withStyle(ChatFormatting.GREEN), true);
        }

        player.addEffect(new MobEffectInstance(
                MobEffects.SPEED, PeppermintToad.DURATION_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(
                ModEffects.PEPPERMINT_HOP, PeppermintToad.DURATION_TICKS, 0, false, true, true));

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FROG_LONG_JUMP, SoundSource.PLAYERS, 0.4f, 1.6f);

        stack.consume(1, player);
        player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.peppermint_toad.speed",
                        PeppermintToad.DURATION_TICKS / 20)
                .withStyle(ChatFormatting.GREEN));
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.peppermint_toad.hop")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
