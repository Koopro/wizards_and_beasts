package at.koopro.wizardsandbeasts.item.trinket;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.function.Consumer;

public class FoeGlassItem extends Item {

    private static final double SCAN_RADIUS = 32.0;

    public FoeGlassItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("Shows your enemies. The clearer the image, the closer they are.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
    }

    /**
     * How long the glass has to be held up before it shows anything.
     *
     * <p>Two seconds. A Dark Detector that answers on a click is a radar readout; one you raise and
     * stare into is the object the books describe. The wait is the item.
     */
    public static final int PEER_TICKS = 40;

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return InteractionResult.FAIL;
        }
        // Started on both sides: the client needs the use to begin for the raised pose, the server
        // to time it. CONSUME rather than SUCCESS so the arm does not swing as the glass comes up.
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return PEER_TICKS;
    }

    /**
     * {@link ItemUseAnimation#NONE} — the two-handed raise belongs to {@code ItemUsePosePass}.
     *
     * <p>Unlike the Omnioculars, which keep {@code ItemUseAnimation.SPYGLASS} so that vanilla poses
     * the holding arm, nothing vanilla does resembles peering into a mirror. The pass owns both arms
     * here.
     */
    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    /**
     * What the glass shows, once it has been held up long enough to show it.
     *
     * <p>The module is re-checked rather than trusted from {@link #use}: two seconds is long enough
     * for an operator to switch DARK_ARTS off mid-look, and a gate that only guards the entrance is
     * not a gate.
     */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide() || !ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return stack;
        }
        if (entity instanceof ServerPlayer serverPlayer) {
            reveal(serverPlayer, level);
        }
        return stack;
    }

    private static void reveal(ServerPlayer serverPlayer, Level level) {
        AABB scan = serverPlayer.getBoundingBox().inflate(SCAN_RADIUS);
        List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, scan,
                e -> e != serverPlayer && isFoe(e));
        if (foes.isEmpty()) {
            serverPlayer.displayClientMessage(
                    Component.literal("The glass is clouded. No enemies are near.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        foes.sort((a, b) -> Double.compare(serverPlayer.distanceToSqr(a), serverPlayer.distanceToSqr(b)));

        LivingEntity nearest = foes.get(0);
        double dist = Math.sqrt(serverPlayer.distanceToSqr(nearest));
        Direction facing = Direction.getApproximateNearest(
                nearest.getX() - serverPlayer.getX(), 0.0, nearest.getZ() - serverPlayer.getZ());
        ChatFormatting clarity = dist < 8 ? ChatFormatting.DARK_RED : dist < 20 ? ChatFormatting.RED : ChatFormatting.GOLD;

        serverPlayer.displayClientMessage(
                Component.literal(foes.size() + " foe" + (foes.size() == 1 ? "" : "s") + " near — nearest: "
                        + nearest.getName().getString() + " " + Math.round(dist) + "m " + facing.getName())
                        .withStyle(clarity), true);
    }

    /** A foe is any hostile mob, or another player not on the holder's team. */
    private static boolean isFoe(LivingEntity entity) {
        if (entity instanceof Enemy) {
            return true;
        }
        return entity instanceof Player;
    }
}
