package at.koopro.wizardsandbeasts.azkaban.command;

import at.koopro.wizardsandbeasts.azkaban.AzkabanDamageTypes;
import at.koopro.wizardsandbeasts.azkaban.attachment.AzkabanTrespasserData;
import at.koopro.wizardsandbeasts.azkaban.structure.AzkabanStructures;
import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.entity.azkaban.DementorEntity;
import at.koopro.wizardsandbeasts.network.azkaban.AzkabanTrespasserSyncPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class AzkabanCommands {

    private AzkabanCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("azkaban")
                .requires(WizardsAndBeastsCommandPermissions.ADMIN)

                .then(Commands.literal("locate")
                        .executes(ctx -> locate(ctx.getSource())))

                .then(Commands.literal("teleport")
                        .executes(ctx -> teleport(ctx.getSource())))

                .then(Commands.literal("force_generate")
                        .executes(ctx -> forceGenerate(ctx.getSource())))

                .then(Commands.literal("dump_info")
                        .executes(ctx -> dumpInfo(ctx.getSource())))

                .then(Commands.literal("summon_dementor")
                        .executes(ctx -> summonDementor(ctx.getSource())))

                .then(Commands.literal("dissipate_nearby")
                        .executes(ctx -> dissipateNearby(ctx.getSource())))

                .then(Commands.literal("tag")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> setTag(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"), true))))

                .then(Commands.literal("untag")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> setTag(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"), false))))

                .then(Commands.literal("chill")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amplifier", IntegerArgumentType.integer(0, 3))
                                        .executes(ctx -> applyChill(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "amplifier"))))))

                .then(Commands.literal("kiss")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> applyKiss(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))));
    }

    // -------------------------------------------------------------------------
    // New worldgen subcommands
    // -------------------------------------------------------------------------

    private static int locate(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        BlockPos center = findCenter(level, BlockPos.containing(src.getPosition()));
        if (center == null) {
            src.sendFailure(Component.literal("No Azkaban Fortress found within search range."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(
                "Azkaban Fortress: " + center.getX() + ", " + center.getY() + ", " + center.getZ()), false);
        return 1;
    }

    private static int teleport(CommandSourceStack src)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();
        BlockPos center = findCenter(level, player.blockPosition());
        if (center == null) {
            src.sendFailure(Component.literal("No Azkaban Fortress found within search range."));
            return 0;
        }
        double aboveY = center.getY() + 40.0;
        player.teleportTo(center.getX() + 0.5, aboveY, center.getZ() + 0.5);
        src.sendSuccess(() -> Component.literal("Teleported above Azkaban Fortress."), true);
        return 1;
    }

    private static int forceGenerate(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        BlockPos center = locateNearest(level, BlockPos.containing(src.getPosition()));
        if (center == null) {
            src.sendFailure(Component.literal("No Azkaban Fortress found within search range to force-generate."));
            return 0;
        }
        ChunkPos target = new ChunkPos(center);
        // Load the chunks the crag/template span so postProcess runs and blocks place.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.getChunk(target.x + dx, target.z + dz);
            }
        }
        src.sendSuccess(() -> Component.literal(
                "Forced generation around Azkaban at chunk " + target), true);
        return 1;
    }

    private static int dumpInfo(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        BlockPos center = findCenter(level, BlockPos.containing(src.getPosition()));
        if (center == null) {
            src.sendFailure(Component.literal("No Azkaban Fortress found within search range."));
            return 0;
        }
        ChunkPos chunkPos = new ChunkPos(center);
        int seaLevel = level.getSeaLevel();
        int seabedY = level.getChunk(chunkPos.x, chunkPos.z)
                .getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG,
                        center.getX(), center.getZ());
        net.minecraft.core.BlockPos spawnPos = level.getRespawnData().pos();
        double dist = Math.sqrt(
                Math.pow(center.getX() - spawnPos.getX(), 2) +
                Math.pow(center.getZ() - spawnPos.getZ(), 2));
        String biomeName = level.getBiome(center)
                .unwrapKey()
                .map(net.minecraft.resources.ResourceKey::toString)
                .orElse("unknown");

        src.sendSuccess(() -> Component.literal(
                "=== Azkaban Info ===\n" +
                "Center: " + center.getX() + ", " + center.getY() + ", " + center.getZ() + "\n" +
                "Chunk: [" + chunkPos.x + ", " + chunkPos.z + "]\n" +
                "Biome: " + biomeName + "\n" +
                "Sea level: " + seaLevel + "\n" +
                "Seabed Y: " + seabedY + "\n" +
                "Distance from spawn: " + (int) dist + " blocks"), false);
        return 1;
    }

    /**
     * Returns the Azkaban center: the cached/saved center if the structure has
     * generated, otherwise the nearest placement found via a structure search
     * (random-spread placement has no seed-derivable location). May be null.
     */
    private static @Nullable BlockPos findCenter(ServerLevel level, BlockPos from) {
        BlockPos cached = AzkabanStructures.getCenter(level);
        if (cached != null) return cached;
        return locateNearest(level, from);
    }

    /** Structure search for the nearest Azkaban Fortress placement. */
    private static @Nullable BlockPos locateNearest(ServerLevel level, BlockPos from) {
        Holder.Reference<Structure> holder = level.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(AzkabanStructures.AZKABAN_FORTRESS_KEY);
        var result = level.getChunkSource().getGenerator().findNearestMapStructure(
                level, HolderSet.direct(holder), from, 200, false);
        return result != null ? result.getFirst() : null;
    }

    // -------------------------------------------------------------------------
    // Existing subcommands (unchanged)
    // -------------------------------------------------------------------------

    private static int summonDementor(CommandSourceStack src)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer executor = src.getPlayerOrException();
        ServerLevel level = (ServerLevel) executor.level();
        DementorEntity dementor = new DementorEntity(ModEntities.DEMENTOR.get(), level);
        dementor.setPos(executor.getX(), executor.getY(), executor.getZ());
        level.addFreshEntity(dementor);
        src.sendSuccess(() -> Component.literal("Dementor summoned."), true);
        return 1;
    }

    private static int dissipateNearby(CommandSourceStack src)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer executor = src.getPlayerOrException();
        ServerLevel level = (ServerLevel) executor.level();
        AABB box = executor.getBoundingBox().inflate(32);
        List<DementorEntity> dementors = level.getEntitiesOfClass(DementorEntity.class, box, LivingEntity::isAlive);
        for (DementorEntity d : dementors) {
            d.setDissipating(true);
            d.hurt(level.damageSources().source(AzkabanDamageTypes.DEMENTOR_DISSIPATE), Float.MAX_VALUE);
        }
        int count = dementors.size();
        src.sendSuccess(() -> Component.literal("Dissipated " + count + " Dementor(s)."), true);
        return count;
    }

    private static int setTag(CommandSourceStack src, ServerPlayer target, boolean tagged) {
        target.setData(ModAttachments.AZKABAN_TRESPASSER_TAG.get(),
                new AzkabanTrespasserData(tagged));
        PacketDistributor.sendToPlayer(target, new AzkabanTrespasserSyncPayload(tagged));
        src.sendSuccess(() -> Component.literal(
                (tagged ? "Tagged" : "Untagged") + " " + target.getName().getString() +
                " as Azkaban trespasser."), true);
        return 1;
    }

    private static int applyChill(CommandSourceStack src, ServerPlayer target, int amplifier) {
        target.addEffect(new MobEffectInstance(ModEffects.DEMENTOR_CHILL, 200, amplifier, false, true, true));
        src.sendSuccess(() -> Component.literal(
                "Applied DEMENTOR_CHILL amp=" + amplifier + " to " + target.getName().getString()), true);
        return 1;
    }

    private static int applyKiss(CommandSourceStack src, ServerPlayer target) {
        if (!(target.level() instanceof ServerLevel sl)) return 0;
        target.addEffect(new MobEffectInstance(
                ModEffects.SOUL_DRAINED, Integer.MAX_VALUE / 20, 0, false, true, true));
        target.hurt(sl.damageSources().source(AzkabanDamageTypes.DEMENTOR_KISS), 4.0f);
        src.sendSuccess(() -> Component.literal(
                target.getName().getString() + " was Kissed by a Dementor."), true);
        return 1;
    }
}
