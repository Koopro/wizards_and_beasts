package at.koopro.wizardsandbeasts.floo.command;

import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlock;
import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlockEntity;
import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.floo.FlooNetworkManager;
import at.koopro.wizardsandbeasts.floo.FlooRegistryEntry;
import at.koopro.wizardsandbeasts.floo.FlooTravelHandler;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class FlooCommands {

    private FlooCommands() {
    }

    public static @NonNull LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("floo")
                .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
                .then(Commands.literal("register")
                        .then(Commands.argument("address", StringArgumentType.string())
                                .executes(ctx -> registerFireplace(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "address"), true))
                                .then(Commands.literal("public")
                                        .executes(ctx -> registerFireplace(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "address"), true)))
                                .then(Commands.literal("private")
                                        .executes(ctx -> registerFireplace(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "address"), false)))))
                .then(Commands.literal("unregister")
                        .then(Commands.argument("address", StringArgumentType.string())
                                .executes(ctx -> unregisterFireplace(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "address")))))
                .then(Commands.literal("list")
                        .executes(ctx -> listFireplaces(ctx.getSource())))
                .then(Commands.literal("enable")
                        .then(Commands.argument("address", StringArgumentType.string())
                                .executes(ctx -> setEnabled(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "address"), true))))
                .then(Commands.literal("disable")
                        .then(Commands.argument("address", StringArgumentType.string())
                                .executes(ctx -> setEnabled(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "address"), false))))
                .then(Commands.literal("teleport")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("address", StringArgumentType.string())
                                        .executes(ctx -> forceTeleport(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "address"))))))
                .then(Commands.literal("log")
                        .executes(ctx -> printLog(ctx.getSource())));
    }

    private static int registerFireplace(@NonNull CommandSourceStack source, @NonNull String address,
                                          boolean isPublic) {
        if (!ModuleManager.isEnabled(Module.FLOO_NETWORK)) {
            source.sendFailure(moduleDisabledMsg());
            return 0;
        }
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Must be a player."));
            return 0;
        }

        HitResult hit = player.pick(5.0, 1.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("Not looking at a block.").withStyle(ChatFormatting.RED));
            return 0;
        }
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        if (!(player.level().getBlockState(pos).getBlock() instanceof FlooFireplaceBlock)) {
            source.sendFailure(Component.literal("Not looking at a Floo Fireplace.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!(player.level().getBlockEntity(pos) instanceof FlooFireplaceBlockEntity be)) {
            source.sendFailure(Component.literal("Block entity not found.").withStyle(ChatFormatting.RED));
            return 0;
        }

        Identifier dimension = player.level().dimension().identifier();
        FlooNetworkManager manager = FlooNetworkManager.get((ServerLevel) player.level());
        boolean success = manager.register(address, dimension, pos, isPublic);
        if (!success) {
            source.sendFailure(Component.literal("Address \"" + address + "\" is already taken.").withStyle(ChatFormatting.RED));
            return 0;
        }
        be.setNetworkAddress(address);
        be.setRegistered(true);
        be.setEnabled(true);

        String visibility = isPublic ? "public" : "private";
        source.sendSuccess(() -> Component.literal("Registered ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("\"" + address + "\"").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" at " + pos + " [" + visibility + "]").withStyle(ChatFormatting.GRAY)), true);
        return 1;
    }

    private static int unregisterFireplace(@NonNull CommandSourceStack source, @NonNull String address) {
        FlooNetworkManager manager = FlooNetworkManager.get(source.getServer().overworld());
        FlooRegistryEntry entry = manager.getEntry(address);
        if (entry == null) {
            source.sendFailure(Component.literal("No fireplace registered as \"" + address + "\".").withStyle(ChatFormatting.RED));
            return 0;
        }

        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, entry.dimension());
        ServerLevel entryLevel = source.getServer().getLevel(dimKey);
        if (entryLevel != null && entryLevel.isLoaded(entry.blockPos())) {
            if (entryLevel.getBlockEntity(entry.blockPos()) instanceof FlooFireplaceBlockEntity be) {
                be.setRegistered(false);
                be.setNetworkAddress("");
            }
        }

        manager.unregister(address);
        source.sendSuccess(() -> Component.literal("Unregistered ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("\"" + address + "\"").withStyle(ChatFormatting.YELLOW)), true);
        return 1;
    }

    private static int listFireplaces(@NonNull CommandSourceStack source) {
        FlooNetworkManager manager = FlooNetworkManager.get(source.getServer().overworld());
        List<FlooRegistryEntry> all = manager.getAllEntries();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No fireplaces registered.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("=== Floo Network ===").withStyle(ChatFormatting.GOLD), false);
        for (FlooRegistryEntry e : all) {
            ChatFormatting stateColor = e.isEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED;
            String state = e.isEnabled() ? "enabled" : "sealed";
            String vis = e.isPublic() ? "public" : "private";
            source.sendSuccess(() -> Component.literal("  ")
                    .append(Component.literal(e.networkAddress()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" [" + state + ", " + vis + "] ").withStyle(stateColor))
                    .append(Component.literal(e.dimension() + " " + e.blockPos()).withStyle(ChatFormatting.DARK_GRAY)), false);
        }
        return 1;
    }

    private static int setEnabled(@NonNull CommandSourceStack source, @NonNull String address, boolean enabled) {
        if (!ModuleManager.isEnabled(Module.FLOO_NETWORK)) {
            source.sendFailure(moduleDisabledMsg());
            return 0;
        }
        FlooNetworkManager manager = FlooNetworkManager.get(source.getServer().overworld());
        if (manager.getEntry(address) == null) {
            source.sendFailure(Component.literal("No fireplace registered as \"" + address + "\".").withStyle(ChatFormatting.RED));
            return 0;
        }
        manager.setEnabled(address, enabled);
        updateBlockEntityEnabled(source, address, manager, enabled);

        String action = enabled ? "Enabled" : "Sealed";
        source.sendSuccess(() -> Component.literal(action + " ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("\"" + address + "\"").withStyle(ChatFormatting.AQUA)), true);
        return 1;
    }

    private static void updateBlockEntityEnabled(@NonNull CommandSourceStack source, @NonNull String address,
                                                  @NonNull FlooNetworkManager manager, boolean enabled) {
        FlooRegistryEntry entry = manager.getEntry(address);
        if (entry == null) return;
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, entry.dimension());
        ServerLevel entryLevel = source.getServer().getLevel(dimKey);
        if (entryLevel != null && entryLevel.isLoaded(entry.blockPos())) {
            if (entryLevel.getBlockEntity(entry.blockPos()) instanceof FlooFireplaceBlockEntity be) {
                be.setEnabled(enabled);
            }
        }
    }

    private static int forceTeleport(@NonNull CommandSourceStack source, @NonNull ServerPlayer target,
                                      @NonNull String address) {
        if (!ModuleManager.isEnabled(Module.FLOO_NETWORK)) {
            source.sendFailure(moduleDisabledMsg());
            return 0;
        }
        FlooNetworkManager manager = FlooNetworkManager.get(source.getServer().overworld());
        if (manager.getEntry(address) == null) {
            source.sendFailure(Component.literal("No fireplace registered as \"" + address + "\".").withStyle(ChatFormatting.RED));
            return 0;
        }
        FlooTravelHandler.forceTeleport(target, address);
        source.sendSuccess(() -> Component.literal("Floo-teleported ")
                .withStyle(ChatFormatting.GRAY)
                .append(target.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" to \"" + address + "\"").withStyle(ChatFormatting.GRAY)), true);
        return 1;
    }

    private static int printLog(@NonNull CommandSourceStack source) {
        FlooNetworkManager manager = FlooNetworkManager.get(source.getServer().overworld());
        List<String> log = manager.getRecentLog(20);
        if (log.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No travel log entries.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("=== Floo Travel Log (last 20) ===").withStyle(ChatFormatting.GOLD), false);
        for (String entry : log) {
            source.sendSuccess(() -> Component.literal(entry).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static @NonNull Component moduleDisabledMsg() {
        return Component.literal("The Floo Network module is currently disabled.").withStyle(ChatFormatting.RED);
    }
}
