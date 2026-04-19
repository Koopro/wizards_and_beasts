package at.koopro.neo.item.wizarding;

import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Instant eat/drink for wizarding snacks without DataComponents food/consumable setup.
 */
public class WizardingQuickConsumableItem extends Item {

    private final int nutrition;
    private final float saturation;
    private final MobEffectInstance[] effects;

    public WizardingQuickConsumableItem(Properties properties, int nutrition, float saturation, MobEffectInstance... effects) {
        super(properties);
        this.nutrition = nutrition;
        this.saturation = saturation;
        this.effects = effects.clone();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            player.getFoodData().eat(nutrition, saturation);
            for (MobEffectInstance template : effects) {
                player.addEffect(new MobEffectInstance(template));
            }
            stack.consume(1, player);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResult.SUCCESS;
    }
}
