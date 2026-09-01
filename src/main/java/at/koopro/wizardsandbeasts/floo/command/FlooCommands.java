package at.koopro.wizardsandbeasts.floo.command;

import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlock;
import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlockEntity;
import at.koopro.wizardsandbeasts.floo.FlooAccess;
import at.koopro.wizardsandbeasts.floo.FlooAddress;
import at.koopro.wizardsandbeasts.floo.FlooNetworkManager;
import at.koopro.wizardsandbeasts.floo.FlooRegistryEntry;
import at.koopro.wizardsandbeasts.floo.FlooTravelHandler;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
import at.koopro.wizardsandbeasts.util.ChatReport;

public final class FlooCommands {

    private static final String KEY = "floo.wizards_and_beasts.";

    /** How many travel-log lines {@code /wandb world floo log} prints, and what its title says. */
    private static final int LOG_ENTRIES = 20;

    private FlooCommands() {
    }

    public static @NonNull LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("floo")
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
                .then(Commands.literal("setpublic")
                        .then(Commands.argument("address", StringArgumentType.string())
                                .then(Commands.literal("true")
                                        .executes(ctx -> setPublic(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "address"), true)))
                                .then(Commands.literal("false")
                                        .executes(ctx -> setPublic(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "address"), false)))))
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

    /**
     * @throws CommandSyntaxException when run from console — deliberately propagated rather than
     *         caught and re-worded. Vanilla's {@code getPlayerOrException} already throws a
     *         translated "requires a player" message, and every other command file in this mod lets
     *         it through; the local try/catch here was the only place that answered in hardcoded
     *         English.
     */
    private static int registerFireplace(@NonNull CommandSourceStack source, @NonNull String address,
                                          boolean isPublic) throws CommandSyntaxException {
        if (!ModuleManager.isEnabled(Module.FLOO_NETWORK)) {
            source.sendFailure(moduleDisabledMsg());
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();

        HitResult hit = player.pick(5.0, 1.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.translatable(KEY + "command.not_looking_block"));
            return 0;
        }
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        if (!(player.level().getBlockState(pos).getBlock() instanceof FlooFireplaceBlock)) {
            source.sendFailure(Component.translatable(KEY + "command.not_fireplace"));
            return 0;
        }
        if (!(player.level().getBlockEntity(pos) instanceof FlooFireplaceBlockEntity be)) {
            source.sendFailure(Component.translatable(KEY + "command.no_block_entity"));
            return 0;
        }

        Identifier dimension = player.level().dimension().identifier();
        FlooNetworkManager manager = FlooNetworkManager.get((ServerLevel) player.level());
        FlooRegistryEntry existing = manager.findByPos(dimension, pos);
        if (existing != null && !mayAdminister(source, existing)) {
            source.sendFailure(Component.translatable(KEY + "fail.not_owner"));
            return 0;
        }
        FlooNetworkManager.RegisterResult result = manager.register(address, dimension, pos, isPublic,
                java.util.Optional.of(player.getUUID()));
        switch (result) {
            case ADDRESS_TAKEN -> {
                // The same sentence the name-tag path gives; one address clash, one wording.
                source.sendFailure(Component.translatable(KEY + "fail.address_taken", address));
                return 0;
            }
            case INVALID_ADDRESS -> {
                // The rule that was broken, not a generic refusal — the validator knows which one.
                // No withStyle: the address.* lang values carry their own §c, the way every other
                // Floo message in en_us.json does, and re-colouring here would have been a second
                // place deciding what a refusal looks like.
                source.sendFailure(FlooAddress.validate(address).message());
                return 0;
            }
            case REGISTERED -> { }
        }
        be.setNetworkAddress(FlooAddress.display(address));
        be.setRegistered(true);
        be.setEnabled(true);

        source.sendSuccess(() -> Component.translatable(KEY + "command.registered",
                address, pos.toShortString(), visibility(isPublic)), true);
        return 1;
    }

    /**
     * Move a hearth on or off the public directory.
     *
     * <p>Owner-gated like unregister, and for the same reason: publishing somebody's private home is
     * as much a violation of it as deleting it. The console and this mod's admins are exempt, which
     * is what makes a moderation request answerable.
     */
    private static int setPublic(@NonNull CommandSourceStack source, @NonNull String address,
                                  boolean isPublic) {
        if (!ModuleManager.isEnabled(Module.FLOO_NETWORK)) {
            source.sendFailure(moduleDisabledMsg());
            return 0;
        }
        FlooNetworkManager manager = FlooNetworkManager.get(source.getServer().overworld());
        FlooRegistryEntry entry = manager.getEntry(address);
        if (entry == null) {
            source.sendFailure(unknownAddress(address));
            return 0;
        }
        if (!mayAdminister(source, entry)) {
            source.sendFailure(Component.translatable(KEY + "fail.not_owner"));
            return 0;
        }
        manager.setPublic(address, isPublic);
        source.sendSuccess(() -> Component.translatable(KEY + "command.visibility_set",
                entry.networkAddress(), visibility(isPublic)), true);
        return 1;
    }

    /**
     * Whether {@code source} may act on somebody else's hearth.
     *
     * <p>A non-player source always may — the console is how an operator answers a report about a
     * hearth whose owner has not logged in for a year, and it is the recovery path for every other
     * gate in this mod.
     */
    private static boolean mayAdminister(@NonNull CommandSourceStack source,
                                          @NonNull FlooRegistryEntry entry) {
        ServerPlayer player = source.getPlayer();
        return player == null || FlooAccess.mayAdminister(player, entry);
    }

    private static int unregisterFireplace(@NonNull CommandSourceStack source, @NonNull String address) {
        FlooNetworkManager manager = FlooNetworkManager.get(source.getServer().overworld());
        FlooRegistryEntry entry = manager.getEntry(address);
        if (entry == null) {
            source.sendFailure(unknownAddress(address));
            return 0;
        }
        if (!mayAdminister(source, entry)) {
            source.sendFailure(Component.translatable(KEY + "fail.not_owner"));
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
        source.sendSuccess(() -> Component.translatable(KEY + "command.unregistered", address), true);
        return 1;
    }

    private static int listFireplaces(@NonNull CommandSourceStack source) {
        FlooNetworkManager manager = FlooNetworkManager.get(source.getServer().overworld());
        List<FlooRegistryEntry> all = manager.getAllEntries();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(KEY + "command.list.empty"), false);
            return 1;
        }
        ChatReport.of(Component.translatable(KEY + "command.list.title")).send(source);
        for (FlooRegistryEntry e : all) {
            // The status bracket is its own key so its colour travels with its words: a translator
            // who reorders the row cannot leave "sealed" wearing the green of "enabled".
            Component status = Component.translatable(
                    KEY + (e.isEnabled() ? "command.state.enabled" : "command.state.sealed"),
                    visibility(e.isPublic()));
            source.sendSuccess(() -> Component.translatable(KEY + "command.list.row",
                    e.networkAddress(), status,
                    e.dimension().toString(), e.blockPos().toShortString()), false);
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
            source.sendFailure(unknownAddress(address));
            return 0;
        }
        manager.setEnabled(address, enabled);
        updateBlockEntityEnabled(source, address, manager, enabled);

        source.sendSuccess(() -> Component.translatable(
                KEY + (enabled ? "command.enabled" : "command.sealed"), address), true);
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
            source.sendFailure(unknownAddress(address));
            return 0;
        }
        FlooTravelHandler.forceTeleport(target, address);
        source.sendSuccess(() -> Component.translatable(KEY + "command.teleported",
                target.getDisplayName(), address), true);
        return 1;
    }

    private static int printLog(@NonNull CommandSourceStack source) {
        FlooNetworkManager manager = FlooNetworkManager.get(source.getServer().overworld());
        List<String> log = manager.getRecentLog(LOG_ENTRIES);
        if (log.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(KEY + "command.log.empty"), false);
            return 1;
        }
        ChatReport.of(Component.translatable(KEY + "command.log.title", LOG_ENTRIES)).send(source);
        for (String entry : log) {
            // Deliberately literal. These are operator log lines built and persisted by
            // FlooNetworkManager.logTravel as plain strings in SavedData, matching what went to the
            // server log; translating the display half would leave two spellings of one record, and
            // translating the stored half would change a save format for a diagnostic.
            source.sendSuccess(() -> Component.literal(entry).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    /** The same sentence the blocks give when the module is off, rather than a second wording. */
    private static @NonNull Component moduleDisabledMsg() {
        return Component.translatable(KEY + "fail.module_off");
    }

    /** Said three times over — from unregister, from enable/disable and from teleport. */
    private static @NonNull Component unknownAddress(@NonNull String address) {
        return Component.translatable(KEY + "command.unknown_address", address);
    }

    private static @NonNull Component visibility(boolean isPublic) {
        return Component.translatable(KEY + (isPublic ? "command.visibility.public"
                : "command.visibility.private"));
    }
}
