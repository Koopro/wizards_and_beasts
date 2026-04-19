package at.koopro.neo.item;

import at.koopro.neo.entity.BroomEntity;
import at.koopro.neo.registry.ModEntities;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;

public class BroomItem extends Item {

    public BroomItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            if (player.getVehicle() instanceof BroomEntity) {
                return InteractionResult.SUCCESS;
            }

            AABB searchBox = player.getBoundingBox().inflate(3.0);
            List<BroomEntity> nearbyBrooms = level.getEntitiesOfClass(BroomEntity.class, searchBox,
                    broom -> broom.getControllingPassenger() == null);
            if (!nearbyBrooms.isEmpty()) {
                BroomEntity nearest = nearbyBrooms.stream()
                        .min(Comparator.comparingDouble(player::distanceToSqr))
                        .orElse(null);
                if (nearest != null) {
                    player.startRiding(nearest);
                    return InteractionResult.SUCCESS;
                }
            }

            BroomEntity broom = new BroomEntity(ModEntities.BROOM.get(), level);
            broom.setPos(player.getX(), player.getY(), player.getZ());
            broom.setYRot(player.getYRot());
            broom.setBroomStack(stack.copy());
            level.addFreshEntity(broom);
            player.startRiding(broom);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
