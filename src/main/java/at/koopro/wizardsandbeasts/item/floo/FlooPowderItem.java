package at.koopro.wizardsandbeasts.item.floo;

import at.koopro.wizardsandbeasts.block.floo.FlooGrateBlock;
import at.koopro.wizardsandbeasts.block.floo.FlooGrateBlockEntity;
import at.koopro.wizardsandbeasts.floo.FlooAccess;
import at.koopro.wizardsandbeasts.floo.FlooCues;
import at.koopro.wizardsandbeasts.floo.FlooNetworkManager;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

public class FlooPowderItem extends Item {
    public FlooPowderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        if (state.is(ModBlocks.FLOO_GRATE.get())) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            // Crouching with powder in hand at a burning grate is kneeling to put your head in it.
            // The gesture is on the powder rather than on an empty hand because an empty hand at a
            // lit hearth already means something else — see FlooFireplaceBlock.useWithoutItem — and
            // a call is the one Floo interaction that plausibly costs a pinch either way.
            // The pinch thrown into the fire is the grate's own business (FlooGrateBlock.useItemOn); crouching skips
            // the block, so only the call arrives here.
            if (player.isShiftKeyDown() && state.getValue(FlooGrateBlock.FIRE).burning()) {
                return openCall((ServerLevel) level, pos, player);
            }
            return InteractionResult.PASS;
        }

        if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
            if (!level.isClientSide()) {
                BlockState soul = Blocks.SOUL_CAMPFIRE.defaultBlockState()
                        .setValue(CampfireBlock.FACING, state.getValue(CampfireBlock.FACING))
                        .setValue(CampfireBlock.SIGNAL_FIRE, state.getValue(CampfireBlock.SIGNAL_FIRE))
                        .setValue(CampfireBlock.WATERLOGGED, state.getValue(CampfireBlock.WATERLOGGED))
                        .setValue(CampfireBlock.LIT, true);
                level.setBlock(pos, soul, 11);
                stack.shrink(1);
                level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1.0f, 1.2f);
                if (level instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            32, 0.3, 0.2, 0.3, 0.02);
                }
            }
            return InteractionResult.SUCCESS;
        }

        if (state.is(Blocks.FIRE)) {
            if (!level.isClientSide()) {
                stack.shrink(1);
                if (level instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5,
                            24, 0.4, 0.1, 0.4, 0.02);
                }
                level.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.8f, 1.4f);
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    /**
     * Offer the "head in the fire" call picker at a lit, registered hearth.
     *
     * <p>Costs nothing by itself: {@link at.koopro.wizardsandbeasts.floo.call.FlooCallService} runs
     * the call, and this only asks who to call. A refusal here therefore has to be free too, which is
     * why the checks mirror the ignite path rather than sharing its powder deduction.
     */
    private static InteractionResult openCall(ServerLevel level, BlockPos pos, Player player) {
        if (!ModuleManager.isEnabled(Module.FLOO_NETWORK)) {
            player.displayClientMessage(
                    Component.translatable("floo.wizards_and_beasts.fail.module_off"), true);
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof FlooGrateBlockEntity be) || !be.formed() || !be.isRegistered()) {
            player.displayClientMessage(
                    Component.translatable("floo.wizards_and_beasts.fail.unregistered"), true);
            return InteractionResult.SUCCESS;
        }
        if (!be.isEnabled()) {
            player.displayClientMessage(
                    Component.translatable("floo.wizards_and_beasts.fail.sealed"), true);
            if (player instanceof ServerPlayer sealedFor) {
                FlooCues.sealed(sealedFor);
            }
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp) {
            FlooAccess.openPicker(sp, FlooNetworkManager.get(level), be.getNetworkAddress(), true);
        }
        return InteractionResult.SUCCESS;
    }
}
