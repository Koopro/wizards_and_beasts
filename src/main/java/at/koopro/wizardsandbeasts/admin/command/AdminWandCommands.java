package at.koopro.wizardsandbeasts.admin.command;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.wand.WandAdminService;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import at.koopro.wizardsandbeasts.network.admin.AdminWandPayloads.PartInfo;
import at.koopro.wizardsandbeasts.network.admin.AdminWandPayloads.Preview;
import at.koopro.wizardsandbeasts.util.ChatReport;
import at.koopro.wizardsandbeasts.wand.customization.WandPresetRegistry;
import at.koopro.wizardsandbeasts.wand.registry.WandDatapackRegistries;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /wandb admin wand …} — read the Wands section from chat and try the generator. Withdrawing a wood, core or
 * pairing is a setting: {@code /wandb admin config set <id> false}; {@code woods}/{@code cores} print each part.
 * {@code preview} and {@code give} run the same server checks as the panel.
 */
@NullMarked
public final class AdminWandCommands {

    private static final String KEY = "command.wizards_and_beasts.admin.wand.";

    // Suggestions read the registries directly (the catalog also reads every part's lore file), and offer what a
    // string argument accepts: a bare path for this mod's ids (qualify() adds the namespace), a quoted id otherwise.
    private static final SuggestionProvider<CommandSourceStack> WOODS = (ctx, builder) -> SharedSuggestionProvider.suggest(
            ctx.getSource().registryAccess().lookupOrThrow(WandDatapackRegistries.WAND_WOOD_REGISTRY).keySet().stream()
                    .map(AdminWandCommands::typeable), builder);
    private static final SuggestionProvider<CommandSourceStack> CORES = (ctx, builder) -> SharedSuggestionProvider.suggest(
            ctx.getSource().registryAccess().lookupOrThrow(WandDatapackRegistries.WAND_CORE_REGISTRY).keySet().stream()
                    .map(AdminWandCommands::typeable), builder);
    private static final SuggestionProvider<CommandSourceStack> FLEX = (ctx, builder) -> SharedSuggestionProvider.suggest(
            Arrays.stream(WandFlexibility.values()).map(f -> f.name().toLowerCase(Locale.ROOT)), builder);
    private static final SuggestionProvider<CommandSourceStack> PRESETS = (ctx, builder) -> SharedSuggestionProvider.suggest(
            WandPresetRegistry.all().stream().map(p -> typeable(p.id())), builder);

    private static String typeable(Identifier id) {
        return WizardsAndBeastsMod.MODID.equals(id.getNamespace()) ? id.getPath() : "\"" + id + "\"";
    }

    private AdminWandCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("wand")
                .then(Commands.literal("woods").executes(ctx -> parts(ctx.getSource(), true)))
                .then(Commands.literal("cores").executes(ctx -> parts(ctx.getSource(), false)))
                .then(Commands.literal("preview").then(request(false)))
                .then(Commands.literal("give").then(request(true)));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> request(boolean give) {
        return Commands.argument("wood", StringArgumentType.string()).suggests(WOODS)
                .then(Commands.argument("core", StringArgumentType.string()).suggests(CORES)
                        .then(Commands.argument("length", FloatArgumentType.floatArg())
                                .then(Commands.argument("flexibility", StringArgumentType.word()).suggests(FLEX)
                                        .executes(ctx -> run(ctx, give, ""))
                                        .then(Commands.argument("preset", StringArgumentType.string()).suggests(PRESETS)
                                                .executes(ctx -> run(ctx, give, StringArgumentType.getString(ctx, "preset")))))));
    }

    private static boolean refuse(CommandSourceStack source) {
        if (WandAdminService.authorised(AdminContext.of(source))) {
            return false;
        }
        source.sendFailure(Component.translatable("admin.wizards_and_beasts.wand_action.unauthorized"));
        return true;
    }

    private static int parts(CommandSourceStack source, boolean woods) {
        if (refuse(source)) {
            return 0;
        }
        WandAdminService.Catalog catalog = WandAdminService.catalog(source.getServer(), AdminContext.of(source));
        List<PartInfo> parts = woods ? catalog.woods() : catalog.cores();
        ChatReport report = ChatReport.of(Component.translatable(KEY + (woods ? "woods" : "cores")));
        for (PartInfo part : parts) {
            String setting = (woods ? "wand_wood/" : "wand_core/") + part.id().replace(':', '/') + "/enabled";
            String enabled = catalog.settings().stream().filter(d -> d.id().getPath().equals(setting))
                    .map(AdminSettingDescriptor::value).findFirst().orElse("?");
            long pairs = catalog.pairs().stream()
                    .filter(p -> woods ? p.startsWith(part.id() + "|") : p.endsWith("|" + part.id())).count();
            report.row(part.id(), Component.translatable(KEY + "part_row",
                    part.nameTranslatable() ? Component.translatable(part.name()) : Component.literal(part.name()),
                    "true".equals(enabled) ? "on" : "off", pairs));
        }
        report.send(source);
        return parts.size();
    }

    private static int run(CommandContext<CommandSourceStack> ctx, boolean give, String preset) {
        CommandSourceStack source = ctx.getSource();
        if (refuse(source)) {
            return 0;
        }
        WandAdminService.Request request = new WandAdminService.Request(
                qualify(StringArgumentType.getString(ctx, "wood")), qualify(StringArgumentType.getString(ctx, "core")),
                FloatArgumentType.getFloat(ctx, "length"), StringArgumentType.getString(ctx, "flexibility"),
                preset.isEmpty() ? "" : qualify(preset));
        if (give) {
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                source.sendFailure(Component.translatable(KEY + "player_only"));
                return 0;
            }
            WandAdminService.Outcome outcome = WandAdminService.giveTestWand(player, request);
            if (outcome.success()) {
                source.sendSuccess(() -> Component.translatable(outcome.messageKey(), outcome.detail()), false);
                return 1;
            }
            source.sendFailure(Component.translatable(outcome.messageKey(), outcome.detail()));
            return 0;
        }
        Preview preview = WandAdminService.preview(source.getServer(), request);
        ChatReport report = ChatReport.of(Component.translatable(KEY + "preview", request.wood(), request.core()));
        report.row("verdict", Component.translatable(preview.messageKey(), request.wood() + " + " + request.core()));
        for (AdminSpellFact fact : preview.facts()) {
            report.row(Component.translatable(fact.labelKey()).getString(), fact.value());
        }
        report.send(source);
        return preview.valid() ? 1 : 0;
    }

    private static String qualify(String id) {
        return id.contains(":") ? id : "wizards_and_beasts:" + id;
    }
}
