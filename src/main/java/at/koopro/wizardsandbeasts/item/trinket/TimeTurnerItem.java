package at.koopro.wizardsandbeasts.item.trinket;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

public class TimeTurnerItem extends Item {

    private static final int MAX_USE_TICKS = 72000;
    private static final int MIN_CHANNEL_TICKS = 16;
    private static final int MAX_CHANNEL_TICKS = 100;
    private static final int COOLDOWN_TICKS = 20 * 30;
    private static final long BASE_SHIFT_TICKS = 20L * 15L;
    private static final long MAX_SHIFT_TICKS = 20L * 180L;
    private static final int DANGER_RADIUS = 8;

    public TimeTurnerItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack) || player.isSpectator() || !player.isAlive()) {
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return MAX_USE_TICKS;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BLOCK;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        int usedTicks = getUseDuration(stack, entity) - remainingUseDuration;
        if (usedTicks > 0 && usedTicks % 10 == 0) {
            serverLevel.sendParticles(
                    ParticleTypes.ENCHANT,
                    player.getX(),
                    player.getY(0.7),
                    player.getZ(),
                    14,
                    0.35,
                    0.35,
                    0.35,
                    0.0
            );
            serverLevel.playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS,
                    0.25F,
                    0.85F + (usedTicks / 200.0F)
            );
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        if (player.isSpectator() || !player.isAlive() || player.getCooldowns().isOnCooldown(stack)) {
            return true;
        }
        if (!canShiftTime(serverLevel)) {
            player.displayClientMessage(
                    Component.literal("The Time Turner cannot bend time in this dimension.")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return true;
        }
        if (hasNearbyThreat(player)) {
            player.displayClientMessage(
                    Component.literal("Focus is unstable while danger is nearby.")
                            .withStyle(ChatFormatting.DARK_RED),
                    true
            );
            return true;
        }

        int channelTicks = getUseDuration(stack, entity) - timeLeft;
        if (channelTicks < MIN_CHANNEL_TICKS) {
            player.displayClientMessage(
                    Component.literal("Channel the Time Turner a little longer.")
                            .withStyle(ChatFormatting.GRAY),
                    true
            );
            return true;
        }

        long shiftTicks = computeShiftTicks(channelTicks);
        long currentDayTime = serverLevel.getDayTime();
        serverLevel.setDayTime(currentDayTime + shiftTicks);
        player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));

        float shiftedSeconds = shiftTicks / 20.0F;
        player.displayClientMessage(
                Component.literal(String.format("Time bends forward by %.1f seconds.", shiftedSeconds))
                        .withStyle(ChatFormatting.GOLD),
                true
        );
        serverLevel.playSound(
                null,
                player.blockPosition(),
                SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS,
                0.8F,
                1.1F
        );
        serverLevel.sendParticles(
                ParticleTypes.PORTAL,
                player.getX(),
                player.getY(1.0),
                player.getZ(),
                36,
                0.5,
                0.6,
                0.5,
                0.05
        );
        return true;
    }

    private static long computeShiftTicks(int channelTicks) {
        float normalized = (float) (Mth.clamp(channelTicks, MIN_CHANNEL_TICKS, MAX_CHANNEL_TICKS) - MIN_CHANNEL_TICKS)
                / (MAX_CHANNEL_TICKS - MIN_CHANNEL_TICKS);
        return BASE_SHIFT_TICKS + (long) ((MAX_SHIFT_TICKS - BASE_SHIFT_TICKS) * normalized);
    }

    private static boolean canShiftTime(LevelAccessor level) {
        return level instanceof Level l && l.dimension() == Level.OVERWORLD;
    }

    private static boolean hasNearbyThreat(ServerPlayer player) {
        return !player.level().getEntitiesOfClass(
                net.minecraft.world.entity.Mob.class,
                player.getBoundingBox().inflate(DANGER_RADIUS),
                mob -> mob.isAlive() && mob.getTarget() == player
        ).isEmpty();
    }
}
