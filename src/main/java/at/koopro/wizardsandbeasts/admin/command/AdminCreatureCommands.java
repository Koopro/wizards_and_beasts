package at.koopro.wizardsandbeasts.admin.command;

import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.creature.CreatureAdminService;
import at.koopro.wizardsandbeasts.admin.creature.CreatureTestSpawns;
import at.koopro.wizardsandbeasts.creature.variant.CreatureVariant;
import at.koopro.wizardsandbeasts.creature.variant.CreatureVariants;
import at.koopro.wizardsandbeasts.network.admin.AdminCreaturePayloads.CreatureSummary;
import at.koopro.wizardsandbeasts.network.admin.AdminCreaturePayloads.SpawnEntry;
import at.koopro.wizardsandbeasts.network.admin.AdminCreaturePayloads.VariantInfo;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
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
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * {@code /wandb admin creature …} — the Creatures section from chat. The same {@link CreatureAdminService} and
 * {@link CreatureTestSpawns} the panel's payloads reach, so the same checks: reading needs the content capability,
 * spawning and cleanup the world capability, and a test creature is placed where the server decides.
 * Creature rules are settings: {@code /wandb admin config set wizards_and_beasts:creature/<id>/natural_spawn false}.
 * The existing {@code /wandb creature summon|lineup} debug commands are untouched.
 */
@NullMarked
public final class AdminCreatureCommands {

    private static final String KEY = "command.wizards_and_beasts.admin.creature.";

    private static final SuggestionProvider<CommandSourceStack> CREATURES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(CreatureAdminService.roster(), builder);
    private static final SuggestionProvider<CommandSourceStack> VARIANTS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(CreatureVariants.of(StringArgumentType.getString(ctx, "creature")).stream()
                    .map(CreatureVariant::variantId), builder);

    private AdminCreatureCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("creature")
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource(), ""))
                        .then(Commands.argument("filter", StringArgumentType.word())
                                .executes(ctx -> list(ctx.getSource(), StringArgumentType.getString(ctx, "filter")))))
                .then(Commands.literal("info").then(creature().executes(ctx -> info(ctx.getSource(), creatureArg(ctx)))))
                .then(Commands.literal("spawn").then(creature()
                        .executes(ctx -> spawn(ctx, null, false))
                        .then(Commands.literal("no_ai").executes(ctx -> spawn(ctx, null, true)))
                        .then(Commands.argument("variant", StringArgumentType.word()).suggests(VARIANTS)
                                .executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "variant"), false))
                                .then(Commands.literal("no_ai").executes(ctx ->
                                        spawn(ctx, StringArgumentType.getString(ctx, "variant"), true))))))
                .then(Commands.literal("cleanup").executes(AdminCreatureCommands::cleanup));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> creature() {
        return Commands.argument("creature", StringArgumentType.word()).suggests(CREATURES);
    }

    private static String creatureArg(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "creature");
    }

    private static boolean refuse(CommandSourceStack source) {
        if (CreatureAdminService.authorised(AdminContext.of(source))) {
            return false;
        }
        source.sendFailure(Component.translatable("admin.wizards_and_beasts.creature_action.unauthorized", ""));
        return true;
    }

    private static int list(CommandSourceStack source, String filter) {
        if (refuse(source)) {
            return 0;
        }
        String needle = filter.toLowerCase(Locale.ROOT);
        ChatReport report = ChatReport.of(Component.translatable(KEY + "list.title"));
        int shown = 0;
        for (CreatureSummary row : CreatureAdminService.list(source.getServer())) {
            if (!needle.isEmpty() && !row.id().contains(needle) && !row.category().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            report.row(row.id(), Component.translatable(KEY + "list.row",
                    row.category().isEmpty() ? "—" : row.category().toLowerCase(Locale.ROOT),
                    row.spawnEntries(), row.naturalSpawnRule() ? "on" : "off", row.variants()));
            shown++;
        }
        report.send(source);
        return shown;
    }

    private static int info(CommandSourceStack source, String id) {
        if (refuse(source)) {
            return 0;
        }
        CreatureAdminService.Detail detail = CreatureAdminService.detail(source.getServer(), id);
        if (detail == null) {
            source.sendFailure(Component.translatable("admin.wizards_and_beasts.creature_action.unknown_creature", id));
            return 0;
        }
        ChatReport report = ChatReport.of(Component.translatable(detail.summary().nameKey()));
        facts(report, detail.attributes());
        report.subtitle(Component.translatable("admin.wizards_and_beasts.creature.spawning"));
        if (detail.spawns().isEmpty()) {
            report.note(Component.translatable("admin.wizards_and_beasts.creature.no_natural_spawns"));
        }
        for (SpawnEntry entry : detail.spawns()) {
            report.item(Component.translatable("admin.wizards_and_beasts.creature.spawn_entry", entry.biomes(),
                    entry.weight(), entry.minCount(), entry.maxCount()));
        }
        report.flag(Component.translatable("admin.wizards_and_beasts.creature_property.natural_spawn").getString(),
                detail.summary().naturalSpawnRule());
        report.subtitle(Component.translatable("admin.wizards_and_beasts.creature.behaviour"));
        facts(report, detail.behaviour());
        if (!detail.abilities().isEmpty()) {
            report.row("abilities", String.join(", ", detail.abilities()));
        }
        for (VariantInfo variant : detail.variants()) {
            report.row("variant " + variant.id(), (variant.enabled() ? "on" : "off") + " · weight " + variant.weight());
        }
        report.send(source);
        return 1;
    }

    private static void facts(ChatReport report, List<AdminSpellFact> facts) {
        for (AdminSpellFact fact : facts) {
            report.row(Component.translatable(fact.labelKey()).getString(),
                    fact.valueTranslatable() ? Component.translatable(fact.value()) : Component.literal(fact.value()));
        }
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, @Nullable String variant, boolean noAi)
            throws CommandSyntaxException {
        CreatureTestSpawns.Outcome outcome = CreatureTestSpawns.spawn(ctx.getSource().getPlayerOrException(),
                creatureArg(ctx), variant, noAi);
        return report(ctx.getSource(), outcome);
    }

    private static int cleanup(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return report(ctx.getSource(), CreatureTestSpawns.cleanup(ctx.getSource().getPlayerOrException()));
    }

    private static int report(CommandSourceStack source, CreatureTestSpawns.Outcome outcome) {
        Component message = Component.translatable(outcome.messageKey(), outcome.detail());
        if (outcome.success()) {
            source.sendSuccess(() -> message.copy().withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        source.sendFailure(message);
        return 0;
    }
}
