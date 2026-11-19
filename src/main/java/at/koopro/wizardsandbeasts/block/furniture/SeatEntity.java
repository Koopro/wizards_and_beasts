package at.koopro.wizardsandbeasts.block.furniture;

import at.koopro.wizardsandbeasts.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

/**
 * The invisible thing a player rides while sitting on a chair, bench or armchair. It lives only while someone sits
 * and the seat stands: it is never saved, and it goes the tick its rider stands up or its block is broken. The rider
 * sits with their hips at the seat's height, facing the way the seat faces.
 */
@NullMarked
public class SeatEntity extends Entity {

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /**
     * Seats {@code player} on the seat at {@code pos}, unless it is taken or they already ride something.
     *
     * @return whether the use was taken (always on the client, which cannot see a seat's rider in advance)
     */
    public static boolean sit(Level level, BlockPos pos, Direction facing, float seatHeight, Player player) {
        if (player.isPassenger()) {
            return false;
        }
        if (level.isClientSide()) {
            return true;
        }
        if (!level.getEntitiesOfClass(SeatEntity.class, new AABB(pos)).isEmpty()) {
            return false;
        }
        SeatEntity seat = new SeatEntity(ModEntities.SEAT.get(), level);
        seat.setPos(pos.getX() + 0.5, pos.getY() + seatHeight, pos.getZ() + 0.5);
        seat.setYRot(facing.toYRot());
        level.addFreshEntity(seat);
        if (!player.startRiding(seat)) {
            seat.discard();
            return false;
        }
        player.setYRot(facing.toYRot());
        player.setYHeadRot(facing.toYRot());
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        BlockState state = level().getBlockState(blockPosition());
        boolean seatStands = state.getBlock() instanceof FurnitureBlock furniture && furniture.spec().seatHeight() >= 0f;
        if (getPassengers().isEmpty() || !seatStands) {
            ejectPassengers();
            discard();
        }
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return Vec3.ZERO;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockState state = level().getBlockState(blockPosition());
        if (state.getBlock() instanceof FurnitureBlock) {
            BlockPos front = blockPosition().relative(state.getValue(FurnitureBlock.FACING));
            if (level().getBlockState(front).getCollisionShape(level(), front).isEmpty()
                    && level().getBlockState(front.above()).getCollisionShape(level(), front.above()).isEmpty()) {
                return Vec3.atBottomCenterOf(front);
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}
