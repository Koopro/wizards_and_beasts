package at.koopro.wizardsandbeasts.floo;

import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlock;
import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlockEntity;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.floo.FlooArrivalEffectsS2CPayload;
import at.koopro.wizardsandbeasts.network.floo.FlooBlockSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class FlooTravelHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private FlooTravelHandler() {
    }

    public static void handleTravel(@NonNull ServerPlayer player, @NonNull String targetAddress) {
        if (!ModuleManager.isEnabled(Module.FLOO_NETWORK)) return;

        FlooFireplaceBlockEntity origin = findNearbyLitFireplace(player);
        if (origin == null) return;

        FlooNetworkManager manager = FlooNetworkManager.get((ServerLevel) player.level());

        String actualAddress = rollMisfire(player, targetAddress, origin, manager);

        FlooRegistryEntry dest = manager.getEntry(actualAddress);
        if (dest == null || !dest.isEnabled()) {
            player.displayClientMessage(Component.literal("That connection has been sealed."), true);
            return;
        }

        resetOriginFireplace((ServerLevel) player.level(), origin);

        ServerLevel destLevel = resolveLevel(player, dest.dimension());
        if (destLevel == null) return;

        BlockPos destPos = dest.blockPos().above();
        TeleportTransition transition = new TeleportTransition(
                destLevel,
                Vec3.atCenterOf(destPos),
                Vec3.ZERO,
                player.getYRot(),
                player.getXRot(),
                Set.of(),
                entity -> {});
        player.teleport(transition);

        player.addEffect(new MobEffectInstance(ModEffects.DISORIENTED, 100, 0, false, true, true));

        BlockState destBlockState = destLevel.getBlockState(dest.blockPos());
        if (destBlockState.hasProperty(FlooFireplaceBlock.LIT)
                && !destBlockState.getValue(FlooFireplaceBlock.LIT)
                && destLevel.getBlockEntity(dest.blockPos()) instanceof FlooFireplaceBlockEntity destBe) {
            destLevel.setBlock(dest.blockPos(), destBlockState.setValue(FlooFireplaceBlock.LIT, true), 3);
            destBe.setLitTicksRemaining(100);
            FlooBlockSyncS2CPayload.sendToNear(destLevel, dest.blockPos(), true, 100);
        }

        boolean wasMisfire = !actualAddress.equalsIgnoreCase(targetAddress);
        if (wasMisfire) {
            player.sendSystemMessage(
                    Component.literal("You meant to say ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal("\"" + targetAddress + "\"")
                                    .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC))
                            .append(Component.literal(" — but stumble out at ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal("\"" + actualAddress + "\"").withStyle(ChatFormatting.GOLD))
                            .append(Component.literal(" instead, covered in soot.").withStyle(ChatFormatting.GRAY)));
        } else {
            player.sendSystemMessage(
                    Component.literal("You step out of the fireplace at ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal("\"" + actualAddress + "\"").withStyle(ChatFormatting.GOLD))
                            .append(Component.literal(", slightly dizzy.").withStyle(ChatFormatting.GRAY)));
        }

        destLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                destPos.getX() + 0.5, destPos.getY() + 0.5, destPos.getZ() + 0.5,
                12, 0.3, 0.3, 0.3, 0.02);
        destLevel.playSound(null, destPos, ModSounds.FLOO_LAND.get(), SoundSource.BLOCKS, 0.9f, 1.0f);

        manager.logTravel(player, origin.getNetworkAddress(), actualAddress);
        FlooArrivalEffectsS2CPayload.sendToNear(destLevel, dest.blockPos());

        Set<String> visited = new HashSet<>(player.getData(ModAttachments.FLOO_VISITED_DESTINATIONS.get()));
        visited.add(actualAddress.toLowerCase(Locale.ROOT));
        player.setData(ModAttachments.FLOO_VISITED_DESTINATIONS.get(), visited);
    }

    public static void forceTeleport(@NonNull ServerPlayer player, @NonNull String address) {
        FlooNetworkManager manager = FlooNetworkManager.get((ServerLevel) player.level());
        FlooRegistryEntry dest = manager.getEntry(address);
        if (dest == null) {
            player.displayClientMessage(Component.literal("Unknown Floo address: " + address), true);
            return;
        }
        ServerLevel destLevel = resolveLevel(player, dest.dimension());
        if (destLevel == null) return;

        BlockPos destPos = dest.blockPos().above();
        TeleportTransition transition = new TeleportTransition(
                destLevel,
                Vec3.atCenterOf(destPos),
                Vec3.ZERO,
                player.getYRot(),
                player.getXRot(),
                Set.of(),
                entity -> {});
        player.teleport(transition);
        player.addEffect(new MobEffectInstance(ModEffects.DISORIENTED, 100, 0, false, true, true));
        destLevel.playSound(null, destPos, ModSounds.FLOO_LAND.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
        manager.logTravel(player, "(forced)", address);
    }

    @Nullable
    private static FlooFireplaceBlockEntity findNearbyLitFireplace(@NonNull ServerPlayer player) {
        BlockPos center = player.blockPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos check = center.offset(dx, dy, dz);
                    BlockState state = player.level().getBlockState(check);
                    if (state.is(ModBlocks.FLOO_FIREPLACE.get()) && state.getValue(FlooFireplaceBlock.LIT)) {
                        if (player.level().getBlockEntity(check) instanceof FlooFireplaceBlockEntity be
                                && be.isRegistered() && be.isEnabled()) {
                            return be;
                        }
                    }
                }
            }
        }
        return null;
    }

    @NonNull
    private static String rollMisfire(@NonNull ServerPlayer player, @NonNull String intendedAddress,
                                       @NonNull FlooFireplaceBlockEntity origin,
                                       @NonNull FlooNetworkManager manager) {
        if (player.getRandom().nextFloat() >= 0.10f) return intendedAddress;

        List<FlooRegistryEntry> alternatives = manager.getDestinations(player).stream()
                .filter(FlooRegistryEntry::isEnabled)
                .filter(e -> !e.networkAddress().equalsIgnoreCase(intendedAddress))
                .filter(e -> !e.networkAddress().equalsIgnoreCase(origin.getNetworkAddress()))
                .toList();

        if (alternatives.isEmpty()) return intendedAddress;

        FlooRegistryEntry misfireTarget = alternatives.get(player.getRandom().nextInt(alternatives.size()));
        LOGGER.info("[FlooNetwork] {} misfired — intended \"{}\" landed at \"{}\"",
                player.getName().getString(), intendedAddress, misfireTarget.networkAddress());
        return misfireTarget.networkAddress();
    }

    private static void resetOriginFireplace(@NonNull ServerLevel level, @NonNull FlooFireplaceBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(FlooFireplaceBlock.LIT)) {
            level.setBlock(pos, state.setValue(FlooFireplaceBlock.LIT, false), 3);
        }
        be.setLitTicksRemaining(0);
        FlooBlockSyncS2CPayload.sendToNear(level, pos, false, 0);
    }

    @Nullable
    private static ServerLevel resolveLevel(@NonNull ServerPlayer player, @NonNull Identifier dimensionId) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel result = ((ServerLevel) player.level()).getServer().getLevel(key);
        if (result == null) {
            LOGGER.warn("[FlooNetwork] Destination dimension not found: {}", dimensionId);
        }
        return result;
    }
}
