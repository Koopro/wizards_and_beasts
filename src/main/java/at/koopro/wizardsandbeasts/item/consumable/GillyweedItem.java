package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.gillyweed.Gillyweed;
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
 * Gillyweed. The Black Lake item, and it should feel like one.
 *
 * <p>Not Water Breathing with a nicer name. Forty-five seconds of being <em>something else</em>: you
 * breathe water, you swim like you belong there, bubbles stream off your neck and your skin takes the
 * colour of the lake. Then, three seconds before it goes, everything tells you to surface — and after
 * that you are a wizard at the bottom of a lake with ordinary lungs.
 *
 * <p>Vanilla Water Breathing is <b>replaced, never stacked</b>: any already running is stripped when
 * the gills grow, and a new one cannot land while they are in. See {@code GillyweedHandler}.
 *
 * <p>Chewing another sprig mid-dive refreshes the duration, which it should — surfacing to plan is
 * not what the Black Lake is about. It costs a ten-second cooldown once the gills finally lapse, so a
 * pocketful of Gillyweed is a long dive rather than a permanent one.
 */
@NullMarked
public class GillyweedItem extends ConsumedItem {

    private static final int CHEW_TICKS = 24;

    public GillyweedItem(Properties properties) {
        super(properties, CHEW_TICKS, ItemUseAnimation.EAT);
    }

    @Override
    protected void onConsumed(ItemStack stack, Level level, Player player) {
        player.getFoodData().eat(1, 0.1f);

        boolean refreshed = Gillyweed.chew(player, player.hasEffect(ModEffects.GILLS));

        // Stripped rather than left to run alongside: two sources of underwater breathing is two
        // timers the player has to track, and the shorter one silently doing nothing.
        player.removeEffect(MobEffects.WATER_BREATHING);
        player.addEffect(new MobEffectInstance(
                ModEffects.GILLS, Gillyweed.DURATION_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(
                MobEffects.WATER_BREATHING, Gillyweed.DURATION_TICKS, 0, false, false, false));

        player.displayClientMessage(
                Component.translatable(refreshed
                                ? "item.wizards_and_beasts.gillyweed.refreshed"
                                : "item.wizards_and_beasts.gillyweed.grown")
                        .withStyle(ChatFormatting.AQUA), true);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.6f, 1.4f);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP,
                    player.getX(), player.getY() + player.getEyeHeight() - 0.2, player.getZ(),
                    18, 0.3, 0.2, 0.3, 0.02);
        }

        stack.consume(1, player);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.gillyweed.gills",
                        Gillyweed.DURATION_TICKS / 20)
                .withStyle(ChatFormatting.AQUA));
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.gillyweed.warning",
                        Gillyweed.WARNING_TICKS / 20)
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
