package at.koopro.wizardsandbeasts.admin.command;

import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.broom.BroomAdminService;
import at.koopro.wizardsandbeasts.broom.rules.BroomRules;
import at.koopro.wizardsandbeasts.broom.rules.BroomStat;
import at.koopro.wizardsandbeasts.network.admin.AdminBroomPayloads.BroomSummary;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import at.koopro.wizardsandbeasts.util.ChatReport;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * {@code /wandb admin broom …} — read the broom roster from chat. Every broom value is a setting, so editing is
 * {@code /wandb admin config set <id> <value>}; {@code info} prints each editable value's id to copy.
 */
@NullMarked
public final class AdminBroomCommands {

    private static final String KEY = "command.wizards_and_beasts.admin.broom.";

    private static final SuggestionProvider<CommandSourceStack> BROOMS = (ctx, builder) -> SharedSuggestionProvider.suggest(
            BroomRules.authoredAll().stream().map(d -> d.id().toString()), builder);

    private AdminBroomCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("broom")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("info").then(Commands.argument("broom", StringArgumentType.greedyString())
                        .suggests(BROOMS).executes(ctx -> info(ctx.getSource(), StringArgumentType.getString(ctx, "broom")))));
    }

    private static boolean refuse(CommandSourceStack source) {
        if (BroomAdminService.authorised(AdminContext.of(source))) {
            return false;
        }
        source.sendFailure(Component.translatable("admin.wizards_and_beasts.wand_action.unauthorized"));
        return true;
    }

    private static int list(CommandSourceStack source) {
        if (refuse(source)) {
            return 0;
        }
        ChatReport report = ChatReport.of(Component.translatable(KEY + "list.title"));
        int shown = 0;
        for (BroomSummary row : BroomAdminService.list(AdminContext.of(source)).brooms()) {
            report.row(row.id(), Component.translatable(KEY + "list.row", row.enabled() ? "on" : "off", row.tier(),
                    format(row.effective().get(BroomStat.MAX_SPEED.ordinal())), row.overridden() ? "*" : ""));
            shown++;
        }
        report.send(source);
        return shown;
    }

    private static int info(CommandSourceStack source, String broomId) {
        if (refuse(source)) {
            return 0;
        }
        String id = broomId.contains(":") ? broomId : "wizards_and_beasts:" + broomId;
        BroomAdminService.Listing listing = BroomAdminService.list(AdminContext.of(source));
        BroomSummary broom = listing.brooms().stream().filter(b -> b.id().equals(id)).findFirst().orElse(null);
        if (broom == null) {
            source.sendFailure(Component.translatable(KEY + "unknown", broomId));
            return 0;
        }
        ChatReport report = ChatReport.of(Component.translatable(broom.nameKey()));
        for (BroomStat stat : BroomStat.values()) {
            report.row(stat.id(), Component.translatable(KEY + "stat", format(broom.authored().get(stat.ordinal())),
                    format(broom.effective().get(stat.ordinal()))));
        }
        for (AdminSpellFact fact : broom.facts()) {
            report.row(Component.translatable(fact.labelKey()).getString(), fact.value());
        }
        report.subtitle(Component.translatable(KEY + "settings"));
        String prefix = "broom/" + id.replace(':', '/') + "/";
        for (AdminSettingDescriptor setting : listing.settings()) {
            if (setting.id().getPath().startsWith(prefix)) {
                report.row(setting.id().toString(), setting.value());
            }
        }
        report.send(source);
        return 1;
    }

    private static String format(float value) {
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
