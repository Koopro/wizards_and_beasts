package at.koopro.wizardsandbeasts.item.consumable;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.function.Supplier;

/**
 * A bottled remedy: drunk by the holder, or poured on somebody else with a right-click on them.
 *
 * <p>Murtlap Essence and the Blood-Replenishing Potion are both things a healer gives to a patient
 * in canon (Harry's hand after Umbridge's quill, Arthur after Nagini), so both ways of using it work,
 * the same way {@link DittanyItem} does. A remedy is refused on a patient it would do nothing for, and
 * the bottle is kept.
 *
 * <p>The tooltip reads {@code <description id>.desc}, so each remedy says what it is for.
 */
@NullMarked
public class RemedyItem extends ConsumedItem {

    private static final int APPLY_TICKS = 24;

    private final float heal;
    private final List<Supplier<MobEffectInstance>> effects;
    private final List<Holder<MobEffect>> cures;
    private final int cooldownTicks;

    public RemedyItem(Properties properties, float heal, List<Supplier<MobEffectInstance>> effects,
                      List<Holder<MobEffect>> cures, int cooldownTicks) {
        super(properties, APPLY_TICKS, ItemUseAnimation.DRINK);
        this.heal = heal;
        this.effects = List.copyOf(effects);
        this.cures = List.copyOf(cures);
        this.cooldownTicks = cooldownTicks;
    }

    @Override
    protected void onConsumed(ItemStack stack, Level level, Player player) {
        apply(player);
        spend(stack, player);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (player.getCooldowns().isOnCooldown(stack) || !helps(target)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        apply(target);
        spend(stack, player);
        return InteractionResult.SUCCESS;
    }

    /** Whether a dose would do anything for this patient. */
    public boolean helps(LivingEntity patient) {
        if (patient.getHealth() < patient.getMaxHealth()) {
            return true;
        }
        for (Holder<MobEffect> cure : cures) {
            if (patient.hasEffect(cure)) {
                return true;
            }
        }
        return false;
    }

    /** One dose on whoever it lands on. */
    public void apply(LivingEntity patient) {
        for (Holder<MobEffect> cure : cures) {
            patient.removeEffect(cure);
        }
        if (heal > 0) {
            patient.heal(heal);
        }
        for (Supplier<MobEffectInstance> effect : effects) {
            patient.addEffect(effect.get());
        }
        patient.level().playSound(null, patient.getX(), patient.getY(), patient.getZ(),
                SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.7f, 1.2f);
        if (patient.level() instanceof ServerLevel level) {
            level.sendParticles(at.koopro.wizardsandbeasts.particle.MagicMotes.healing(),
                    patient.getX(), patient.getY() + patient.getBbHeight() * 0.6, patient.getZ(),
                    6, 0.3, 0.3, 0.3, 0.0);
        }
    }

    private void spend(ItemStack stack, Player player) {
        stack.consume(1, player);
        if (cooldownTicks > 0) {
            player.getCooldowns().addCooldown(stack, cooldownTicks);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
    }

}
