package at.koopro.wizardsandbeasts.item.consumable;

import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * Instant eat/drink for wizarding snacks without DataComponents food/consumable setup.
 */
public class WizardingQuickConsumableItem extends Item {

    private final int nutrition;
    private final float saturation;
    private final MobEffectInstance[] effects;
    private final int consumeTicks;
    private final ItemUseAnimation animation;
    private final int cooldownTicks;

    public WizardingQuickConsumableItem(Properties properties, int nutrition, float saturation, MobEffectInstance... effects) {
        this(properties, nutrition, saturation, 16, ItemUseAnimation.EAT, 0, effects);
    }

    public WizardingQuickConsumableItem(Properties properties, int nutrition, float saturation, int consumeTicks,
            ItemUseAnimation animation, int cooldownTicks, MobEffectInstance... effects) {
        super(properties);
        this.nutrition = nutrition;
        this.saturation = saturation;
        this.consumeTicks = consumeTicks;
        this.animation = animation;
        this.cooldownTicks = cooldownTicks;
        this.effects = effects.clone();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return consumeTicks;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return animation;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide() && entity instanceof Player player) {
            player.getFoodData().eat(nutrition, saturation);
            for (MobEffectInstance template : effects) {
                player.addEffect(new MobEffectInstance(template));
            }
            stack.consume(1, player);
            if (cooldownTicks > 0) {
                player.getCooldowns().addCooldown(stack, cooldownTicks);
            }
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return stack;
    }
}
