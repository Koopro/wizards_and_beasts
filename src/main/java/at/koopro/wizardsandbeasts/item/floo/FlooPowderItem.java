package at.koopro.wizardsandbeasts.item.floo;

import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlock;
import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlockEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

        if (state.is(ModBlocks.FLOO_FIREPLACE.get()) && ModuleManager.isEnabled(Module.FLOO_NETWORK)) {
            if (!state.getValue(FlooFireplaceBlock.LIT)) {
                if (!level.isClientSide()) {
                    stack.shrink(1);
                    level.setBlock(pos, state.setValue(FlooFireplaceBlock.LIT, true), 3);
                    if (level.getBlockEntity(pos) instanceof FlooFireplaceBlockEntity be) {
                        be.setLitTicksRemaining(FlooFireplaceBlockEntity.LIT_TIMEOUT_TICKS);
                    }
                    // LORE: the flames roar up and turn emerald green.
                    level.playSound(null, pos, ModSounds.FLOO_IGNITE.get(), SoundSource.BLOCKS, 1.0f, 0.9f);
                    level.playSound(null, pos, ModSounds.FLOO_WHOOSH.get(), SoundSource.BLOCKS, 0.9f, 0.8f);
                    if (level instanceof ServerLevel server) {
                        spawnEmeraldRoar(server, pos);
                    }
                }
            } else if (!level.isClientSide()) {
                player.displayClientMessage(Component.literal("The fireplace is already burning."), true);
            }
            return InteractionResult.SUCCESS;
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

    /** Emerald-green flame column roaring upward when a fireplace is lit for Floo. */
    private static void spawnEmeraldRoar(ServerLevel server, BlockPos pos) {
        DustParticleOptions emerald = new DustParticleOptions(0xFF21B342, 1.6f);
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;
        // Rising emerald column (taller than the idle flicker).
        for (int i = 0; i < 40; i++) {
            double ox = (server.random.nextDouble() - 0.5) * 0.7;
            double oz = (server.random.nextDouble() - 0.5) * 0.7;
            double y = pos.getY() + 0.1 + server.random.nextDouble() * 1.6;
            server.sendParticles(emerald, cx + ox, y, cz + oz, 1, 0, 0, 0, 0);
        }
        // Ember burst + soul-flame core for the roar.
        server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, cx, pos.getY() + 0.4, cz,
                18, 0.25, 0.5, 0.25, 0.08);
        server.sendParticles(ParticleTypes.LAVA, cx, pos.getY() + 0.3, cz,
                4, 0.2, 0.2, 0.2, 0.0);
    }
}
