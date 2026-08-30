package at.koopro.wizardsandbeasts.item.consumable;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

/**
 * Shared scaffold for every item this mod feeds, drinks or chews through the held-use animation.
 *
 * <p>Fifteen items carried the same four overrides verbatim — a cooldown guard in {@code use}, a
 * constant {@code getUseDuration}, a constant {@code getUseAnimation}, and the
 * client-side/non-player narrowing at the top of {@code finishUsingItem}. All of it lives here now,
 * and a subclass supplies only the part that actually differs: {@link #onConsumed}.
 *
 * <p>The cooldown guard is unconditional. Items that never set a cooldown are unaffected by it —
 * {@code isOnCooldown} is simply always false for them — so the four items that previously skipped
 * the check behave exactly as before.
 *
 * <p>Items whose first click does something other than start eating (a Chocolate Frog that may bolt,
 * a Demiguise hair that weaves into a cloak when not sneaking) still override {@link #use} and keep
 * the other three behaviours from here.
 */
@NullMarked
public abstract class ConsumedItem extends Item {

    private final int useTicks;
    private final ItemUseAnimation animation;

    protected ConsumedItem(Properties properties, int useTicks, ItemUseAnimation animation) {
        super(properties);
        this.useTicks = useTicks;
        this.animation = animation;
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
        return useTicks;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return animation;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide() || !(entity instanceof Player player)) {
            return stack;
        }
        onConsumed(stack, level, player);
        return stack;
    }

    /**
     * Runs once, server-side, when the use animation completes.
     *
     * <p>The eater is already narrowed to a {@link Player} and the level is already known to be
     * server-side, so an implementation goes straight to its own effect. Consuming the stack is the
     * implementation's job — some items return a mug or a bottle instead of vanishing.
     */
    protected abstract void onConsumed(ItemStack stack, Level level, Player player);
}
