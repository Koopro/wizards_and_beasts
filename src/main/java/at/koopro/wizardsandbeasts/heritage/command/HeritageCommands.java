package at.koopro.wizardsandbeasts.heritage.command;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.event.heritage.HeritageEvents;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfConfig;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfRules;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfState;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfTransformService;
import at.koopro.wizardsandbeasts.heritage.werewolf.Wolfsbane;
import at.koopro.wizardsandbeasts.heritage.profession.ProfessionNode;
import at.koopro.wizardsandbeasts.util.ChatReport;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Arrays;

/**
 * {@code /wandb player heritage …} — the heritage itself: what you are, and the roster of what
 * you could be.
 *
 * <p>Professions, O.W.L.s, form and size all used to hang off this node. They read heritage but are
 * their own systems, so they now sit beside it under {@code player} rather than inside it — see
 * {@link ProfessionCommands} and {@link AppearanceCommands}.
 */
public final class HeritageCommands {

    /** Comfortably after nightfall (night starts at 12300), so the first scan after the jump counts. */
    private static final long NIGHT_TARGET_TIME = 14000L;

    private HeritageCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("heritage")
                .then(Commands.literal("info")
                        .executes(ctx -> info(
                                ctx.getSource(),
                                ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> info(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("set")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(Heritage.values()).map(Heritage::getId), builder))
                                        .then(Commands.argument("subtype", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    String typeName = StringArgumentType.getString(ctx, "type");
                                                    Heritage type = Heritage.byId(typeName);
                                                    if (type != null) {
                                                        return SharedSuggestionProvider.suggest(
                                                                type.getSubtypes().stream().map(HeritageVariant::getId), builder);
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> set(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "type"),
                                                        StringArgumentType.getString(ctx, "subtype")))))))
                .then(Commands.literal("reset")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reset(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())))
                // The blood pool a blood_hunger lineage lives on. Its own file for the same reason the
                // werewolf verbs below are not: they are one heritage's mechanics, not heritage itself.
                .then(BloodCommands.register())
                // Deliberately unguarded by ADMIN: this is a gameplay action a medicated werewolf
                // performs on themselves, and it refuses on its own terms when they are not in a
                // position to take it. See WerewolfTransformService.requestVoluntaryRevert.
                .then(Commands.literal("werewolf")
                        .then(Commands.literal("revert")
                                .executes(ctx -> revertWerewolf(ctx.getSource())))
                        .then(Commands.literal("status")
                                .executes(ctx -> werewolfStatus(
                                        ctx.getSource(), ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> werewolfStatus(
                                                ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        // Testing aids. Admin-gated, and both are shortcuts through conditions the
                        // system checks for itself rather than back doors around them -- "moon" moves
                        // the world clock and nothing else, so the ordinary scan does the actual work.
                        .then(Commands.literal("moon")
                                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                                .executes(ctx -> setFullMoonNight(ctx.getSource())))
                        .then(Commands.literal("transform")
                                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                                .executes(ctx -> forceTransform(
                                        ctx.getSource(), ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> forceTransform(
                                                ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("wolfsbane")
                                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> doseWolfsbane(
                                                ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), 0))
                                        .then(Commands.argument("amplifier", IntegerArgumentType.integer(0, 10))
                                                .executes(ctx -> doseWolfsbane(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "amplifier")))))));
    }

    /**
     * A werewolf giving the shape back by choice. Only ever possible under Wolfsbane — an unmedicated
     * wolf is refused, which is the loss-of-control contract's "cannot cancel the transform" clause.
     */
    private static int revertWerewolf(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return WerewolfTransformService.requestVoluntaryRevert(player) ? 1 : 0;
    }

    /**
     * Winds the world clock to the next full-moon night.
     *
     * <p>Deliberately moves <em>only the clock</em>. It does not transform anyone, does not touch the
     * exposure counter and does not bypass the sky check — the ordinary
     * {@code WerewolfMoonHandler} scan then runs exactly as it would on a real full moon, which is the
     * only way a test of it is worth anything. A werewolf standing in a cellar still will not turn.
     *
     * <p>Vanilla numbers the phases by day count modulo 8 with 0 as full, so the target is the next such
     * day; 14000 puts it comfortably after nightfall (night starts at 12300).
     */
    private static int setFullMoonNight(CommandSourceStack source) {
        net.minecraft.server.level.ServerLevel level = source.getLevel();
        if (WerewolfRules.fullMoonNight(level)) {
            source.sendSuccess(() -> Component.literal(
                    "Already a full-moon night here. Nothing to move."), false);
            return 1;
        }
        long dayTime = level.getDayTime();
        long day = dayTime / 24000L;
        // Today still counts if it is a full-moon day and night has not yet been missed. Without this
        // the "already the right day, just too early" case would skip a whole eight-day cycle, which is
        // the exact case a tester hits after running this command once.
        boolean tonightStillWorks = WerewolfRules.moonPhase(dayTime) == WerewolfRules.FULL_MOON
                && Math.floorMod(dayTime, 24000L) < NIGHT_TARGET_TIME;
        long targetDay = tonightStillWorks
                ? day
                : day + Math.floorMod(-day, 8L) + (Math.floorMod(day, 8L) == 0 ? 8L : 0L);
        long target = targetDay * 24000L + NIGHT_TARGET_TIME;
        level.setDayTime(target);
        source.sendSuccess(() -> Component.literal("Set " + level.dimension().identifier()
                + " to a full-moon night (day " + targetDay + ", time " + target + ")."), true);
        return 1;
    }

    /**
     * Starts a forced change now, skipping only the moonlight-exposure wait.
     *
     * <p>Everything after that is the real path: the TRANSITIONING window, the delay, the equipment
     * strip, the attributes and the loss-of-control flag all come from
     * {@code WerewolfTransformService.beginForcedTransform}. It still refuses for a non-werewolf, for a
     * player already in wolf shape, and while Wolfsbane suppression applies — those are the conditions
     * under test, not obstacles to it.
     */
    private static int forceTransform(CommandSourceStack source, ServerPlayer target) {
        PlayerHeritageData data = target.getData(ModAttachments.HERITAGE_DATA.get());
        if (!WerewolfRules.isWerewolf(data)) {
            source.sendFailure(Component.literal(target.getName().getString() + " is not a werewolf.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!(target.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return 0;
        }
        if (!WerewolfTransformService.beginForcedTransform(target, level, data)) {
            source.sendFailure(Component.literal(
                            "The change was refused: already a wolf, one is already in flight, "
                                    + "forced transforms are disabled, or Wolfsbane is suppressing it.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("The moon takes " + target.getName().getString() + "."), true);
        return 1;
    }

    /** Doses a werewolf from code, so the Wolfsbane config keys are reachable without brewing. */
    private static int doseWolfsbane(CommandSourceStack source, ServerPlayer target, int amplifier) {
        Wolfsbane.apply(target, amplifier);
        int ticks = Wolfsbane.durationFor(amplifier);
        source.sendSuccess(() -> Component.literal("Dosed " + target.getName().getString()
                + " with Wolfsbane for " + ticks + " ticks (amplifier " + amplifier + ")."), true);
        return 1;
    }

    /**
     * What the moon and the potion are doing to this werewolf right now. Readable by anyone about
     * anyone: none of it is secret, and every one of these numbers is something a player watching
     * themselves transform would want to be able to check.
     */
    private static int werewolfStatus(CommandSourceStack source, ServerPlayer target) {
        PlayerHeritageData data = target.getData(ModAttachments.HERITAGE_DATA.get());
        if (!WerewolfRules.isWerewolf(data)) {
            source.sendFailure(Component.literal(target.getName().getString() + " is not a werewolf.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        boolean moonUp = target.level() instanceof net.minecraft.server.level.ServerLevel level
                && WerewolfRules.fullMoonNight(level);
        ChatReport.of("Werewolf — " + target.getName().getString())
                .flag("Full moon", moonUp)
                .meter("Moonlight", WerewolfState.getExposure(data),
                        Math.max(1, WerewolfConfig.exposureThreshold))
                .row("State", data.getTransformationState().name())
                .row("Form", data.getActiveFormId() == null ? "none" : data.getActiveFormId())
                .flag("Wolfsbane", WerewolfRules.hasWolfsbane(target))
                .flag("Loss of control", WerewolfState.isLossOfControl(data))
                .flag("Staying by choice", WerewolfState.isVoluntary(data))
                .send(source);
        return 1;
    }

    private static int info(CommandSourceStack source, ServerPlayer target) {
        PlayerHeritageData data = target.getData(ModAttachments.HERITAGE_DATA.get());

        ChatReport report = ChatReport.of(target.getName().getString() + "'s Heritage");

        if (!data.hasHeritageSelected()) {
            report.note("No heritage selected.").send(source);
            return 1;
        }

        Heritage type = data.getSelectedHeritage();
        HeritageVariant subtype = data.getSelectedHeritageVariant();

        report.row("Heritage", type.getDisplayName())
                .row("Variant", subtype.getDisplayName())
                .flag("Locked", data.isLocked())
                .row("Magic", type.getMagicSource().getDisplayName())
                .flag("Wand", type.canUseWand())
                .row("Stats", String.format("HP %+.0f  SPD %+.3f  ARM %+.0f",
                        subtype.getTotalHealth(), subtype.getTotalSpeed(), subtype.getTotalArmor()));

        if (!subtype.getTags().isEmpty()) {
            report.row("Tags", String.join(", ", subtype.getTags()));
        }

        String selectedProfessionId = data.getSelectedProfessionId();
        ProfessionNode selectedProfession = selectedProfessionId == null ? null : ProfessionNode.byId(selectedProfessionId);
        report.row("Profession", selectedProfession != null
                        ? selectedProfession.getDisplayName() + " (" + selectedProfession.getId() + ")"
                        : selectedProfessionId == null ? "none" : selectedProfessionId)
                .row("Points", data.getProfessionPoints() + " (" + data.getTotalProfessionPointsEarned() + " earned)")
                .row("Transform", data.getTransformationState().name())
                .send(source);

        return 1;
    }

    private static int set(CommandSourceStack source, ServerPlayer target,
                           String typeId, String subtypeId) {
        Heritage type = Heritage.byId(typeId);
        if (type == null) {
            source.sendFailure(Component.literal("Unknown heritage: " + typeId).withStyle(ChatFormatting.RED));
            return 0;
        }

        HeritageVariant subtype = HeritageVariant.byId(subtypeId);
        if (subtype == null || subtype.getParentHeritage() != type) {
            source.sendFailure(Component.literal("Invalid variant '" + subtypeId
                    + "' for heritage " + type.getDisplayName()).withStyle(ChatFormatting.RED));
            return 0;
        }

        PlayerHeritageData data = target.getData(ModAttachments.HERITAGE_DATA.get());
        data.resetProfessionProgress();
        data.addProfessionPoints(3);

        // The same routine the first-join gate runs. This used to set the fields, apply the attribute
        // modifiers and sync to the one player — which left the body, the POWER band, the ability grants
        // and every other client's copy describing the heritage the target used to be.
        HeritageAPI.commit(target, type, subtype);

        NeoForge.EVENT_BUS.post(new HeritageEvents.PlayerHeritageChangedEvent(target, type, subtype));

        source.sendSuccess(() -> Component.literal("Set " + target.getName().getString()
                + " to " + type.getDisplayName() + " (" + subtype.getDisplayName() + ")").withStyle(ChatFormatting.GREEN), false);
        target.displayClientMessage(Component.literal(
                "Your heritage has been set to " + type.getDisplayName()
                        + " (" + subtype.getDisplayName() + ")").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int reset(CommandSourceStack source, ServerPlayer target) {
        // true: put the client back in front of the gate. Clearing the data without reopening it leaves a
        // player with no heritage and no way to choose one.
        HeritageAPI.clear(target, true);

        NeoForge.EVENT_BUS.post(new HeritageEvents.PlayerHeritageResetEvent(target));

        source.sendSuccess(() -> Component.literal(
                "Reset " + target.getName().getString() + "'s heritage.").withStyle(ChatFormatting.YELLOW), false);
        target.displayClientMessage(Component.literal(
                "Your heritage has been reset. Choose again.").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        ChatReport report = ChatReport.of("Heritages & Variants");

        for (Heritage type : Heritage.values()) {
            report.item(type.getDisplayName() + " (" + type.getId() + ")"
                    + " — " + type.getMagicSource().getDisplayName()
                    + ", " + type.getSizeCategory().getDisplayName());

            for (HeritageVariant sub : type.getSubtypes()) {
                report.subItem(Component.literal(sub.getDisplayName() + " (" + sub.getId() + ") — ")
                        .append(Component.translatable(sub.getDescriptionTranslationKey())));
            }
        }
        report.send(source);
        return 1;
    }
}
