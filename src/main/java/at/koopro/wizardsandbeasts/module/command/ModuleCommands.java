package at.koopro.wizardsandbeasts.module.command;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

public final class ModuleCommands {

    private ModuleCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("module")
                .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
                .executes(ctx -> listModules(ctx.getSource()))
                .then(Commands.literal("list")
                        .executes(ctx -> listModules(ctx.getSource())))
                .then(Commands.argument("module", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(Module.values()).map(Enum::name), builder))
                        .then(Commands.argument("state", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        List.of("DISABLED", "ENABLED", "PREVIEW"), builder))
                                .executes(ctx -> setModuleState(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "module"),
                                        StringArgumentType.getString(ctx, "state")))));
    }

    private static int listModules(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("=== Module States ===").withStyle(ChatFormatting.GOLD), false);
        for (Module module : Module.values()) {
            String stateName;
            ChatFormatting stateColor;
            if (ModuleManager.isPreview(module)) {
                stateName = "PREVIEW";
                stateColor = ChatFormatting.YELLOW;
            } else if (ModuleManager.isEnabled(module)) {
                stateName = "ENABLED";
                stateColor = ChatFormatting.GREEN;
            } else {
                stateName = "DISABLED";
                stateColor = ChatFormatting.RED;
            }
            source.sendSuccess(() -> Component.literal("  " + module.name() + ": ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(stateName).withStyle(stateColor)), false);
        }
        return 1;
    }

    private static int setModuleState(CommandSourceStack source, String moduleName, String stateName) {
        Module module;
        try {
            module = Module.valueOf(moduleName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("Unknown module: " + moduleName).withStyle(ChatFormatting.RED));
            return 0;
        }
        ModuleManager.State state;
        try {
            state = ModuleManager.State.valueOf(stateName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("Unknown state: " + stateName
                    + " (use DISABLED, ENABLED, or PREVIEW).").withStyle(ChatFormatting.RED));
            return 0;
        }
        ModuleManager.setState(module, state);
        source.sendSuccess(() -> Component.literal("Module ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(module.name()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(state.name()).withStyle(ChatFormatting.GREEN)), true);
        return 1;
    }
}
