package at.koopro.wizardsandbeasts.item.hallow;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class ResurrectionStoneItem extends Item implements IHallowItem {

    /** "Turned thrice in the hand" — three turns call the shades. */
    private static final int TURNS_TO_SUMMON = 3;
    private static final int COMFORT_DURATION = 2400;   // 2 minutes of the dead's company
    private static final int COOLDOWN_TICKS = 1200;

    public ResurrectionStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("Turns thrice in the hand to summon shades of the dead.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        tooltipAdder.accept(Component.literal("They are not truly here.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_PURPLE));
        int uses = stack.getOrDefault(ModDataComponents.RESURRECTION_STONE_USES.get(), 0);
        if (uses > 0) {
            tooltipAdder.accept(Component.literal("Times used: " + uses)
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer caster) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        // Each right-click is a turn of the Stone; the third turn summons the shades.
        int turns = stack.getOrDefault(ModDataComponents.RESURRECTION_STONE_USES.get(), 0) + 1;
        if (turns < TURNS_TO_SUMMON) {
            stack.set(ModDataComponents.RESURRECTION_STONE_USES.get(), turns);
            serverLevel.playSound(null, caster.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.5f, 0.7f);
            caster.displayClientMessage(Component.literal("You turn the Stone in your hand…")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return InteractionResult.SUCCESS;
        }

        // Third turn: the shades gather. They are not truly here — a comfort that also unsettles.
        stack.set(ModDataComponents.RESURRECTION_STONE_USES.get(), 0);
        serverLevel.sendParticles(ParticleTypes.SOUL, caster.getX(), caster.getY() + 1.0, caster.getZ(),
                24, 0.6, 0.8, 0.6, 0.01);
        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, caster.getX(), caster.getY() + 0.5, caster.getZ(),
                8, 0.5, 0.4, 0.5, 0.005);
        serverLevel.playSound(null, caster.blockPosition(), SoundEvents.SOUL_ESCAPE.value(),
                SoundSource.PLAYERS, 0.7f, 0.8f);

        caster.addEffect(new MobEffectInstance(MobEffects.REGENERATION, COMFORT_DURATION, 0, true, true));
        caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, COMFORT_DURATION, 0, true, true));

        // The company of the dead steadies the mind, but meddling with death stains the soul.
        float mental = caster.getData(ModAttachments.MENTAL_STABILITY.get());
        caster.setData(ModAttachments.MENTAL_STABILITY.get(), Math.min(100f, mental + 15f));
        float corruption = caster.getData(ModAttachments.DARK_CORRUPTION.get());
        caster.setData(ModAttachments.DARK_CORRUPTION.get(), Math.min(100f,
                corruption + at.koopro.wizardsandbeasts.skill.vocation.VocationAbilityHooks.scaleCorruptionGain(caster, 3f)));

        caster.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        caster.displayClientMessage(Component.literal("Shades of the dead gather around you.")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        return InteractionResult.SUCCESS;
    }
}
