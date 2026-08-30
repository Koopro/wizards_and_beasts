package at.koopro.wizardsandbeasts.item.consumable;

import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * Instant eat/drink for wizarding snacks without DataComponents food/consumable setup.
 */
public class WizardingQuickConsumableItem extends ConsumedItem {

    private final int nutrition;
    private final float saturation;
    private final MobEffectInstance[] effects;
    private final int cooldownTicks;

    public WizardingQuickConsumableItem(Properties properties, int nutrition, float saturation, MobEffectInstance... effects) {
        this(properties, nutrition, saturation, 16, ItemUseAnimation.EAT, 0, effects);
    }

    public WizardingQuickConsumableItem(Properties properties, int nutrition, float saturation, int consumeTicks,
            ItemUseAnimation animation, int cooldownTicks, MobEffectInstance... effects) {
        super(properties, consumeTicks, animation);
        this.nutrition = nutrition;
        this.saturation = saturation;
        this.cooldownTicks = cooldownTicks;
        this.effects = effects.clone();
    }

    @Override
    protected void onConsumed(ItemStack stack, Level level, Player player) {
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
}
