package at.koopro.wizardsandbeasts.item.trinket;

import at.koopro.wizardsandbeasts.item.AnimatedItem;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.timeturner.TimeTurnerRules;
import at.koopro.wizardsandbeasts.timeturner.TimeTurnerService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * The Time-Turner: hold to wind it, one turn per second, and let go to go back that many hours to where you stood.
 *
 * <p>All of what it may do is {@link TimeTurnerRules}; all of the doing is {@link TimeTurnerService}. The item only
 * counts turns and shows them. It used to push the whole world's clock <em>forward</em> for every player on the
 * server, the opposite of a Time-Turner and a side effect no one player should own (2026-09-29,
 * documentation/MAGICAL_ARTEFACT_STATUS.md).
 */
public class TimeTurnerItem extends Item implements AnimatedItem {

    private static final int MAX_USE_TICKS = 72000;

    public TimeTurnerItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.time_turner.lore")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.time_turner.rule",
                TimeTurnerRules.MAX_TURNS).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.ARTEFACTS) || player.isSpectator() || !player.isAlive()) {
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return MAX_USE_TICKS;
    }

    /**
     * {@link ItemUseAnimation#NONE} — winding the chain belongs to {@code ItemUsePosePass}.
     *
     * <p>It was {@code BLOCK}, which is vanilla's shield stance: the arm comes across the body and
     * stops. That reads as bracing for a hit, which is close to the opposite of what a Time-Turner
     * is doing — the whole gesture is a small thing being turned, repeatedly, at the chest.
     */
    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    /** Each completed turn chimes and says how many hours are wound, so letting go is a choice. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        int wound = getUseDuration(stack, entity) - remainingUseDuration;
        if (wound <= 0 || wound % TimeTurnerRules.WIND_TICKS_PER_TURN != 0) {
            return;
        }
        int turns = TimeTurnerRules.turns(wound);
        if (wound / TimeTurnerRules.WIND_TICKS_PER_TURN > TimeTurnerRules.MAX_TURNS) {
            return; // fully wound: nothing more to count
        }
        serverLevel.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY(0.7), player.getZ(),
                14, 0.35, 0.35, 0.35, 0.0);
        serverLevel.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.35F, 0.8F + turns * 0.15F);
        player.displayClientMessage(Component.translatable("item.wizards_and_beasts.time_turner.wound",
                turns, TimeTurnerRules.MAX_TURNS).withStyle(ChatFormatting.GOLD), true);
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof ServerPlayer player) || level.isClientSide()
                || !ModuleManager.isEnabled(Module.ARTEFACTS) || player.isSpectator() || !player.isAlive()) {
            return true;
        }
        int turns = TimeTurnerRules.turns(getUseDuration(stack, entity) - timeLeft);
        if (TimeTurnerService.turnBack(player, turns) == TimeTurnerService.Result.BACK) {
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return true;
    }
}
