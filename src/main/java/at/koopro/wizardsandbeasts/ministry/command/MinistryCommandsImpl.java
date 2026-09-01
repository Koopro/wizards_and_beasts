package at.koopro.wizardsandbeasts.ministry.command;

import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.data.MinistryRank;
import at.koopro.wizardsandbeasts.ministry.data.PlayerMinistryRecord;
import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.ministry.law.MinistryFines;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import at.koopro.wizardsandbeasts.ministry.law.WantedLevel;
import at.koopro.wizardsandbeasts.ministry.post.MinistryPost;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;
import java.util.Map;

/**
 * The {@code /wandb ministry …} surface.
 *
 * <p>Authority is by {@link MinistryRank} or operator, not by operator alone — an Auror can act as an Auror
 * on a server where they are not staff. Reading <i>your own</i> record needs nothing at all; everything that
 * touches someone else, or changes anything, is gated.
 */
@NullMarked
public final class MinistryCommandsImpl {

    private MinistryCommandsImpl() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("ministry")
                // Your own record is public to you and needs no rank.
                .then(Commands.literal("record")
                        .executes(ctx -> showRecord(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(MinistryPermissions.atLeast(MinistryRank.AUROR))
                                .executes(ctx -> showRecord(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))

                .then(Commands.literal("wanted")
                        .requires(MinistryPermissions.atLeast(MinistryRank.AUROR))
                        .executes(ctx -> listWanted(ctx.getSource())))

                .then(Commands.literal("pardon")
                        .requires(MinistryPermissions.atLeast(MinistryRank.MAGICAL_LAW_ENFORCEMENT))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> pardon(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))

                .then(Commands.literal("rank")
                        .requires(MinistryPermissions.atLeast(MinistryRank.MINISTER))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("rank", StringArgumentType.word())
                                                .suggests((ctx, b) -> {
                                                    for (MinistryRank r : MinistryRank.values()) {
                                                        b.suggest(r.getSerializedName());
                                                    }
                                                    return b.buildFuture();
                                                })
                                                .executes(ctx -> setRank(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "rank"))))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> setRank(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"), "none")))))

                .then(Commands.literal("licence")
                        .requires(MinistryPermissions.atLeast(MinistryRank.MAGICAL_LAW_ENFORCEMENT))
                        .then(Commands.literal("grant")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> setLicence(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"), true))))
                        .then(Commands.literal("revoke")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> setLicence(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"), false)))))

                .then(Commands.literal("register")
                        .requires(MinistryPermissions.atLeast(MinistryRank.MAGICAL_LAW_ENFORCEMENT))
                        .then(Commands.literal("animagus")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> setAnimagusRegistered(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"), true))))
                        .then(Commands.literal("revoke_animagus")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> setAnimagusRegistered(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"), false)))))

                .then(Commands.literal("notice")
                        .requires(MinistryPermissions.atLeast(MinistryRank.OBLIVIATOR))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(ctx -> sendNotice(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "message"))))))

                // Your own bill is yours to see and to settle; no rank.
                .then(Commands.literal("fine")
                        .executes(ctx -> showFine(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.literal("pay")
                                .executes(ctx -> payFine(ctx.getSource(),
                                        ctx.getSource().getPlayerOrException(), Long.MAX_VALUE))
                                .then(Commands.argument("knuts", LongArgumentType.longArg(1L))
                                        .executes(ctx -> payFine(ctx.getSource(),
                                                ctx.getSource().getPlayerOrException(),
                                                LongArgumentType.getLong(ctx, "knuts")))))
                        .then(Commands.literal("waive")
                                .requires(MinistryPermissions.atLeast(MinistryRank.MAGICAL_LAW_ENFORCEMENT))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> waiveFine(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"))))))

                // Administrative: drive the system directly for testing. Operator only — no rank reaches it.
                .then(Commands.literal("notoriety")
                        .requires(MinistryPermissions.operatorOnly())
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("value", FloatArgumentType.floatArg(0f, 100f))
                                                .executes(ctx -> setNotoriety(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        FloatArgumentType.getFloat(ctx, "value"))))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> setNotoriety(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"), 0f)))))

                .then(Commands.literal("offence")
                        .requires(MinistryPermissions.operatorOnly())
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("offence", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            for (MagicalOffence o : MagicalOffence.values()) {
                                                b.suggest(o.getSerializedName());
                                            }
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> reportOffence(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "offence"))))));
    }

    // ── read ──

    private static int showRecord(CommandSourceStack source, ServerPlayer target) {
        PlayerMinistryRecord record = MinistryRecords.get(target);

        say(source, Component.literal("Ministry record — ").withStyle(ChatFormatting.GOLD)
                .append(target.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE)));

        if (!TraceService.isActive()) {
            say(source, Component.literal("  (the Trace is disabled — nothing is being recorded)")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        say(source, Component.literal("  Status: ").withStyle(ChatFormatting.GRAY)
                .append(record.wantedLevel().displayName()));
        say(source, Component.literal(String.format(Locale.ROOT, "  Notoriety: %.1f / %.0f",
                record.notoriety(), PlayerMinistryRecord.MAX_NOTORIETY)).withStyle(ChatFormatting.GRAY));
        say(source, Component.literal("  Rank: ").withStyle(ChatFormatting.GRAY)
                .append(record.rank().displayName().copy().withStyle(ChatFormatting.AQUA)));

        if (record.fugitive()) {
            say(source, Component.literal("  FUGITIVE — escaped custody").withStyle(ChatFormatting.DARK_RED));
        }
        if (record.isServingSentence()) {
            say(source, Component.literal("  Serving: " + formatTicks(record.sentenceTicks()) + " remaining")
                    .withStyle(ChatFormatting.RED));
        }
        if (record.owesFine()) {
            say(source, Component.literal("  Outstanding fine: ").withStyle(ChatFormatting.GRAY)
                    .append(MinistryFines.money(record.outstandingFineKnuts())
                            .copy().withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("  (heat will not cool until it is paid)")
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }

        Map<MagicalOffence, Integer> offences = record.offencesByWeight();
        if (offences.isEmpty()) {
            say(source, Component.literal("  No offences on file.").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            say(source, Component.literal("  Offences on file:").withStyle(ChatFormatting.GRAY));
            offences.forEach((offence, count) -> say(source, Component.literal("   · ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(offence.displayName().copy().withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" ×" + count).withStyle(ChatFormatting.YELLOW))));
        }
        return 1;
    }

    private static int listWanted(CommandSourceStack source) {
        int shown = 0;
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            PlayerMinistryRecord record = MinistryRecords.get(player);
            if (record.wantedLevel() == WantedLevel.CLEAR && !record.fugitive()) {
                continue;
            }
            shown++;
            say(source, Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(player.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  "))
                    .append(record.wantedLevel().displayName())
                    .append(Component.literal(record.fugitive() ? "  (fugitive)" : "")
                            .withStyle(ChatFormatting.DARK_RED)));
        }
        if (shown == 0) {
            say(source, Component.literal("Nobody currently of interest.").withStyle(ChatFormatting.GRAY));
        }
        return shown;
    }

    /** What you owe, and how to settle it. Self-service — the whole point is that it needs no official. */
    private static int showFine(CommandSourceStack source, ServerPlayer target) {
        long owed = MinistryFines.owed(target);
        if (owed <= 0L) {
            say(source, Component.literal("Nothing outstanding with the Ministry.")
                    .withStyle(ChatFormatting.GREEN));
            return 0;
        }
        say(source, Component.literal("Outstanding fine: ").withStyle(ChatFormatting.GOLD)
                .append(MinistryFines.money(owed).copy().withStyle(ChatFormatting.WHITE)));
        say(source, Component.literal("Gringotts settles it from your vault automatically; "
                        + "/wandb ministry fine pay takes it now.")
                .withStyle(ChatFormatting.DARK_GRAY));
        return 1;
    }

    private static int payFine(CommandSourceStack source, ServerPlayer target, long maxKnuts) {
        long owed = MinistryFines.owed(target);
        if (owed <= 0L) {
            say(source, Component.literal("Nothing outstanding with the Ministry.")
                    .withStyle(ChatFormatting.GREEN));
            return 0;
        }
        long paid = MinistryFines.pay(target, maxKnuts);
        if (paid <= 0L) {
            say(source, Component.literal("Your vault is empty — nothing could be collected.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        // The paid/remaining notice itself comes from MinistryFines through the Ministry post seam, so the
        // command deliberately does not restate it; this line is only the command's own acknowledgement.
        say(source, Component.literal("Paid ").withStyle(ChatFormatting.GREEN)
                .append(MinistryFines.money(paid).copy().withStyle(ChatFormatting.WHITE)));
        return 1;
    }

    private static int waiveFine(CommandSourceStack source, ServerPlayer target) {
        long owed = MinistryFines.owed(target);
        if (owed <= 0L) {
            say(source, Component.literal("Nothing outstanding against ").withStyle(ChatFormatting.GRAY)
                    .append(target.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE)));
            return 0;
        }
        MinistryFines.waive(target);
        say(source, Component.literal("Waived ").withStyle(ChatFormatting.GREEN)
                .append(MinistryFines.money(owed).copy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" against "))
                .append(target.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" — the file remains.").withStyle(ChatFormatting.DARK_GRAY)));
        return 1;
    }

    // ── write ──

    private static int pardon(CommandSourceStack source, ServerPlayer target) {
        MinistryRecords.mutate(target, PlayerMinistryRecord::pardoned);
        MinistryPost.send(target,
                Component.translatable("ministry.wizards_and_beasts.notice.pardon.subject"),
                Component.translatable("ministry.wizards_and_beasts.notice.pardon.body"));
        say(source, Component.literal("Pardoned ").withStyle(ChatFormatting.GREEN)
                .append(target.getDisplayName().plainCopy())
                .append(Component.literal(" — the file remains.").withStyle(ChatFormatting.DARK_GRAY)));
        return 1;
    }

    private static int setRank(CommandSourceStack source, ServerPlayer target, String rawRank) {
        MinistryRank rank = null;
        for (MinistryRank candidate : MinistryRank.values()) {
            if (candidate.getSerializedName().equalsIgnoreCase(rawRank)) {
                rank = candidate;
                break;
            }
        }
        if (rank == null) {
            say(source, Component.literal("Unknown rank: " + rawRank).withStyle(ChatFormatting.RED));
            return 0;
        }
        MinistryRank applied = rank;
        MinistryRecords.mutate(target, record -> record.withRank(applied));
        MinistryPost.send(target,
                Component.translatable("ministry.wizards_and_beasts.notice.appointment.subject"),
                Component.translatable("ministry.wizards_and_beasts.notice.appointment.body", applied.displayName()));
        say(source, Component.literal("Appointed ").withStyle(ChatFormatting.GREEN)
                .append(target.getDisplayName().plainCopy())
                .append(Component.literal(" as "))
                .append(applied.displayName()));
        return 1;
    }

    private static int setLicence(CommandSourceStack source, ServerPlayer target, boolean granted) {
        PlayerAbilityHelper.setApparitionLicensed(target, granted);
        MinistryPost.send(target,
                Component.translatable("ministry.wizards_and_beasts.notice.licence.subject"),
                Component.translatable(granted
                        ? "ministry.wizards_and_beasts.notice.licence.granted"
                        : "ministry.wizards_and_beasts.notice.licence.revoked"));
        say(source, Component.literal((granted ? "Granted" : "Revoked") + " Apparition licence for ")
                .withStyle(granted ? ChatFormatting.GREEN : ChatFormatting.YELLOW)
                .append(target.getDisplayName().plainCopy()));
        return 1;
    }

    private static int setAnimagusRegistered(CommandSourceStack source, ServerPlayer target, boolean registered) {
        PlayerAbilityHelper.setAnimagusRegistered(target, registered);
        say(source, Component.literal((registered ? "Registered " : "Struck from the register: "))
                .withStyle(registered ? ChatFormatting.GREEN : ChatFormatting.YELLOW)
                .append(target.getDisplayName().plainCopy())
                .append(Component.literal(registered ? " on the Animagus Registry." : "")));
        return 1;
    }

    private static int sendNotice(CommandSourceStack source, ServerPlayer target, String message) {
        MinistryPost.send(target,
                Component.translatable("ministry.wizards_and_beasts.notice.generic.subject"),
                Component.literal(message));
        say(source, Component.literal("Notice delivered to ").withStyle(ChatFormatting.GREEN)
                .append(target.getDisplayName().plainCopy()));
        return 1;
    }

    // ── administrative ──

    private static int setNotoriety(CommandSourceStack source, ServerPlayer target, float value) {
        MinistryRecords.mutate(target, record -> record.withNotoriety(value));
        PlayerMinistryRecord record = MinistryRecords.get(target);
        say(source, Component.literal(String.format(Locale.ROOT, "Notoriety set to %.1f — ", record.notoriety()))
                .withStyle(ChatFormatting.GRAY).append(record.wantedLevel().displayName()));
        return 1;
    }

    private static int reportOffence(CommandSourceStack source, ServerPlayer target, String rawOffence) {
        MagicalOffence offence = MagicalOffence.byName(rawOffence.toLowerCase(Locale.ROOT));
        if (offence == null) {
            say(source, Component.literal("Unknown offence: " + rawOffence).withStyle(ChatFormatting.RED));
            return 0;
        }
        WantedLevel level = TraceService.report(target, offence);
        say(source, Component.literal("Recorded ").withStyle(ChatFormatting.GRAY)
                .append(offence.displayName())
                .append(Component.literal(" — now "))
                .append(level.displayName()));
        return 1;
    }

    // ── helpers ──

    private static void say(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }

    private static String formatTicks(int ticks) {
        int seconds = ticks / 20;
        return seconds >= 60 ? (seconds / 60) + "m " + (seconds % 60) + "s" : seconds + "s";
    }
}
