package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.whizzbee.Whizzbee;
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
 * A Fizzing Whizzbee. Eight seconds of not quite touching the ground.
 *
 * <p><b>Not just Jump Boost.</b> Jump Boost on its own is a bigger hop and then the same fall, which
 * is a potion effect rather than a joke. Three things together make the sweet: you go up further, you
 * come down slowly, and every jump while you are fizzing gets an extra kick on the way — plus a
 * steady sputter of sherbet round your feet the whole time and a soft pop when it runs out.
 *
 * <p>The float is the payload and the pop is the punchline. See {@link Whizzbee} for the numbers and
 * {@code WhizzbeeHandler} for the three things an effect cannot do for itself.
 */
@NullMarked
public class FizzingWhizzbeeItem extends ConsumedItem {

    private static final int NUTRITION = 2;
    private static final float SATURATION = 0.3f;
    private static final int EAT_TICKS = 16;

    /** Just long enough to stop a double-click eating two. */
    private static final int COOLDOWN_TICKS = 10;

    public FizzingWhizzbeeItem(Properties properties) {
        super(properties, EAT_TICKS, ItemUseAnimation.EAT);
    }

    @Override
    protected void onConsumed(ItemStack stack, Level level, Player player) {
        player.getFoodData().eat(NUTRITION, SATURATION);

        // All three run for the same eight seconds and are all plain effects, so a milk bucket takes
        // the whole thing and nothing is left half-applied.
        player.addEffect(new MobEffectInstance(
                MobEffects.JUMP_BOOST, Whizzbee.DURATION_TICKS, Whizzbee.JUMP_AMPLIFIER,
                false, true, true));
        player.addEffect(new MobEffectInstance(
                MobEffects.SLOW_FALLING, Whizzbee.DURATION_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(
                ModEffects.FIZZING, Whizzbee.DURATION_TICKS, 0, false, true, true));

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.6f, 1.6f);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 0.1, player.getZ(), 12, 0.3, 0.05, 0.3, 0.05);
        }

        stack.consume(1, player);
        player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.fizzing_whizzbee.float",
                        Whizzbee.DURATION_TICKS / 20)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.fizzing_whizzbee.whizz")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
