package at.koopro.wizardsandbeasts.floo;

import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlock;
import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlockEntity;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.floo.call.FlooCallService;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.floo.FlooArrivalEffectsS2CPayload;
import at.koopro.wizardsandbeasts.network.floo.FlooBlockSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.floo.FlooTransitS2CPayload;
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

        if (FlooCallService.isInCall(player.getUUID())) {
            player.displayClientMessage(
                    Component.literal("You are mid-Floo-call. Pull your head out of the fire first."), true);
            return;
        }

        FlooFireplaceBlockEntity origin = findNearbyLitFireplace(player);
        if (origin == null) return;

        FlooNetworkManager manager = FlooNetworkManager.get((ServerLevel) player.level());

        String actualAddress = rollMisfire(player, targetAddress, origin, manager);

        FlooRegistryEntry dest = manager.getEntry(actualAddress);
        if (dest == null || !dest.isEnabled()) {
            player.displayClientMessage(Component.literal("That connection has been sealed."), true);
            return;
        }

        ServerLevel destLevel = resolveLevel(player, dest.dimension());
        if (destLevel == null) return;

        BlockPos destPos = findSafeArrival(destLevel, dest.blockPos());
        if (destPos == null) {
            player.displayClientMessage(
                    Component.literal("The grate at the far end is blocked — you cannot step through."), true);
            return;
        }

        // Throw a pinch of Floo Powder to travel (free in creative).
        if (!consumeOnePowder(player)) {
            player.displayClientMessage(Component.literal("You have no Floo Powder."), true);
            return;
        }

        resetOriginFireplace((ServerLevel) player.level(), origin);

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

        boolean wasMisfire = !actualAddress.equalsIgnoreCase(targetAddress);
        applyTravelSickness(player, destLevel, destPos, wasMisfire);

        BlockState destBlockState = destLevel.getBlockState(dest.blockPos());
        if (destBlockState.hasProperty(FlooFireplaceBlock.LIT)
                && !destBlockState.getValue(FlooFireplaceBlock.LIT)
                && destLevel.getBlockEntity(dest.blockPos()) instanceof FlooFireplaceBlockEntity destBe) {
            destLevel.setBlock(dest.blockPos(), destBlockState.setValue(FlooFireplaceBlock.LIT, true), 3);
            destBe.setLitTicksRemaining(100);
            FlooBlockSyncS2CPayload.sendToNear(destLevel, dest.blockPos(), true, 100);
        }

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

    /**
     * Floo travel is famously unpleasant: spinning, dizzy, and out covered in soot.
     * Sends the spin overlay, dusts the arrival in ash, and rolls a stumble.
     */
    private static void applyTravelSickness(@NonNull ServerPlayer player, @NonNull ServerLevel destLevel,
                                            @NonNull BlockPos destPos, boolean wasMisfire) {
        FlooTransitS2CPayload.send(player, wasMisfire ? 26 : 18);

        int slowTicks = wasMisfire ? 80 : 50;
        player.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS,
                slowTicks, 0, false, false, true));

        // Soot/ash cloud around the arriving traveller.
        destLevel.sendParticles(ParticleTypes.SMOKE,
                player.getX(), player.getY() + 0.6, player.getZ(),
                wasMisfire ? 30 : 16, 0.3, 0.5, 0.3, 0.02);
        destLevel.sendParticles(ParticleTypes.ASH,
                player.getX(), player.getY() + 0.4, player.getZ(),
                wasMisfire ? 24 : 12, 0.3, 0.4, 0.3, 0.01);

        // Stumble: clumsier travellers (and misfires) lurch out of the grate.
        float stumbleChance = wasMisfire ? 0.6f : 0.2f;
        if (player.getRandom().nextFloat() < stumbleChance) {
            double yaw = Math.toRadians(player.getYRot());
            player.push(-Math.sin(yaw) * 0.35, 0.05, Math.cos(yaw) * 0.35);
            player.hurtMarked = true;
            player.sendSystemMessage(Component.literal("You tumble out of the grate in a heap of soot.")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    /** Finds the lowest unobstructed 2-block-tall standing spot above the destination grate. */
    @Nullable
    private static BlockPos findSafeArrival(@NonNull ServerLevel level, @NonNull BlockPos firePos) {
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos feet = firePos.above(dy);
            if (isPassable(level, feet) && isPassable(level, feet.above())) {
                return feet;
            }
        }
        return null;
    }

    private static boolean isPassable(@NonNull ServerLevel level, @NonNull BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** Consumes one Floo Powder from the player's inventory; true if paid (or creative). */
    private static boolean consumeOnePowder(@NonNull ServerPlayer player) {
        if (player.getAbilities().instabuild) return true;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (stack.is(MiscItemRegistry.FLOO_POWDER.get())) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static FlooFireplaceBlockEntity findNearbyLitFireplace(@NonNull ServerPlayer player) {
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

        // LORE: a mumbled destination tends to spit you out somewhere unsavoury
        // (Harry's "Diagonally" → Knockturn Alley). Weight dark addresses heavier.
        FlooRegistryEntry misfireTarget = pickWeightedMisfire(player, alternatives);
        LOGGER.info("[FlooNetwork] {} misfired — intended \"{}\" landed at \"{}\"",
                player.getName().getString(), intendedAddress, misfireTarget.networkAddress());
        return misfireTarget.networkAddress();
    }

    private static final List<String> DARK_ADDRESS_KEYWORDS =
            List.of("knockturn", "borgin", "burkes", "azkaban", "riddle", "malfoy", "spinner");

    private static boolean isDarkAddress(@NonNull FlooRegistryEntry entry) {
        String addr = entry.networkAddress().toLowerCase(Locale.ROOT);
        return DARK_ADDRESS_KEYWORDS.stream().anyMatch(addr::contains);
    }

    @NonNull
    private static FlooRegistryEntry pickWeightedMisfire(@NonNull ServerPlayer player,
                                                         @NonNull List<FlooRegistryEntry> alternatives) {
        int totalWeight = 0;
        for (FlooRegistryEntry e : alternatives) {
            totalWeight += isDarkAddress(e) ? 4 : 1;
        }
        int roll = player.getRandom().nextInt(totalWeight);
        for (FlooRegistryEntry e : alternatives) {
            roll -= isDarkAddress(e) ? 4 : 1;
            if (roll < 0) return e;
        }
        return alternatives.getLast();
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
