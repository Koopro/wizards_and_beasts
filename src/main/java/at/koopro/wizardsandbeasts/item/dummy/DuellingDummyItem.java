package at.koopro.wizardsandbeasts.item.dummy;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

import at.koopro.wizardsandbeasts.entity.dummy.DuellingDummyEntity;
import at.koopro.wizardsandbeasts.registry.ModEntities;

/**
 * Places a duelling dummy on the face you clicked.
 *
 * <p>An item that spawns an entity rather than a block that renders one: the dummy wears armour on
 * player bones and reports damage through the ordinary combat pipeline, and both of those want a
 * living entity. Crouching with an empty hand takes it back, so the item is the dummy's only
 * container - there is no block state to keep in step with it.
 */
@NullMarked
public class DuellingDummyItem extends Item {

    /** Space a dummy needs. Matches its hitbox, so one cannot be planted inside a wall. */
    private static final double WIDTH = 0.6;
    private static final double HEIGHT = 1.95;

    public DuellingDummyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        AABB footprint = new AABB(
                x - WIDTH / 2, y, z - WIDTH / 2,
                x + WIDTH / 2, y + HEIGHT, z + WIDTH / 2);
        if (!serverLevel.noCollision(footprint)) {
            return InteractionResult.FAIL;
        }

        DuellingDummyEntity dummy = ModEntities.DUELLING_DUMMY.get()
                .create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
        if (dummy == null) {
            return InteractionResult.FAIL;
        }
        float facing = rotationFacing(context.getHorizontalDirection());
        dummy.snapTo(x, y, z, facing, 0.0f);
        // snapTo moves only yRot, which a LivingEntity is not drawn from. setFacing carries it into
        // the body and head rotation as well; without it the dummy renders facing south.
        dummy.setFacing(facing);
        ItemStack stack = context.getItemInHand();
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name != null) {
            dummy.setCustomName(name);
        }
        serverLevel.addFreshEntity(dummy);
        serverLevel.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);

        Player player = context.getPlayer();
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    /** Faces the dummy back at whoever planted it, so it is ready to be hit without turning it. */
    private static float rotationFacing(Direction placerFacing) {
        return placerFacing.getOpposite().toYRot();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        lines.accept(Component.translatable("tooltip.wizards_and_beasts.duelling_dummy.equip")
                .withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable("tooltip.wizards_and_beasts.duelling_dummy.banner")
                .withStyle(ChatFormatting.DARK_GRAY));
        lines.accept(Component.translatable("tooltip.wizards_and_beasts.duelling_dummy.take")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
