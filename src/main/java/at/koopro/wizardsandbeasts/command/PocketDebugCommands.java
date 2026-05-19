package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.trunk.TrunkAccessMode;
import at.koopro.wizardsandbeasts.trunk.TrunkArchetype;
import at.koopro.wizardsandbeasts.trunk.ExtensionCharmService;
import at.koopro.wizardsandbeasts.trunk.TrunkRecord;
import at.koopro.wizardsandbeasts.trunk.TrunkTier;
import at.koopro.wizardsandbeasts.trunk.TrunkRegistryData;
import at.koopro.wizardsandbeasts.registry.ModDimensions;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class PocketDebugCommands {

    private PocketDebugCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("pocket")
                .then(Commands.literal("enter")
                        .then(Commands.argument("archetype", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(TrunkArchetype.values()).map(TrunkArchetype::getSerializedName), builder))
                                .executes(ctx -> enterPocket(
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "archetype")))))
                .then(Commands.literal("exit")
                        .executes(ctx -> exitPocket(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("info")
                        .executes(ctx -> pocketInfo(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("reset")
                        .executes(ctx -> resetPocket(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("list")
                        .executes(ctx -> listPockets(ctx.getSource().getPlayerOrException())));
    }

    private static UUID debugCaseId(ServerPlayer player, TrunkArchetype archetype) {
        return UUID.nameUUIDFromBytes((player.getUUID().toString() + archetype.getSerializedName()).getBytes());
    }

    private static int enterPocket(ServerPlayer player, String archetypeName) {
        TrunkArchetype archetype = Arrays.stream(TrunkArchetype.values())
                .filter(a -> a.getSerializedName().equals(archetypeName))
                .findFirst()
                .orElse(null);
        if (archetype == null) {
            player.sendSystemMessage(Component.literal("Unknown archetype: " + archetypeName).withStyle(ChatFormatting.RED));
            return 0;
        }
        UUID caseId = debugCaseId(player, archetype);
        TrunkRecord record = ExtensionCharmService.getOrCreatePocket(
                player, caseId, archetype, "debug", TrunkAccessMode.SEALED, false, false, TrunkTier.TIER_3);
        ExtensionCharmService.enterPocket(player, record);
        return 1;
    }

    private static int exitPocket(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)
                || !level.dimension().equals(ModDimensions.EXTENSION_REALM)) {
            player.sendSystemMessage(Component.literal("Not in pocket realm.").withStyle(ChatFormatting.RED));
            return 0;
        }
        ExtensionCharmService.exitPocket(player);
        return 1;
    }

    private static int pocketInfo(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)
                || !level.dimension().equals(ModDimensions.EXTENSION_REALM)) {
            player.sendSystemMessage(Component.literal("Not in pocket realm.").withStyle(ChatFormatting.RED));
            return 0;
        }
        TrunkRegistryData data = TrunkRegistryData.get(level);
        TrunkRecord record = data.getPocketAtPos(player.blockPosition()).orElse(null);
        if (record == null) {
            player.sendSystemMessage(Component.literal("No pocket record at this position.").withStyle(ChatFormatting.RED));
            return 0;
        }
        player.sendSystemMessage(Component.literal("[Pocket Info]").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("  ID: " + record.pocketId()).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  Name: " + record.pocketName()).withStyle(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  Archetype: " + record.archetype().getSerializedName()).withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("  Radius: " + record.pocketRadius()).withStyle(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  Access: " + record.accessMode()).withStyle(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  Biomes: " + String.join(", ", record.biomeZones())).withStyle(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("  Initialized: " + data.isInitialized(record.pocketId())).withStyle(ChatFormatting.WHITE));
        return 1;
    }

    private static int resetPocket(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)
                || !level.dimension().equals(ModDimensions.EXTENSION_REALM)) {
            player.sendSystemMessage(Component.literal("Not in pocket realm.").withStyle(ChatFormatting.RED));
            return 0;
        }
        TrunkRegistryData data = TrunkRegistryData.get(level);
        TrunkRecord record = data.getPocketAtPos(player.blockPosition()).orElse(null);
        if (record == null) {
            player.sendSystemMessage(Component.literal("No pocket record at this position.").withStyle(ChatFormatting.RED));
            return 0;
        }
        data.markUninitialized(record.pocketId());
        player.sendSystemMessage(Component.literal("Pocket reset — shell regenerates on next enter.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int listPockets(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        TrunkRegistryData data = TrunkRegistryData.get(level);
        List<TrunkRecord> all = data.getAllPockets();
        if (all.isEmpty()) {
            player.sendSystemMessage(Component.literal("No pocket records.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        player.sendSystemMessage(Component.literal("[Pockets — " + all.size() + "]").withStyle(ChatFormatting.GOLD));
        for (TrunkRecord r : all) {
            player.sendSystemMessage(
                    Component.literal("  " + r.pocketId().toString().substring(0, 8)).withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(" " + r.archetype().getSerializedName()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" r=" + r.pocketRadius()).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" " + r.pocketName()).withStyle(ChatFormatting.YELLOW)));
        }
        return all.size();
    }
}
