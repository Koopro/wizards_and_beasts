package at.koopro.wizardsandbeasts.admin.command;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminConfirmations;
import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.spell.SpellAdminService;
import at.koopro.wizardsandbeasts.admin.spell.SpellProperty;
import at.koopro.wizardsandbeasts.admin.spell.SpellSettingIds;
import at.koopro.wizardsandbeasts.admin.spell.SpellSettingProvider;
import at.koopro.wizardsandbeasts.admin.spell.SpellTestService;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellSummary;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import at.koopro.wizardsandbeasts.util.ChatReport;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /wandb admin spell …} — the Magic section from chat or the console.
 *
 * <p>An adapter: every edit is {@code AdminSettingService.change/reset} with a
 * {@link SpellSettingIds spell setting id}, the same call the panel's packets make; batch resets and test casts
 * are {@link SpellAdminService} and {@link SpellTestService}, the same classes the panel's payloads reach. The
 * existing {@code /wandb magic spell …} tree edits players' spell knowledge, not server state, and is untouched.
 */
@NullMarked
public final class AdminSpellCommands {

    private static final SuggestionProvider<CommandSourceStack> SPELLS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Spells.all().stream().map(AdminSpellCommands::typeable), builder);
    private static final SuggestionProvider<CommandSourceStack> PROPERTIES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(SpellProperty.values()).map(SpellProperty::id), builder);
    private static final SuggestionProvider<CommandSourceStack> CATEGORIES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(SpellCategory.values()).map(c -> c.name().toLowerCase(Locale.ROOT)), builder);

    private AdminSpellCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("spell")
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource(), ""))
                        .then(Commands.argument("filter", StringArgumentType.word())
                                .executes(ctx -> list(ctx.getSource(), StringArgumentType.getString(ctx, "filter")))))
                .then(Commands.literal("info").then(spell().executes(ctx -> info(ctx.getSource(), spellArg(ctx)))))
                .then(Commands.literal("enable").then(spell().executes(ctx ->
                        set(ctx.getSource(), spellArg(ctx), SpellProperty.ENABLED.id(), "true"))))
                .then(Commands.literal("disable").then(spell().executes(ctx ->
                        set(ctx.getSource(), spellArg(ctx), SpellProperty.ENABLED.id(), "false"))))
                .then(Commands.literal("set").then(spell()
                        .then(Commands.argument("property", StringArgumentType.word()).suggests(PROPERTIES)
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> set(ctx.getSource(), spellArg(ctx),
                                                StringArgumentType.getString(ctx, "property"),
                                                StringArgumentType.getString(ctx, "value")))))))
                .then(Commands.literal("reset").then(spell()
                        .executes(ctx -> resetSpell(ctx.getSource(), spellArg(ctx)))
                        .then(Commands.argument("property", StringArgumentType.word()).suggests(PROPERTIES)
                                .executes(ctx -> resetProperty(ctx.getSource(), spellArg(ctx),
                                        StringArgumentType.getString(ctx, "property"))))))
                .then(Commands.literal("reset_category")
                        .then(Commands.argument("category", StringArgumentType.word()).suggests(CATEGORIES)
                                .executes(ctx -> resetCategory(ctx.getSource(), StringArgumentType.getString(ctx, "category")))))
                .then(Commands.literal("test").then(spell()
                        .then(Commands.literal("self").executes(ctx -> test(ctx, SpellTestService.TargetMode.SELF, null)))
                        .then(Commands.literal("looked_at").executes(ctx -> test(ctx, SpellTestService.TargetMode.LOOKED_AT, null)))
                        .then(Commands.literal("nearest_dummy").executes(ctx -> test(ctx, SpellTestService.TargetMode.NEAREST_DUMMY, null)))
                        .then(Commands.literal("player").then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> test(ctx, SpellTestService.TargetMode.PLAYER,
                                        EntityArgument.getPlayer(ctx, "target").getUUID()))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> spell() {
        return Commands.argument("spell", StringArgumentType.word()).suggests(SPELLS);
    }

    private static String spellArg(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "spell");
    }

    /** Bare path for this mod's spells, as {@code /wandb magic spell} suggests them. */
    private static String typeable(Spell spell) {
        String prefix = WizardsAndBeastsMod.MODID + ":";
        return spell.getId().startsWith(prefix) ? spell.getId().substring(prefix.length()) : spell.getId();
    }

    private static @Nullable Spell resolve(CommandSourceStack source, String raw) {
        Spell spell = Spells.byId(raw);
        if (spell == null) {
            source.sendFailure(Component.translatable("command.wizards_and_beasts.admin.spell.unknown", raw));
        }
        return spell;
    }

    // ── reads ──

    private static int list(CommandSourceStack source, String filter) {
        String needle = filter.toLowerCase(Locale.ROOT);
        ChatReport report = ChatReport.of(Component.translatable("command.wizards_and_beasts.admin.spell.list.title"));
        int shown = 0;
        for (AdminSpellSummary row : SpellAdminService.list()) {
            if (!matches(row, needle)) {
                continue;
            }
            String state = row.castAllowed() ? "enabled" : row.enabled() ? "refused" : "disabled";
            int tone = row.castAllowed() ? (row.overridden() ? ChatPalette.WARN : ChatPalette.TEXT) : ChatPalette.BAD;
            String path = row.id().startsWith(WizardsAndBeastsMod.MODID + ":") ? row.id().substring(WizardsAndBeastsMod.MODID.length() + 1) : row.id();
            report.state(path + " · " + row.category().toLowerCase(Locale.ROOT)
                            + (row.unforgivable() ? " · unforgivable" : row.dark() ? " · dark" : ""),
                    state, tone, "/wandb admin spell info " + path, Component.literal(row.summary()));
            shown++;
        }
        report.subtitle(Component.translatable("command.wizards_and_beasts.admin.spell.list.count", shown));
        report.send(source);
        return 1;
    }

    /** Filter words: a category, {@code dark}, {@code unforgivable}, {@code disabled}, {@code changed}, or text. */
    private static boolean matches(AdminSpellSummary row, String needle) {
        return switch (needle) {
            case "" -> true;
            case "dark" -> row.dark();
            case "unforgivable" -> row.unforgivable();
            case "disabled" -> !row.castAllowed();
            case "changed" -> row.overridden();
            default -> row.category().equalsIgnoreCase(needle) || row.id().contains(needle);
        };
    }

    private static int info(CommandSourceStack source, String raw) {
        Spell spell = resolve(source, raw);
        if (spell == null) {
            return 0;
        }
        ChatReport report = ChatReport.of(Component.translatable(spell.getDisplayName())).subtitle(spell.getId());
        for (AdminSpellFact fact : SpellAdminService.facts(spell)) {
            report.row(Component.translatable(fact.labelKey()).getString(),
                    fact.valueTranslatable() ? Component.translatable(fact.value()) : Component.literal(fact.value()));
        }
        report.divider();
        for (SpellProperty property : SpellSettingProvider.applicable(spell)) {
            AdminSetting<?> setting = AdminSettings.registry().get(SpellSettingIds.of(spell.getId(), property));
            if (setting != null) {
                report.state(property.id(), setting.currentText() + (setting.isDefault() ? "" : " (default " + setting.defaultText() + ")"),
                        setting.isDefault() ? ChatPalette.TEXT : ChatPalette.WARN,
                        "/wandb admin spell set " + typeable(spell) + " " + property.id() + " ",
                        Component.translatable(setting.descriptionKey()));
            }
        }
        report.send(source);
        return 1;
    }

    // ── writes: all through AdminSettingService ──

    private static int set(CommandSourceStack source, String raw, String rawProperty, String value) {
        Spell spell = resolve(source, raw);
        SpellProperty property = property(source, rawProperty);
        if (spell == null || property == null) {
            return 0;
        }
        Identifier id = SpellSettingIds.of(spell.getId(), property);
        return AdminCommandFeedback.report(source,
                AdminSettings.service().change(AdminContext.of(source), id, value),
                new AdminConfirmations.Action(id, value));
    }

    private static int resetProperty(CommandSourceStack source, String raw, String rawProperty) {
        Spell spell = resolve(source, raw);
        SpellProperty property = property(source, rawProperty);
        if (spell == null || property == null) {
            return 0;
        }
        Identifier id = SpellSettingIds.of(spell.getId(), property);
        return AdminCommandFeedback.report(source, AdminSettings.service().reset(AdminContext.of(source), id),
                new AdminConfirmations.Action(id, null));
    }

    private static int resetSpell(CommandSourceStack source, String raw) {
        Spell spell = resolve(source, raw);
        if (spell == null) {
            return 0;
        }
        return AdminCommandFeedback.reportBatch(source, SpellAdminService.resetSpell(AdminContext.of(source), spell, false));
    }

    private static int resetCategory(CommandSourceStack source, String raw) {
        SpellCategory category = Arrays.stream(SpellCategory.values())
                .filter(c -> c.name().equalsIgnoreCase(raw)).findFirst().orElse(null);
        if (category == null) {
            source.sendFailure(Component.translatable("command.wizards_and_beasts.admin.spell.unknown_category", raw));
            return 0;
        }
        return AdminCommandFeedback.reportBatch(source, SpellAdminService.resetCategory(AdminContext.of(source), category, false));
    }

    private static int test(CommandContext<CommandSourceStack> ctx, SpellTestService.TargetMode mode, @Nullable UUID target)
            throws CommandSyntaxException {
        ServerPlayer admin = ctx.getSource().getPlayerOrException();
        SpellTestService.Outcome outcome = SpellTestService.test(admin, spellArg(ctx), mode, target);
        Component message = Component.translatable(outcome.messageKey(), outcome.detail())
                .withStyle(outcome.success() ? ChatFormatting.GREEN : ChatFormatting.RED);
        if (outcome.success()) {
            ctx.getSource().sendSuccess(() -> message, false);
            return 1;
        }
        ctx.getSource().sendFailure(message);
        return 0;
    }

    private static @Nullable SpellProperty property(CommandSourceStack source, String raw) {
        SpellProperty property = SpellProperty.byId(raw.toLowerCase(Locale.ROOT));
        if (property == null) {
            source.sendFailure(Component.translatable("command.wizards_and_beasts.admin.spell.unknown_property", raw,
                    String.join(", ", Arrays.stream(SpellProperty.values()).map(SpellProperty::id).toList())));
        }
        return property;
    }
}
