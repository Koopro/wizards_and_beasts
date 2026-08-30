package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.dittany.Dittany;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
 * Essence of Dittany, applied to a wound — yours or somebody else's.
 *
 * <p>Four hit points ordinarily, six when there is a real injury to close, and the injury goes with
 * it. That is the point of the change from a flat six: Dittany is what Hermione uses on Ron's
 * splinched arm, not a healing potion, and it should reward being used on somebody who is actually
 * hurt. See {@link Dittany}.
 *
 * <h2>Two ways to use it, and why they differ</h2>
 * On yourself it is <b>held</b> for a moment — a use-over-time, which also keeps
 * {@code SplinchDamageHandler} working, since that listens for the use-item finish event and is what
 * makes Dittany the cure for splinching. On somebody else it is an instant
 * {@link #interactLivingEntity}, because holding a bottle still while a friend bleeds would be a
 * worse mechanic than the one it replaced.
 *
 * <p>Still a brewing ingredient. Nothing here touches the Wiggenweld recipe, which takes the item
 * and does not care what its use behaviour is.
 */
@NullMarked
public class DittanyItem extends ConsumedItem {

    private static final int APPLY_TICKS = 24;

    public DittanyItem(Properties properties) {
        super(properties, APPLY_TICKS, ItemUseAnimation.DRINK);
    }

    @Override
    protected void onConsumed(ItemStack stack, Level level, Player player) {
        treat(player, player);
        stack.consume(1, player);
        player.getCooldowns().addCooldown(stack, Dittany.COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    /**
     * Pouring a dose on somebody else.
     *
     * <p>Refused on a patient who needs nothing, and the bottle is not spent — a rare healing item
     * used on a healthy sheep by mis-click should not vanish.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        if (!Dittany.needsTreatment(target)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        treat(player, target);
        stack.consume(1, player);
        player.getCooldowns().addCooldown(stack, Dittany.COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.SUCCESS;
    }

    /** One dose, on whoever it lands on. Healing is decided before the wound is closed. */
    private static void treat(Player healer, LivingEntity patient) {
        // Read first: mending the wound would otherwise erase the reason for the bonus before it is
        // applied, and a wounded patient would quietly heal the ordinary four.
        float healing = Dittany.healingFor(patient);
        int mended = Dittany.mend(patient);
        patient.heal(healing);

        patient.level().playSound(null, patient.getX(), patient.getY(), patient.getZ(),
                SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.7f, 1.4f);
        if (patient.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    patient.getX(), patient.getY() + patient.getBbHeight() * 0.6, patient.getZ(),
                    8, 0.3, 0.3, 0.3, 0.0);
        }
        if (mended > 0) {
            healer.displayClientMessage(
                    Component.translatable("item.wizards_and_beasts.dittany.mended")
                            .withStyle(ChatFormatting.GREEN), true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.dittany.drops")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.dittany.heals",
                        (int) Dittany.BASE_HEAL / 2,
                        (int) (Dittany.BASE_HEAL + Dittany.WOUND_BONUS) / 2)
                .withStyle(ChatFormatting.GREEN));
    }
}
