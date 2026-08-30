package at.koopro.wizardsandbeasts.item.floo;

import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlock;
import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlockEntity;
import at.koopro.wizardsandbeasts.block.floo.FlooFlamesBlock;
import at.koopro.wizardsandbeasts.floo.FlooAccess;
import at.koopro.wizardsandbeasts.floo.FlooCues;
import at.koopro.wizardsandbeasts.floo.FlooFlamePlacement;
import at.koopro.wizardsandbeasts.floo.FlooNetworkManager;
import at.koopro.wizardsandbeasts.network.floo.FlooBlockSyncS2CPayload;
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

        if (state.is(ModBlocks.FLOO_FIREPLACE.get())) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            // Crouching with powder in hand at a burning grate is kneeling to put your head in it.
            // The gesture is on the powder rather than on an empty hand because an empty hand at a
            // lit hearth already means something else — see FlooFireplaceBlock.useWithoutItem — and
            // a call is the one Floo interaction that plausibly costs a pinch either way.
            if (player.isShiftKeyDown() && state.getValue(FlooFireplaceBlock.LIT)) {
                return openCall((ServerLevel) level, pos, player);
            }
            return igniteHearth((ServerLevel) level, pos, state, stack, player);
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
        if (!(level.getBlockEntity(pos) instanceof FlooFireplaceBlockEntity be) || !be.isRegistered()) {
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

    /**
     * A pinch of powder into a hearth: light it, and stand green fire in front of it.
     *
     * <h2>Nothing is spent on a refusal</h2>
     * <p>Every check below returns before {@code stack.shrink}, so a player who clicks a sealed-off
     * module, an unnamed hearth or a blocked opening keeps their powder. Floo Powder is a crafted
     * consumable and the old behaviour burned one on any cold hearth whether or not it could ever be
     * travelled from — an unregistered fireplace lit, roared, and then refused the trip.
     *
     * <h2>Registration first</h2>
     * <p>Refusing to light an <em>unregistered</em> hearth cannot deadlock, because naming one with a
     * name tag requires it to be <em>cold</em> (see {@code FlooFireplaceBlock.useItemOn}). The order is
     * therefore always: place, name, light.
     *
     * <h2>Re-stoking</h2>
     * <p>A hearth that is already burning is never refused. Powder thrown onto a live fire resets it
     * to {@link FlooFlamesBlock#MAX_CHARGES} and pushes the hearth's own countdown back to full, and
     * it costs a pinch like any other lighting.
     *
     * <p>It used to refuse a hearth that was lit and already at full charges, on the reasoning that
     * the powder would buy nothing. That was wrong in the case that matters: the charges say how many
     * hops are left, the hearth timer says how long the fire lasts, and they run down independently.
     * A hearth lit eighty seconds ago still has all three hops and about ten seconds of life, and
     * "already as green as it will burn" was exactly the wrong thing to tell someone about to lose it.
     * Re-stoking is now the honest answer, and paying for it is what stops a hearth being free to
     * keep alive forever.
     */
    private static InteractionResult igniteHearth(ServerLevel level, BlockPos pos, BlockState state,
                                                  ItemStack stack, Player player) {
        if (!ModuleManager.isEnabled(Module.FLOO_NETWORK)) {
            player.displayClientMessage(
                    Component.translatable("floo.wizards_and_beasts.fail.module_off"), true);
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof FlooFireplaceBlockEntity be)) {
            return InteractionResult.SUCCESS;
        }
        if (!be.isRegistered()) {
            player.displayClientMessage(
                    Component.translatable("floo.wizards_and_beasts.fail.unregistered"), true);
            return InteractionResult.SUCCESS;
        }

        Direction facing = state.getValue(FlooFireplaceBlock.FACING);
        boolean wasLit = state.getValue(FlooFireplaceBlock.LIT);
        if (!FlooFlamesBlock.restoke(level, pos, facing)) {
            player.displayClientMessage(
                    Component.translatable("floo.wizards_and_beasts.fail.obstructed"), true);
            return InteractionResult.SUCCESS;
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        if (!wasLit) {
            level.setBlock(pos, state.setValue(FlooFireplaceBlock.LIT, true), 3);
        }
        be.setLitTicksRemaining(FlooFireplaceBlockEntity.litTimeoutTicks());
        FlooBlockSyncS2CPayload.sendToNear(level, pos,
                true, FlooFireplaceBlockEntity.litTimeoutTicks());
        // LORE: the flames roar up and turn emerald green.
        level.playSound(null, pos, ModSounds.FLOO_IGNITE.get(), SoundSource.BLOCKS, 1.0f, 0.9f);
        level.playSound(null, pos, ModSounds.FLOO_WHOOSH.get(), SoundSource.BLOCKS, 0.9f, 0.8f);
        FlooCues.ignite(level, pos);
        FlooCues.ignite(level, FlooFlamePlacement.flamePos(pos, facing));
        // Quieter line for a top-up than for a cold hearth catching: the player can see the fire, so
        // the thing worth saying is that the pinch was not wasted.
        player.displayClientMessage(Component.translatable(wasLit
                ? "floo.wizards_and_beasts.restoked"
                : "floo.wizards_and_beasts.lit"), true);
        return InteractionResult.SUCCESS;
    }

}
