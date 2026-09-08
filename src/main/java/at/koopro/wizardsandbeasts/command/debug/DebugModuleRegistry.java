package at.koopro.wizardsandbeasts.command.debug;

import at.koopro.wizardsandbeasts.command.debug.dev.DevCommand;
import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugCommand;
import at.koopro.wizardsandbeasts.command.debug.inspect.DebugInspectCommand;
import at.koopro.wizardsandbeasts.command.debug.inspect.DebugInspectors;
import at.koopro.wizardsandbeasts.network.debug.DebugModeS2CPayload;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DebugModuleRegistry {
    private static final Map<String, DebugModule> MODULES = new LinkedHashMap<>();

    private DebugModuleRegistry() {
    }

    public static void bootstrap() {
        if (!MODULES.isEmpty()) return;
        register(new SpellDebugModule());
        register(new WandBondDebugModule());
        register(new ElderWandDebugModule());
        register(new PlayerDebugModule());
        register(new VaultDebugModule());
        register(new BroomDebugModule());
        register(new ReloadDebugModule());
        DebugInspectors.bootstrap();
    }

    public static void register(DebugModule module) {
        MODULES.put(module.name(), module);
    }

    public static void attachTo(LiteralArgumentBuilder<CommandSourceStack> debugRoot) {
        MODULES.values().forEach(module -> debugRoot.then(module.register()));
        debugRoot.then(DebugInspectCommand.register());
        debugRoot.then(FeatureDebugCommand.register());
        debugRoot.then(DevCommand.register());
        debugRoot.then(Commands.literal("help")
                .executes(ctx -> showOverview(ctx.getSource())));
        debugRoot.then(Commands.literal("toggle")
                .executes(ctx -> togglePlayer(ctx.getSource()))
                .then(Commands.literal("all")
                        .executes(ctx -> toggleGlobal(ctx.getSource()))));
        debugRoot.executes(ctx -> showOverview(ctx.getSource()));
    }

    private static int togglePlayer(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[W&B Debug] Player toggle requires an in-game player or use all toggle."));
            return 0;
        }
        boolean state = DebugModeService.toggleForPlayer(player);
        // The client needs this to know whether to poll for the in-world panel. Sent to the toggled
        // player, not the caller: those are the same person here, but the flag belongs to the former.
        DebugModeS2CPayload.sendTo(player, DebugModeService.isEnabled(player));
        DebugOutput out = new DebugOutput(source);
        out.ok("Debug mode for " + player.getName().getString() + ": " + (state ? "ON" : "OFF"));
        out.info(state
                ? "Look at a block, beast or player for its panel. /wandb debug inspect prints it."
                : "Panel off.");
        return 1;
    }

    private static int toggleGlobal(CommandSourceStack source) {
        boolean state = DebugModeService.toggleGlobal();
        // Everyone's flag just changed, including players with no per-player entry, so every client
        // has to be told rather than only the ones in ENABLED_PLAYERS.
        MinecraftServer server = source.getServer();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            DebugModeS2CPayload.sendTo(online, DebugModeService.isEnabled(online));
        }
        new DebugOutput(source).ok("All-player debug mode: " + (state ? "ON" : "OFF"));
        return 1;
    }

    private static int showOverview(CommandSourceStack source) {
        DebugOutput out = new DebugOutput(source);
        out.header("Debug Modules");
        out.kv("Registered", MODULES.size());
        out.kv("Global debug", DebugModeService.isGlobalEnabled() ? "ON" : "OFF");
        out.info("Built-in: tree, glow, wandtool, beam, morph, pose, apparition, blank_test, toggle, help");
        out.kv("inspect", "What you are looking at. Also drawn beside it while debug is on.");
        out.kv("feature", "Per-subsystem player state. 'feature all' for every one at once.");
        out.kv("dev", "WRITES. Open gates, hand over items, reset. 'dev setup' does the lot.");
        MODULES.values().forEach(m -> out.kv(m.name(), m.summary().isEmpty() ? "—" : m.summary()));
        return 1;
    }
}
