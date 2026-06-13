package at.koopro.wizardsandbeasts.trunk;

import at.koopro.wizardsandbeasts.event.trunk.PocketDimensionEvents;
import at.koopro.wizardsandbeasts.network.trunk.PocketStatusS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Set;
import java.util.UUID;

public final class ExtensionCharmService {

    private static final Logger LOGGER = LogUtils.getLogger();

    static final int GRID_SPACING = 512;
    static final int FLOOR_Y = 64;

    private ExtensionCharmService() {}

    public static TrunkRecord getOrCreatePocket(ServerPlayer player,
                                                UUID caseId,
                                                TrunkArchetype archetype,
                                                String templateId,
                                                TrunkAccessMode accessMode,
                                                boolean muggleWorthy,
                                                boolean lockedExternally,
                                                TrunkTier tier) {
        if (!(player.level() instanceof ServerLevel playerLevel)) {
            throw new IllegalStateException("Pocket operations require ServerLevel context");
        }
        TrunkRegistryData data = TrunkRegistryData.get(playerLevel);

        TrunkRecord existing = data.getCasePocket(caseId).orElse(null);
        if (existing != null) {
            if (existing.owner().equals(player.getUUID())) {
                data.updateRecord(existing.pocketId(), record ->
                        record.withArchetype(archetype).withTemplateId(templateId).withAccessMode(accessMode)
                              .withMuggleWorthy(muggleWorthy).withLockedExternally(lockedExternally));
                return data.getPocket(existing.pocketId()).orElse(existing);
            }
            return existing;
        }

        BlockPos spawn = computePocketSpawn(caseId);
        TrunkRecord created = TrunkRecord.create(player.getUUID(), archetype, templateId, spawn, tier.maxRadius());
        created = created.withAccessMode(accessMode);
        data.putRecord(created);
        data.bindCase(caseId, created.pocketId());
        return created;
    }

    public static void enterPocket(ServerPlayer player, TrunkRecord record) {
        if (!(player.level() instanceof ServerLevel sourceLevel)) return;

        TrunkRegistryData data = TrunkRegistryData.get(sourceLevel);
        data.saveReturnPosition(player.getUUID(), player.blockPosition(), sourceLevel.dimension());

        ServerLevel target = sourceLevel.getServer().getLevel(ModDimensions.EXTENSION_REALM);
        if (target == null) {
            PocketStatusS2CPayload.send(player, "Extension realm is missing from datapacks.");
            return;
        }
        if (!record.canAccess(player.getUUID())) {
            PocketStatusS2CPayload.send(player, "This case is bound to a private pocket.");
            return;
        }

        PocketShellGenerator.ensurePocketShell(target, record, data);

        // SCAMANDER: spawn on shed roof trapdoor (cy+5); others: floor trapdoor (cy).
        BlockPos spawn = record.archetype() == TrunkArchetype.SCAMANDER_SANCTUARY
                ? new BlockPos(record.spawnPos().getX(), record.spawnPos().getY() + 5, record.spawnPos().getZ())
                : record.spawnPos();
        TeleportTransition transition = new TeleportTransition(
                target, Vec3.atCenterOf(spawn), Vec3.ZERO,
                0.0f, 0.0f, Set.of(), entity -> {});
        player.teleport(transition);

        // Play trapdoor open immediately; ladder creak follows 2 ticks later via tick handler.
        target.playSound(null, spawn, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0f, 1.0f);
        PocketDimensionEvents.scheduleLadderSound(player.getUUID(), 2);

        PocketStatusS2CPayload.send(player, "Entering " + record.pocketName());
    }

    public static void exitPocket(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel sourceLevel)) return;

        // Play closing sound while player is still in the realm.
        sourceLevel.playSound(null, player.blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);

        TrunkRegistryData data = TrunkRegistryData.get(sourceLevel);
        ServerLevel returnLevel = sourceLevel.getServer().overworld();
        BlockPos returnPos = BlockPos.ZERO;

        String dimStr = data.getReturnDimension(player.getUUID()).orElse(null);
        BlockPos savedPos = data.getReturnPosition(player.getUUID()).orElse(null);
        if (dimStr != null && savedPos != null) {
            try {
                ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimStr));
                ServerLevel candidate = sourceLevel.getServer().getLevel(dimKey);
                if (candidate != null) {
                    returnLevel = candidate;
                    returnPos = savedPos;
                }
            } catch (Exception ex) {
                LOGGER.warn("[WizardsAndBeasts] Failed to parse saved return dimension '{}' for {} — falling back to overworld spawn",
                        dimStr, player.getUUID(), ex);
            }
        }
        data.clearReturnData(player.getUUID());
        PocketDimensionEvents.cancelLadderSound(player.getUUID());

        BlockPos safe = findSafeSpawn(returnLevel, returnPos);
        TeleportTransition transition = new TeleportTransition(
                returnLevel, Vec3.atCenterOf(safe), Vec3.ZERO,
                player.getYRot(), player.getXRot(), Set.of(), entity -> {});
        player.teleport(transition);
        setInventoryLatchSecured(player, true);
        PocketStatusS2CPayload.send(player, "Returning from extension realm");
    }

    // Called by creature release interactions — wire to creature entity AI
    // when creature entities are implemented.
    public static void releaseToWorld(ServerPlayer player, TrunkRecord trunk) {
        if (!(player.level() instanceof ServerLevel sourceLevel)) return;

        TrunkRegistryData data = TrunkRegistryData.get(sourceLevel);
        ServerLevel returnLevel = sourceLevel.getServer().overworld();
        BlockPos returnPos = BlockPos.ZERO;

        String dimStr = data.getReturnDimension(player.getUUID()).orElse(null);
        BlockPos savedPos = data.getReturnPosition(player.getUUID()).orElse(null);
        if (dimStr != null && savedPos != null) {
            try {
                ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimStr));
                ServerLevel candidate = sourceLevel.getServer().getLevel(dimKey);
                if (candidate != null) {
                    returnLevel = candidate;
                    returnPos = savedPos;
                }
            } catch (Exception ex) {
                LOGGER.warn("[WizardsAndBeasts] Failed to parse saved return dimension '{}' for {} — falling back to overworld spawn",
                        dimStr, player.getUUID(), ex);
            }
        }
        data.clearReturnData(player.getUUID());
        PocketDimensionEvents.cancelLadderSound(player.getUUID());

        BlockPos safe = findSafeSpawn(returnLevel, returnPos);
        TeleportTransition transition = new TeleportTransition(
                returnLevel, Vec3.atCenterOf(safe), Vec3.ZERO,
                player.getYRot(), player.getXRot(), Set.of(), entity -> {});
        player.teleport(transition);

        // Spawn PORTAL particles at exit location to mark the release point.
        returnLevel.sendParticles(ParticleTypes.PORTAL,
                safe.getX() + 0.5, safe.getY() + 1.0, safe.getZ() + 0.5,
                30, 0.3, 0.5, 0.3, 0.1);
        returnLevel.playSound(null, safe, SoundEvents.ENDER_EYE_DEATH, SoundSource.NEUTRAL, 1.0f, 0.9f);

        PocketStatusS2CPayload.send(player, "Released from extension realm");
    }

    /** Sets latchSecured on all EnchantedTrunkItem stacks in the player's inventory. */
    static void setInventoryLatchSecured(ServerPlayer player, boolean secured) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
            if (stack.has(at.koopro.wizardsandbeasts.registry.ModDataComponents.POCKET_CASE_ID.get())) {
                stack.set(at.koopro.wizardsandbeasts.registry.ModDataComponents.POCKET_LATCH_SECURED.get(), secured);
            }
        }
    }

    private static BlockPos computePocketSpawn(UUID pocketId) {
        int xIndex = Math.floorMod((int) pocketId.getMostSignificantBits(), 2048);
        int zIndex = Math.floorMod((int) pocketId.getLeastSignificantBits(), 2048);
        return new BlockPos(xIndex * GRID_SPACING, FLOOR_Y + 2, zIndex * GRID_SPACING);
    }

    private static BlockPos findSafeSpawn(ServerLevel level, BlockPos origin) {
        BlockPos.MutableBlockPos cursor = origin.mutable();
        for (int i = 0; i < 16; i++) {
            if (level.getBlockState(cursor).isAir() && level.getBlockState(cursor.above()).isAir()) {
                return cursor.immutable();
            }
            cursor.move(0, 1, 0);
        }
        return origin;
    }

    /** Delegates to {@link PocketShellGenerator}; kept here so existing callers keep one entry point. */
    public static void applyBiome(ServerLevel level, TrunkRecord record) {
        PocketShellGenerator.applyBiome(level, record);
    }
}
