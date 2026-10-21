package at.koopro.wizardsandbeasts.ministry.command;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.data.PlayerMinistryRecord;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseType;
import at.koopro.wizardsandbeasts.ministry.trace.AppealRules;
import at.koopro.wizardsandbeasts.ministry.trace.CaseworkRules;
import at.koopro.wizardsandbeasts.ministry.trace.ClosedCase;
import at.koopro.wizardsandbeasts.ministry.trace.Dossier;
import at.koopro.wizardsandbeasts.ministry.trace.Evidence;
import at.koopro.wizardsandbeasts.ministry.trace.EvidenceKind;
import at.koopro.wizardsandbeasts.ministry.trace.EvidenceRules;
import at.koopro.wizardsandbeasts.ministry.trace.Incident;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryCaseData;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryCasework;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryTrace;
import at.koopro.wizardsandbeasts.ministry.trace.NpcCase;
import at.koopro.wizardsandbeasts.ministry.trace.NpcCaseTemplates;
import at.koopro.wizardsandbeasts.ministry.trace.OpenCase;
import at.koopro.wizardsandbeasts.ministry.trace.Plea;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The wizard's and the Auror's side of a case, under {@code /wandb ministry}.
 *
 * <ul>
 *   <li>{@code summons answer [contest|guilty]} and {@code surrender [contest|guilty]} — come before the court.</li>
 *   <li>{@code appeal} — take the last ruling to the full Wizengamot.</li>
 *   <li>{@code testify [incident]} — give an account of something the server saw you witness.</li>
 *   <li>{@code casework …} — the Auror Office's board and the work on it.</li>
 *   <li>{@code dossier} — the case file as the wizard is entitled to see it: every charge, the evidence on it,
 *       and the bar it has to clear. Disclosure is what makes the plea a real choice.</li>
 * </ul>
 *
 * <p>Nothing here carries evidence: every command asks the server to make an observation and the server decides
 * what, if anything, it shows.
 */
@NullMarked
public final class MinistryCaseworkCommands {

    private static final String BASE = "/wandb ministry ";

    private MinistryCaseworkCommands() {}

    /** {@code surrender}, {@code appeal}, {@code testify}, {@code dossier}. */
    public static LiteralArgumentBuilder<CommandSourceStack> attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        return root
                .then(Commands.literal("surrender")
                        .executes(ctx -> answer(ctx.getSource(), Plea.CONTEST))
                        .then(Commands.literal("contest").executes(ctx -> answer(ctx.getSource(), Plea.CONTEST)))
                        .then(Commands.literal("guilty").executes(ctx -> answer(ctx.getSource(), Plea.GUILTY))))
                .then(Commands.literal("appeal")
                        .executes(ctx -> appeal(ctx.getSource())))
                .then(Commands.literal("testify")
                        .executes(ctx -> listWitnessed(ctx.getSource()))
                        .then(Commands.argument("incident", LongArgumentType.longArg(1L))
                                .executes(ctx -> testify(ctx.getSource(),
                                        LongArgumentType.getLong(ctx, "incident")))))
                .then(Commands.literal("dossier")
                        .executes(ctx -> dossier(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(MinistryPermissions.operatorOnly())
                                .executes(ctx -> dossier(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(casework());
    }

    /** The plea forms of {@code summons answer}. */
    public static LiteralArgumentBuilder<CommandSourceStack> answerWithPlea(
            LiteralArgumentBuilder<CommandSourceStack> answer) {
        return answer
                .then(Commands.literal("contest").executes(ctx -> answer(ctx.getSource(), Plea.CONTEST)))
                .then(Commands.literal("guilty").executes(ctx -> answer(ctx.getSource(), Plea.GUILTY)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> casework() {
        return Commands.literal("casework")
                .executes(ctx -> board(ctx.getSource()))
                .then(Commands.literal("board").executes(ctx -> board(ctx.getSource())))
                .then(Commands.literal("take")
                        .then(Commands.argument("case", LongArgumentType.longArg(1L))
                                .executes(ctx -> take(ctx.getSource(), LongArgumentType.getLong(ctx, "case")))))
                .then(Commands.literal("drop")
                        .then(Commands.argument("case", LongArgumentType.longArg(1L))
                                .executes(ctx -> drop(ctx.getSource(), LongArgumentType.getLong(ctx, "case")))))
                .then(Commands.literal("examine").executes(ctx -> examine(ctx.getSource())))
                .then(Commands.literal("interview").executes(ctx -> interview(ctx.getSource())))
                .then(Commands.literal("apprehend")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> apprehend(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                // Posts a case at your feet with its hideout 30 blocks east. Authoring and testing only.
                .then(Commands.literal("post")
                        .requires(MinistryPermissions.operatorOnly())
                        .then(Commands.argument("template", IdentifierArgument.id())
                                .suggests((ctx, builder) -> {
                                    NpcCaseTemplates.all().keySet().forEach(id -> builder.suggest(id.toString()));
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> post(ctx.getSource(), IdentifierArgument.getId(ctx, "template")))));
    }

    // ── the wizard ──

    private static int answer(CommandSourceStack source, Plea plea) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer wizard = source.getPlayerOrException();
        if (!TraceService.isActive()) {
            say(source, Component.literal("The Ministry is not sitting.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        return switch (MinistryTrace.answer(wizard, plea)) {
            case HEARD -> 1;
            case NOTHING_TO_ANSWER -> {
                say(source, Component.literal("The Ministry has not summoned you.").withStyle(ChatFormatting.GRAY));
                yield 0;
            }
            case STILL_UNDER_INVESTIGATION -> {
                say(source, Component.literal("Your case is still being investigated; no hearing has been called.")
                        .withStyle(ChatFormatting.YELLOW));
                yield 0;
            }
        };
    }

    private static int appeal(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer wizard = source.getPlayerOrException();
        if (!TraceService.isActive()) {
            say(source, Component.literal("The Ministry is not sitting.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        MinistryTrace.AppealResult result = MinistryTrace.appeal(wizard);
        String line = switch (result) {
            case NO_CASE -> "You have no ruling to appeal.";
            case NOTHING_TO_APPEAL -> "Your last hearing cleared you; there is nothing to appeal.";
            case PLEADED_GUILTY -> "You pleaded guilty; a plea cannot be appealed.";
            case ALREADY_APPEALED -> "That ruling has already been appealed.";
            case WINDOW_CLOSED -> "The time to appeal that ruling has passed.";
            case CANNOT_PAY -> "Your vault cannot cover the appeal fee of "
                    + at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper.formatFromKnuts(
                            Config.ministryAppealFeeKnuts) + ".";
            case UPHELD -> "The appeal court upheld the ruling.";
            case REDUCED -> "The appeal court reduced the ruling; the fee is refunded.";
            case OVERTURNED -> "The appeal court overturned the ruling; the fee is refunded.";
        };
        boolean good = result == MinistryTrace.AppealResult.REDUCED || result == MinistryTrace.AppealResult.OVERTURNED;
        say(source, Component.literal(line).withStyle(good ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        return good || result == MinistryTrace.AppealResult.UPHELD ? 1 : 0;
    }

    private static int listWitnessed(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer witness = source.getPlayerOrException();
        List<Incident> seen = MinistryTrace.witnessed(witness);
        if (seen.isEmpty()) {
            say(source, Component.literal("Nothing you witnessed is before the Ministry.")
                    .withStyle(ChatFormatting.GRAY));
            return 0;
        }
        say(source, Component.literal("You witnessed, and may give an account of:").withStyle(ChatFormatting.GOLD));
        for (Incident incident : seen) {
            say(source, Component.literal(" · #" + incident.id() + " ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(MinistryTrace.spellName(incident.spellId()).copy().withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" by " + incident.casterName() + " ").withStyle(ChatFormatting.GRAY))
                    .append(button("[testify]", BASE + "testify " + incident.id())));
        }
        return seen.size();
    }

    private static int testify(CommandSourceStack source, long incidentId)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CaseworkRules.Result result = MinistryTrace.testify(source.getPlayerOrException(), incidentId);
        if (result.ok()) {
            say(source, Component.literal("Your account is on the file.").withStyle(ChatFormatting.GREEN));
            return 1;
        }
        return refuse(source, result);
    }

    /** The case file, disclosed: charges, evidence, and the bar each must clear. */
    private static int dossier(CommandSourceStack source, ServerPlayer wizard) {
        MinistryCaseData data = MinistryCaseData.get(source.getServer());
        Dossier dossier = data.dossier(wizard.getUUID());
        long now = MinistryTrace.now(source.getServer());
        PlayerMinistryRecord record = MinistryRecords.get(wizard);
        say(source, Component.literal("Dossier — ").withStyle(ChatFormatting.GOLD)
                .append(wizard.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE)));
        say(source, Component.literal("  Conduct with the Ministry: " + record.conduct()).withStyle(ChatFormatting.GRAY));
        for (LicenseType type : LicenseType.values()) {
            if (record.suspendedAt(type, now)) {
                say(source, Component.literal("  Suspended: ").withStyle(ChatFormatting.RED)
                        .append(type.displayName())
                        .append(Component.literal(" for ").withStyle(ChatFormatting.GRAY))
                        .append(MinistryTrace.duration(record.suspensions().get(type) - now)));
            }
        }
        if (dossier.openCase().isPresent()) {
            OpenCase open = dossier.openCase().get();
            say(source, Component.literal("  Case #" + open.caseId() + " — " + open.stage().getSerializedName()
                    + (open.cold() ? " (cold)" : "")
                    + (open.failedToAppear() ? ", failed to appear" : "")).withStyle(ChatFormatting.YELLOW));
            for (long id : open.incidents()) {
                Incident incident = data.incident(id);
                if (incident == null) {
                    continue;
                }
                List<Evidence> evidence = data.evidence(id);
                int strength = EvidenceRules.strength(evidence);
                int bar = EvidenceRules.standardFor(incident.legalClass());
                say(source, Component.literal("   · ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(MinistryTrace.spellName(incident.spellId()).copy().withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" (" + incident.legalClass().getSerializedName() + ", "
                                + incident.exposure().getSerializedName() + ") evidence " + strength + " / " + bar)
                                .withStyle(strength >= bar ? ChatFormatting.RED : ChatFormatting.GREEN)));
                say(source, Component.literal("     " + describe(evidence)).withStyle(ChatFormatting.DARK_GRAY));
            }
            if (open.stage() != at.koopro.wizardsandbeasts.ministry.trace.CaseStage.INVESTIGATING) {
                say(source, Component.literal("  Answer: ").withStyle(ChatFormatting.GRAY)
                        .append(button("[contest]", BASE + "summons answer contest"))
                        .append(Component.literal(" "))
                        .append(button("[plead guilty]", BASE + "summons answer guilty")));
            }
        } else {
            say(source, Component.literal("  No open case.").withStyle(ChatFormatting.DARK_GRAY));
        }
        dossier.lastCase().ifPresent(closed -> {
            say(source, Component.literal("  Last ruling: " + closed.ruling().verdict().getSerializedName()
                    + (closed.appeal() == ClosedCase.Appeal.NONE ? "" : " (appeal " + closed.appeal()
                    .getSerializedName() + ")")).withStyle(ChatFormatting.GRAY));
            if (AppealRules.eligibility(closed, now) == AppealRules.Eligibility.ALLOWED) {
                say(source, Component.literal("  You may appeal for another ").withStyle(ChatFormatting.GRAY)
                        .append(MinistryTrace.duration(closed.appealBy() - now))
                        .append(Component.literal(" "))
                        .append(button("[appeal]", BASE + "appeal")));
            }
        });
        return 1;
    }

    private static String describe(List<Evidence> evidence) {
        if (evidence.isEmpty()) {
            return "no evidence on file";
        }
        Map<EvidenceKind, Integer> counts = new EnumMap<>(EvidenceKind.class);
        evidence.forEach(item -> counts.merge(item.kind(), 1, Integer::sum));
        StringBuilder out = new StringBuilder();
        counts.forEach((kind, count) -> {
            if (!out.isEmpty()) {
                out.append(", ");
            }
            out.append(kind.getSerializedName().replace('_', ' ')).append(count > 1 ? " ×" + count : "");
        });
        return out.toString();
    }

    // ── the Auror ──

    private static int board(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer viewer = source.getPlayerOrException();
        if (!MinistryCasework.isAuror(viewer)) {
            return refuse(source, Config.ministryPlayerAurors ? CaseworkRules.Result.NOT_AUROR
                    : CaseworkRules.Result.DISABLED);
        }
        List<MinistryCasework.Entry> entries = MinistryCasework.board(viewer);
        long now = MinistryTrace.now(source.getServer());
        say(source, Component.literal("Auror Office — cases").withStyle(ChatFormatting.GOLD));
        if (entries.isEmpty()) {
            say(source, Component.literal("  The board is empty.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        for (MinistryCasework.Entry entry : entries) {
            boolean mine = entry.holder().filter(viewer.getUUID()::equals).isPresent();
            MutableComponent line = Component.literal(" #" + entry.id() + " ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(switch (entry.kind()) {
                        case CASE -> "Case against " + entry.subject().orElse("?");
                        case UNSOLVED -> "Unsolved";
                        case POSTED -> "Posted";
                    }).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(MinistryTrace.spellName(entry.spellId()))
                    .append(Component.literal(" (" + entry.legalClass().getSerializedName() + ")  ")
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("at " + entry.where().getX() + ", " + entry.where().getZ()
                            + "  evidence " + entry.strength() + " ").withStyle(ChatFormatting.GRAY));
            if (mine) {
                line.append(Component.literal("[yours] ").withStyle(ChatFormatting.GREEN))
                        .append(button("[drop]", BASE + "casework drop " + entry.id()));
            } else if (entry.holder().isPresent()) {
                line.append(Component.literal("[held]").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                line.append(button("[take]", BASE + "casework take " + entry.id()));
            }
            say(source, line);
        }
        say(source, Component.literal("Examine a scene with Revelio or ").withStyle(ChatFormatting.DARK_GRAY)
                .append(button("[examine]", BASE + "casework examine"))
                .append(Component.literal("; sneak-use a witness or ").withStyle(ChatFormatting.DARK_GRAY))
                .append(button("[interview]", BASE + "casework interview")));
        return entries.size();
    }

    private static int take(CommandSourceStack source, long id)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CaseworkRules.Result result = MinistryCasework.take(source.getPlayerOrException(), id);
        if (result.ok()) {
            say(source, Component.literal("Case #" + id + " is yours for three days.").withStyle(ChatFormatting.GREEN));
            return 1;
        }
        return refuse(source, result);
    }

    private static int drop(CommandSourceStack source, long id)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CaseworkRules.Result result = MinistryCasework.drop(source.getPlayerOrException(), id);
        if (result.ok()) {
            say(source, Component.literal("Case #" + id + " is back on the board.").withStyle(ChatFormatting.GRAY));
            return 1;
        }
        return refuse(source, result);
    }

    private static int examine(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        int filed = MinistryCasework.examine(source.getPlayerOrException());
        if (filed == 0) {
            say(source, Component.literal("Nothing here bears on a case you hold.").withStyle(ChatFormatting.GRAY));
        }
        return filed;
    }

    private static int interview(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        int done = MinistryCasework.interviewNearby(source.getPlayerOrException());
        if (done == 0) {
            say(source, Component.literal("Nobody within reach saw anything you are working.")
                    .withStyle(ChatFormatting.GRAY));
        }
        return done;
    }

    private static int apprehend(CommandSourceStack source, ServerPlayer suspect)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CaseworkRules.Result result = MinistryCasework.apprehend(source.getPlayerOrException(), suspect);
        if (result.ok()) {
            say(source, Component.literal("Taken in. The Wizengamot sits at once.").withStyle(ChatFormatting.GREEN));
            return 1;
        }
        return refuse(source, result);
    }

    private static int post(CommandSourceStack source, Identifier template)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer near = source.getPlayerOrException();
        NpcCase posted = MinistryCasework.post(near, template, near.blockPosition(), near.blockPosition().east(30));
        if (posted == null) {
            say(source, Component.literal("No such case template: " + template).withStyle(ChatFormatting.RED));
            return 0;
        }
        say(source, Component.literal("Posted case #" + posted.id() + ".").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    // ── helpers ──

    private static int refuse(CommandSourceStack source, CaseworkRules.Result result) {
        source.sendFailure(Component.translatable("ministry.wizards_and_beasts.casework.refused."
                + result.name().toLowerCase(java.util.Locale.ROOT)));
        return 0;
    }

    private static Component button(String label, String command) {
        return Component.literal(label).withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent.RunCommand(command)));
    }

    private static void say(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }
}
