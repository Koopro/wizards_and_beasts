package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.firewhisky.Firewhisky;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * Firewhisky. Courage in a glass, and a bill.
 *
 * <p><b>Never pure Strength.</b> Every shot buys twelve seconds of Strength and a little fire
 * resistance, and every shot charges three seconds of burn for it — heat haze across the screen and a
 * bite out of your hunger. A drink that granted Strength and nothing else would be a worse version of
 * a Strength potion with better flavour text.
 *
 * <p>The interesting part is what a second and a third cost. Inside a minute, the room starts moving
 * and goes briefly dark. Inside two minutes, a third puts you on the floor and takes your wand off
 * you for fifteen seconds — see {@link Firewhisky} for the windows and
 * {@code SpellCastGate.TOO_DRUNK} for what "takes your wand" means.
 *
 * <p>The bottle comes back, like a Butterbeer mug: a plain glass bottle, because unlike Butterbeer
 * there is nowhere to refill it and a bespoke empty would be a souvenir with no use.
 */
@NullMarked
public class FirewhiskyItem extends ConsumedItem {

    private static final int DRINK_TICKS = 32;

    public FirewhiskyItem(Properties properties) {
        super(properties, DRINK_TICKS, ItemUseAnimation.DRINK);
    }

    @Override
    protected void onConsumed(ItemStack stack, Level level, Player player) {
        player.getFoodData().eat(Firewhisky.NUTRITION, Firewhisky.SATURATION);

        // The courage, and the burn that pays for it. Both every time, whatever round this is.
        player.addEffect(new MobEffectInstance(
                MobEffects.STRENGTH, Firewhisky.COURAGE_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(
                MobEffects.FIRE_RESISTANCE, Firewhisky.COURAGE_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(
                ModEffects.FIREWHISKY_BURN, Firewhisky.BURN_TICKS, 0, false, false, true));

        Firewhisky.Round round = Firewhisky.drink(player);
        applyRound(player, round);
        announce(player, round);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 1.8f);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.FLAME,
                    player.getX(), player.getEyeY(), player.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
        }

        stack.consume(1, player);
        returnBottle(player, stack);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    /** What this particular shot costs on top of the burn. */
    private static void applyRound(Player player, Firewhisky.Round round) {
        switch (round) {
            case FIRST -> { }
            case SECOND -> {
                player.addEffect(new MobEffectInstance(
                        MobEffects.NAUSEA, Firewhisky.SECOND_NAUSEA_TICKS, 0, false, true, true));
                player.addEffect(new MobEffectInstance(
                        MobEffects.BLINDNESS, Firewhisky.SECOND_BLINDNESS_TICKS, 0, false, true, true));
            }
            case THIRD -> {
                player.addEffect(new MobEffectInstance(
                        MobEffects.NAUSEA, Firewhisky.SECOND_NAUSEA_TICKS, 0, false, true, true));
                player.addEffect(new MobEffectInstance(
                        ModEffects.DRUNK, Firewhisky.DRUNK_TICKS, 0, false, true, true));
            }
        }
    }

    /**
     * Says which round this was, always.
     *
     * <p>A wizard who suddenly cannot cast needs to know it was the whisky. An unexplained cast
     * refusal fifteen seconds after a drink is a bug report.
     */
    private static void announce(Player player, Firewhisky.Round round) {
        String key = switch (round) {
            case FIRST -> "item.wizards_and_beasts.firewhisky.first";
            case SECOND -> "item.wizards_and_beasts.firewhisky.second";
            case THIRD -> "item.wizards_and_beasts.firewhisky.third";
        };
        ChatFormatting colour = switch (round) {
            case FIRST -> ChatFormatting.GOLD;
            case SECOND -> ChatFormatting.YELLOW;
            case THIRD -> ChatFormatting.RED;
        };
        player.displayClientMessage(Component.translatable(key).withStyle(colour), true);
    }

    /** Hand, then pack, then floor — the same order a bucket empties into. */
    private static void returnBottle(Player player, ItemStack drunk) {
        if (player.getAbilities().instabuild) {
            return;
        }
        ItemStack empty = new ItemStack(Items.GLASS_BOTTLE);
        if (drunk.isEmpty()) {
            player.setItemInHand(player.getUsedItemHand(), empty);
            return;
        }
        if (!player.getInventory().add(empty)) {
            player.drop(empty, false);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.firewhisky.courage",
                        Firewhisky.COURAGE_TICKS / 20)
                .withStyle(ChatFormatting.GOLD));
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.firewhisky.cost",
                        Firewhisky.SECOND_WINDOW_TICKS / 20, Firewhisky.THIRD_WINDOW_TICKS / 20)
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
