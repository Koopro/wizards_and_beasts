package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.wrackspurt.Wrackspurt;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * A Dirigible Plum. It floats, and so do you, briefly.
 *
 * <p>Luna wears Spectrespecs to look for Wrackspurts; this is the same idea eaten rather than worn.
 * For forty-five seconds anything nearby that is <em>hiding</em> is outlined in violet — and only to
 * you, because the outline is a client render-state field rather than anything on the wire. Then five
 * seconds of muddle, because a fruit that let you see the invisible and cost nothing would be a
 * scrying tool rather than a Luna Lovegood joke.
 *
 * <p>Narrow on purpose: concealment, not proximity. An ordinary zombie three blocks away is not
 * outlined; a wizard under a Cloak is. See {@link Wrackspurt}.
 */
@NullMarked
public class DirigiblePlumItem extends ConsumedItem {

    private static final int EAT_TICKS = 20;

    public DirigiblePlumItem(Properties properties) {
        super(properties, EAT_TICKS, ItemUseAnimation.EAT);
    }

    @Override
    protected void onConsumed(ItemStack stack, Level level, Player player) {
        player.getFoodData().eat(2, 0.2f);

        // The bob. One second, amplifier 0 -- enough to leave the ground and come straight back,
        // which is the whimsy and not a movement ability.
        player.addEffect(new MobEffectInstance(
                MobEffects.LEVITATION, Wrackspurt.LEVITATION_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(
                ModEffects.WRACKSPURT_SIGHT, Wrackspurt.SIGHT_TICKS, 0, false, true, true));
        // The muddle comes after the bob rather than with it: landing and then feeling odd reads as
        // an aftertaste, where all three at once would just be a mess of icons.
        player.addEffect(new MobEffectInstance(
                MobEffects.NAUSEA, Wrackspurt.CONFUSION_TICKS, 0, false, true, true));

        player.displayClientMessage(
                Component.translatable("item.wizards_and_beasts.dirigible_plum.sight")
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.7f);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getEyeY(), player.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
        }

        stack.consume(1, player);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.dirigible_plum.wrackspurt",
                        Wrackspurt.SIGHT_TICKS / 20, (int) Wrackspurt.RANGE)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.dirigible_plum.muddle")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
