package at.koopro.wizardsandbeasts.item.projectile;

import at.koopro.wizardsandbeasts.entity.spell.WizardingThrownEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class WizardingProjectileItem extends Item {

    /** The throw every projectile used before any of them needed a different one. */
    public static final float DEFAULT_VELOCITY = 1.5f;

    private final float velocity;

    public WizardingProjectileItem(Properties properties) {
        this(properties, DEFAULT_VELOCITY);
    }

    /**
     * @param velocity how hard this one is thrown. Shadow Essence is deliberately weak — it is meant
     *                 to be placed a few blocks away, not lobbed across a valley, and range is the
     *                 only thing keeping a twelve-second concealment pool honest.
     */
    public WizardingProjectileItem(Properties properties, float velocity) {
        super(properties);
        this.velocity = velocity;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.5f, 0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f));
        if (!level.isClientSide()) {
            WizardingThrownEntity entity = new WizardingThrownEntity(level, player, new ItemStack(this));
            entity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, velocity, 1.0f);
            level.addFreshEntity(entity);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
