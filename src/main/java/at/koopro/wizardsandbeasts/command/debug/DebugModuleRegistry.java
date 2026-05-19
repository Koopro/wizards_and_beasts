package at.koopro.wizardsandbeasts.command.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
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
    }

    public static void register(DebugModule module) {
        MODULES.put(module.name(), module);
    }

    public static void attachTo(LiteralArgumentBuilder<CommandSourceStack> debugRoot) {
        MODULES.values().forEach(module -> debugRoot.then(module.register()));
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
        new DebugOutput(source).ok("Debug mode for " + player.getName().getString() + ": " + (state ? "ON" : "OFF"));
        return 1;
    }

    private static int toggleGlobal(CommandSourceStack source) {
        boolean state = DebugModeService.toggleGlobal();
        new DebugOutput(source).ok("All-player debug mode: " + (state ? "ON" : "OFF"));
        return 1;
    }

    private static int showOverview(CommandSourceStack source) {
        DebugOutput out = new DebugOutput(source);
        out.header("Debug Modules");
        out.kv("Registered", MODULES.size());
        out.kv("Global debug", DebugModeService.isGlobalEnabled() ? "ON" : "OFF");
        out.info("Built-in: tree, glow, wandtool, beam, stats, morph, toggle, help");
        MODULES.values().forEach(m -> out.kv(m.name(), m.summary().isEmpty() ? "—" : m.summary()));
        return 1;
    }
}
