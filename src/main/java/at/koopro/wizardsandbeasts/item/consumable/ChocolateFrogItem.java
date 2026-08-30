package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.chocolate.ChocolateFrog;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.entity.frog.ChocolateFrogEntity;
import at.koopro.wizardsandbeasts.item.trinket.FamousWizardCardItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * A Chocolate Frog: one mouthful of chocolate, one card, and a fifteen percent chance of neither.
 *
 * <p><b>Chocolate is what you eat after a Dementor.</b> That is the one piece of first aid the books
 * are explicit about, so eating one clears every despair the eater is carrying and refuses it
 * re-entry for thirty seconds — see {@link ChocolateFrog}. Not resistance to Dementors; resistance to
 * the feeling, which is what chocolate is actually for.
 *
 * <p><b>And it might hop off.</b> Rolled before the eating starts, so the animation never plays for a
 * frog that was never eaten. An escapee is a real entity you can chase down and pick back up; fail to
 * and you are simply out one snack. Low odds and a recoverable loss, because this is the joke the
 * item is named after rather than a punishment.
 */
@NullMarked
public class ChocolateFrogItem extends ConsumedItem {

    private static final int EAT_TICKS = 18;
    /** Short: this exists to stop a double-click eating two, not to ration chocolate. */
    private static final int COOLDOWN_TICKS = 10;

    public ChocolateFrogItem(Properties properties) {
        super(properties, EAT_TICKS, ItemUseAnimation.EAT);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        // Rolled here rather than in finishUsingItem: a frog that escapes must escape *instead of*
        // being eaten, and an eating animation that ends in nothing would read as a lost click.
        if (!level.isClientSide() && ChocolateFrog.escapes(level.getRandom())) {
            releaseFrog(level, player, stack);
            player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
            return InteractionResult.SUCCESS;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onConsumed(ItemStack stack, Level level, Player player) {
        player.getFoodData().eat(ChocolateFrog.NUTRITION, ChocolateFrog.SATURATION);

        int cleared = ChocolateFrog.clearDespair(player);
        player.addEffect(new MobEffectInstance(
                ModEffects.CHOCOLATE_WARD, ChocolateFrog.WARD_TICKS, 0, false, false, true));
        if (cleared > 0) {
            player.displayClientMessage(
                    Component.translatable("item.wizards_and_beasts.chocolate_frog.warmed")
                            .withStyle(ChatFormatting.GOLD), true);
        }

        ItemStack card = FamousWizardCardItem.randomCard(level);
        if (!player.getInventory().add(card)) {
            player.drop(card, false);
        }

        stack.consume(1, player);
        player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    /**
     * Lets one frog go.
     *
     * <p>The card stays in the wrapper, so an escaped frog costs the collector their card as well as
     * their snack — and catching it gets both back, because what hops away is the whole item.
     */
    private static void releaseFrog(Level level, Player player, ItemStack stack) {
        ItemStack one = stack.copyWithCount(1);
        stack.shrink(1);

        ChocolateFrogEntity frog = new ChocolateFrogEntity(level,
                player.getX(), player.getEyeY() - 0.2, player.getZ(), one);
        frog.setDeltaMovement(player.getLookAngle().scale(0.35).add(0.0, 0.28, 0.0));
        level.addFreshEntity(frog);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SLIME_JUMP_SMALL, SoundSource.PLAYERS, 0.7f, 1.4f);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getEyeY(), player.getZ(), 6, 0.3, 0.2, 0.3, 0.0);
        }
        player.displayClientMessage(
                Component.translatable("item.wizards_and_beasts.chocolate_frog.escaped")
                        .withStyle(ChatFormatting.YELLOW), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.chocolate_frog.dementor",
                        ChocolateFrog.WARD_TICKS / 20)
                .withStyle(ChatFormatting.GOLD));
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.chocolate_frog.jumpy",
                        Math.round(ChocolateFrog.ESCAPE_CHANCE * 100.0f))
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
