package at.koopro.wizardsandbeasts.module.command;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleIds;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.ModuleState;
import at.koopro.wizardsandbeasts.module.ModuleStateService;
import at.koopro.wizardsandbeasts.module.settings.ModuleSettingsSchema;
import at.koopro.wizardsandbeasts.module.settings.ModuleSettingsValues;
import at.koopro.wizardsandbeasts.module.settings.SettingDefinition;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Arrays;
import java.util.Locale;

/**
 * {@code /wandb module …} — the admin surface for module state until the screen lands.
 *
 * <p>Every mutation goes through {@link ModuleStateService}, the same method the network packet uses, so
 * the two entry points cannot drift: whatever the packet refuses, the command refuses identically.
 */
@NullMarked
public final class ModuleCommands {

    private ModuleCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("module")
                .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
                .executes(ctx -> listModules(ctx.getSource()))
                .then(Commands.literal("list")
                        .executes(ctx -> listModules(ctx.getSource())))

                .then(Commands.literal("set")
                        .then(Commands.argument("module", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(Module.values())
                                                .map(m -> m.name().toLowerCase(Locale.ROOT)), b))
                                .then(Commands.argument("state", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(ModuleState.values())
                                                        .map(ModuleState::getSerializedName), b))
                                        .executes(ctx -> setState(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "module"),
                                                StringArgumentType.getString(ctx, "state"))))))

                .then(Commands.literal("setting")
                        .then(Commands.argument("module", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(Module.values())
                                                .map(m -> m.name().toLowerCase(Locale.ROOT)), b))
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            Module module = ModuleIds.parse(
                                                    StringArgumentType.getString(ctx, "module"));
                                            if (module != null) {
                                                ModuleSettingsSchema.of(module).definitions()
                                                        .forEach(d -> b.suggest(d.key().getPath()));
                                            }
                                            return b.buildFuture();
                                        })
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(ctx -> setSetting(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "module"),
                                                        StringArgumentType.getString(ctx, "key"),
                                                        StringArgumentType.getString(ctx, "value")))))));
    }

    private static int listModules(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("=== Module States ===").withStyle(ChatFormatting.GOLD), false);
        for (Module module : Module.values()) {
            ModuleState state = ModuleManager.state(module);
            ChatFormatting color = switch (state) {
                case ENABLED -> ChatFormatting.GREEN;
                case PREVIEW -> ChatFormatting.YELLOW;
                case COMING_SOON -> ChatFormatting.AQUA;
                case DISABLED -> ChatFormatting.RED;
            };
            source.sendSuccess(() -> Component.literal("  " + module.name().toLowerCase(Locale.ROOT) + ": ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(state.getSerializedName()).withStyle(color))
                    .append(state == ModuleState.COMING_SOON
                            ? Component.literal("  (locked)").withStyle(ChatFormatting.DARK_GRAY)
                            : Component.empty()), false);

            ModuleSettingsSchema schema = ModuleSettingsSchema.of(module);
            if (schema.isEmpty()) {
                continue;
            }
            ModuleSettingsValues values = ModuleManager.settings(module);
            for (SettingDefinition<?> definition : schema.definitions()) {
                source.sendSuccess(() -> Component.literal("      " + definition.key().getPath() + " = ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.literal(String.valueOf(values.get(definition)))
                                .withStyle(ChatFormatting.WHITE)), false);
            }
        }
        return 1;
    }

    private static int setState(CommandSourceStack source, String rawModule, String rawState) {
        Module module = ModuleIds.parse(rawModule);
        if (module == null) {
            source.sendFailure(Component.literal("Unknown module: " + rawModule).withStyle(ChatFormatting.RED));
            return 0;
        }
        ModuleState state = ModuleState.parse(rawState);
        if (state == null) {
            source.sendFailure(Component.literal("Unknown state: " + rawState
                    + " (disabled, enabled, preview, coming_soon).").withStyle(ChatFormatting.RED));
            return 0;
        }
        ModuleStateService.Result result = ModuleStateService.setState(source.getServer(), module, state);
        return report(source, result, () -> Component.literal("Module ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(module.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(state.getSerializedName()).withStyle(ChatFormatting.GREEN)));
    }

    private static int setSetting(CommandSourceStack source, String rawModule, String rawKey, String rawValue) {
        Module module = ModuleIds.parse(rawModule);
        if (module == null) {
            source.sendFailure(Component.literal("Unknown module: " + rawModule).withStyle(ChatFormatting.RED));
            return 0;
        }
        Identifier key = rawKey.contains(":")
                ? Identifier.tryParse(rawKey)
                : Identifier.fromNamespaceAndPath(
                        at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID, rawKey.toLowerCase(Locale.ROOT));
        if (key == null) {
            source.sendFailure(Component.literal("Unusable setting key: " + rawKey).withStyle(ChatFormatting.RED));
            return 0;
        }
        ModuleStateService.Result result =
                ModuleStateService.setSetting(source.getServer(), module, key, rawValue);
        return report(source, result, () -> Component.literal("Set ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(module.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" " + key.getPath() + " = ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(rawValue).withStyle(ChatFormatting.GREEN)));
    }

    private static int report(CommandSourceStack source, ModuleStateService.Result result,
                              java.util.function.Supplier<Component> success) {
        switch (result) {
            case OK -> {
                source.sendSuccess(success, true);
                return 1;
            }
            case COMING_SOON_LOCKED -> source.sendFailure(Component.literal(
                    "That module is marked coming soon — a roadmap marker, not a switch. Change it in the "
                            + "config defaults or in code.").withStyle(ChatFormatting.RED));
            case UNKNOWN_SETTING -> source.sendFailure(Component.literal(
                    "That module has no such setting.").withStyle(ChatFormatting.RED));
            case BAD_VALUE -> source.sendFailure(Component.literal(
                    "That value is not valid for this setting.").withStyle(ChatFormatting.RED));
            case UNAVAILABLE -> source.sendFailure(Component.literal(
                    "Module state is unavailable right now.").withStyle(ChatFormatting.RED));
        }
        return 0;
    }
}
