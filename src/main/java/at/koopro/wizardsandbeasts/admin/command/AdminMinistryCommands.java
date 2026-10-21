package at.koopro.wizardsandbeasts.admin.command;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.access.AdminPolicy;
import at.koopro.wizardsandbeasts.ministry.trace.CaseReport;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /wandb admin ministry cases}: the Ministry → Cases page as chat, for a console or a server without the
 * Control Center open. Read-only; needs the Ministry section's capability.
 */
@NullMarked
public final class AdminMinistryCommands {

    private AdminMinistryCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("ministry")
                .requires(AdminPolicy.requires(AdminCategory.MINISTRY.defaultCapability()))
                .then(Commands.literal("cases").executes(ctx -> cases(ctx.getSource())));
    }

    private static int cases(CommandSourceStack source) {
        CaseReport.Snapshot snapshot = CaseReport.build(source.getServer());
        say(source, Component.literal("Ministry cases — " + snapshot.rows().size() + " on the board, "
                + snapshot.incidents() + " incidents on file, " + snapshot.pending() + " reports on their way")
                .withStyle(ChatFormatting.GOLD));
        for (CaseReport.CaseRow row : snapshot.rows()) {
            say(source, Component.literal(" #" + row.id() + " [" + row.kind() + "] "
                            + (row.subject().isEmpty() ? "" : row.subject() + " · ") + row.spell() + " ("
                            + row.legalClass() + ")" + (row.stage().isEmpty() ? "" : " · " + row.stage())
                            + " · evidence " + row.strength() + "/" + row.bar()
                            + (row.holder().isEmpty() ? "" : " · held by " + row.holder()))
                    .withStyle(row.strength() >= row.bar() ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
            say(source, Component.literal("    " + row.where() + " · "
                    + (row.evidence().isEmpty() ? "no evidence" : row.evidence())).withStyle(ChatFormatting.GRAY));
        }
        return snapshot.rows().size();
    }

    private static void say(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }
}
