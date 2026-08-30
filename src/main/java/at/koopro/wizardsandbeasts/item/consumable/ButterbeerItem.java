package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.butterbeer.Butterbeer;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * A mug of Butterbeer.
 *
 * <p>Split out of {@link WizardingQuickConsumableItem} because none of what it does now fits that
 * class's shape: the effects depend on when you last drank, the mug comes back, and the sound is its
 * own. That base class is still right for a Chocolate Frog, and forcing four new constructor
 * parameters through it to serve one drink would have made it wrong for both.
 *
 * <h2>The second mug</h2>
 * A refill inside {@link Butterbeer#EFFECT_WINDOW_TICKS} restores hunger and nothing else. Not a
 * cooldown: you can always drink, it simply stops stacking. The tooltip says how long is left, so
 * this reads as a rule rather than as the item being broken.
 *
 * <h2>The mug comes back</h2>
 * Emptied into your hand where possible, otherwise into the inventory, otherwise dropped — the same
 * three-step fallback vanilla uses for a bucket, so a full inventory never silently eats it.
 */
@NullMarked
public class ButterbeerItem extends ConsumedItem {

    /** How long the drink takes. Longer than a potion: it is a pint, not a phial. */
    private static final int DRINK_TICKS = 32;

    public ButterbeerItem(Properties properties) {
        super(properties, DRINK_TICKS, ItemUseAnimation.DRINK);
    }

    /** The fizz as it is lifted, so the drink has a beginning as well as an end. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (remainingTicks == DRINK_TICKS - 1) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    ModSounds.BUTTERBEER_FIZZ.get(), SoundSource.PLAYERS, 0.7f, 1.0f);
        }
    }

    @Override
    protected void onConsumed(ItemStack stack, Level level, Player player) {

        Butterbeer.Pour pour = Butterbeer.pourFor(player);
        player.getFoodData().eat(Butterbeer.NUTRITION, Butterbeer.SATURATION);

        if (pour == Butterbeer.Pour.FULL) {
            player.addEffect(new MobEffectInstance(
                    ModEffects.WARMTH, Butterbeer.WARMTH_TICKS, 0, false, false, true));
            player.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION, Butterbeer.REGENERATION_TICKS, 0, false, false, true));
            player.addEffect(new MobEffectInstance(
                    ModEffects.MELLOW, Butterbeer.MELLOW_TICKS, 0, false, false, true));
        } else {
            player.displayClientMessage(
                    Component.translatable("item.wizards_and_beasts.butterbeer.refill_only")
                            .withStyle(ChatFormatting.GRAY), true);
        }

        // Optional and off by default. The one unpleasant outcome in the item, and it exists only
        // because the brief asked for a knob — a wizarding drink that debuffs children's characters
        // is not something a server should get without opting in.
        if (Config.butterbeerGulpNausea && Butterbeer.isGulping(player)) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.NAUSEA, Butterbeer.GULP_NAUSEA_TICKS, 0, false, false, true));
        }

        Butterbeer.markDrunk(player);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.BUTTERBEER_CHUG.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        player.awardStat(Stats.ITEM_USED.get(this));

        stack.consume(1, player);
        returnMug(player, stack);
    }

    /**
     * Hands the empty mug back.
     *
     * <p>Into the drinking hand if the mug that was drunk was the last of its stack, so the common
     * case is that your hand simply now holds an empty mug. Otherwise into the pack, otherwise on the
     * floor.
     */
    private static void returnMug(Player player, ItemStack drunk) {
        ItemStack empty = new ItemStack(ConsumableItemRegistry.EMPTY_BUTTERBEER_MUG.get());
        if (player.getAbilities().instabuild) {
            return;
        }
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
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.butterbeer.effects",
                        Butterbeer.WARMTH_TICKS / 20, Butterbeer.MELLOW_TICKS / 20)
                .withStyle(ChatFormatting.GOLD));
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.butterbeer.window",
                        Butterbeer.EFFECT_WINDOW_TICKS / 20)
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
