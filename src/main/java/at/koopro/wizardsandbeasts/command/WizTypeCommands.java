package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.data.PlayerTypeData;
import at.koopro.wizardsandbeasts.event.TypeEvents;
import at.koopro.wizardsandbeasts.network.TypeDataSyncS2CPacket;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.type.TypeSystemAPI;
import at.koopro.wizardsandbeasts.type.WizSubtype;
import at.koopro.wizardsandbeasts.type.WizType;
import at.koopro.wizardsandbeasts.type.profession.ProfessionNode;
import at.koopro.wizardsandbeasts.type.profession.ProfessionSystemAPI;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public final class WizTypeCommands {

    private WizTypeCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register(String literal) {
        return Commands.literal(literal)
                .then(Commands.literal("info")
                        .executes(ctx -> info(
                                ctx.getSource(),
                                ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> info(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("set")
                        .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(WizType.values()).map(WizType::getId), builder))
                                        .then(Commands.argument("subtype", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    String typeName = StringArgumentType.getString(ctx, "type");
                                                    WizType type = WizType.byId(typeName);
                                                    if (type != null) {
                                                        return SharedSuggestionProvider.suggest(
                                                                type.getSubtypes().stream().map(WizSubtype::getId), builder);
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> set(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "type"),
                                                        StringArgumentType.getString(ctx, "subtype")))))))
                .then(Commands.literal("profession")
                        .then(Commands.literal("list")
                                .executes(ctx -> professionList(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
                                        .executes(ctx -> professionList(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("info")
                                .executes(ctx -> professionInfo(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
                                        .executes(ctx -> professionInfo(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("unlock")
                                .then(Commands.argument("profession", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(suggestProfessions(ctx), builder))
                                        .executes(ctx -> professionUnlock(
                                                ctx.getSource(),
                                                ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "profession")))))
                        .then(Commands.literal("select")
                                .then(Commands.argument("profession", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(suggestProfessions(ctx), builder))
                                        .executes(ctx -> professionSelect(
                                                ctx.getSource(),
                                                ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "profession")))))
                        .then(Commands.literal("points")
                                .executes(ctx -> professionPoints(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
                                        .executes(ctx -> professionPoints(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))
                                        .then(Commands.literal("add")
                                                .then(Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                                        .executes(ctx -> professionAddPoints(
                                                                ctx.getSource(),
                                                                EntityArgument.getPlayer(ctx, "player"),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount")))))
                                        .then(Commands.literal("set")
                                                .then(Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                                        .executes(ctx -> professionSetPoints(
                                                                ctx.getSource(),
                                                                EntityArgument.getPlayer(ctx, "player"),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount")))))))
                        .then(Commands.literal("reset")
                                .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> professionReset(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("reset")
                        .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reset(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())));
    }

    private static int info(CommandSourceStack source, ServerPlayer target) {
        PlayerTypeData data = target.getData(ModAttachments.TYPE_DATA.get());

        source.sendSuccess(() -> Component.literal(
                "\u00A76--- " + target.getName().getString() + "'s Type Profile ---"), false);

        if (!data.hasTypeSelected()) {
            source.sendSuccess(() -> Component.literal("\u00A77No type selected."), false);
            return 1;
        }

        WizType type = data.getSelectedType();
        WizSubtype subtype = data.getSelectedSubtype();

        source.sendSuccess(() -> Component.literal("\u00A77Type: \u00A7f" + type.getDisplayName()), false);
        source.sendSuccess(() -> Component.literal("\u00A77Subtype: \u00A7f" + subtype.getDisplayName()), false);
        source.sendSuccess(() -> Component.literal("\u00A77Locked: "
                + (data.isLocked() ? "\u00A7aYes" : "\u00A7cNo")), false);
        source.sendSuccess(() -> Component.literal("\u00A77Magic: \u00A7f"
                + type.getMagicSource().getDisplayName()), false);
        source.sendSuccess(() -> Component.literal("\u00A77Wand: "
                + (type.canUseWand() ? "\u00A7aYes" : "\u00A7cNo")), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "\u00A77Stats: \u00A7fHP %+.0f  SPD %+.3f  ARM %+.0f",
                subtype.getTotalHealth(), subtype.getTotalSpeed(), subtype.getTotalArmor())), false);

        if (!subtype.getTags().isEmpty()) {
            source.sendSuccess(() -> Component.literal("\u00A77Tags: \u00A7f"
                    + String.join(", ", subtype.getTags())), false);
        }
        String selectedProfessionId = data.getSelectedProfessionId();
        String professionName = selectedProfessionId == null ? "None" : selectedProfessionId;
        ProfessionNode selectedProfession = selectedProfessionId == null ? null : ProfessionNode.byId(selectedProfessionId);
        if (selectedProfession != null) {
            professionName = selectedProfession.getDisplayName() + " (" + selectedProfession.getId() + ")";
        }
        final String finalProfessionName = professionName;
        source.sendSuccess(() -> Component.literal("\u00A77Profession: \u00A7f" + finalProfessionName), false);
        source.sendSuccess(() -> Component.literal("\u00A77Profession Points: \u00A7f"
                + data.getProfessionPoints() + "\u00A77 (" + data.getTotalProfessionPointsEarned() + " total earned)"), false);

        source.sendSuccess(() -> Component.literal("\u00A77Transform: \u00A7f"
                + data.getTransformationState().name()), false);

        return 1;
    }

    private static int set(CommandSourceStack source, ServerPlayer target,
                           String typeId, String subtypeId) {
        WizType type = WizType.byId(typeId);
        if (type == null) {
            source.sendFailure(Component.literal("\u00A7cUnknown type: " + typeId));
            return 0;
        }

        WizSubtype subtype = WizSubtype.byId(subtypeId);
        if (subtype == null || subtype.getParentType() != type) {
            source.sendFailure(Component.literal("\u00A7cInvalid subtype '" + subtypeId
                    + "' for type " + type.getDisplayName()));
            return 0;
        }

        PlayerTypeData data = target.getData(ModAttachments.TYPE_DATA.get());
        data.setSelectedType(type);
        data.setSelectedSubtype(subtype);
        data.setLocked(true);
        data.resetProfessionProgress();
        data.addProfessionPoints(3);

        TypeSystemAPI.applyStats(target);
        TypeDataSyncS2CPacket.syncToPlayer(target, false);

        NeoForge.EVENT_BUS.post(new TypeEvents.PlayerTypeChangedEvent(target, type, subtype));

        source.sendSuccess(() -> Component.literal("\u00A7aSet " + target.getName().getString()
                + " to " + type.getDisplayName() + " (" + subtype.getDisplayName() + ")"), false);
        target.displayClientMessage(Component.literal(
                "\u00A7eYour heritage has been set to " + type.getDisplayName()
                        + " (" + subtype.getDisplayName() + ")"), false);
        return 1;
    }

    private static int reset(CommandSourceStack source, ServerPlayer target) {
        PlayerTypeData data = target.getData(ModAttachments.TYPE_DATA.get());
        data.reset();

        TypeSystemAPI.removeStats(target);
        TypeDataSyncS2CPacket.syncToPlayer(target, true);

        NeoForge.EVENT_BUS.post(new TypeEvents.PlayerTypeResetEvent(target));

        source.sendSuccess(() -> Component.literal(
                "\u00A7eReset " + target.getName().getString() + "'s heritage."), false);
        target.displayClientMessage(Component.literal(
                "\u00A7eYour heritage has been reset. Choose again."), false);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("\u00A76--- Types & Subtypes ---"), false);

        for (WizType type : WizType.values()) {
            source.sendSuccess(() -> Component.literal(
                    " \u00A7e" + type.getDisplayName() + " \u00A78(" + type.getId() + ")"
                            + " \u00A77- " + type.getMagicSource().getDisplayName()
                            + ", " + type.getSizeCategory().getDisplayName()), false);

            for (WizSubtype sub : type.getSubtypes()) {
                source.sendSuccess(() -> Component.literal(
                        "   \u00A7f" + sub.getDisplayName() + " \u00A78(" + sub.getId() + ")"
                                + " \u00A77- " + sub.getDescription()), false);
            }
        }
        return 1;
    }

    private static Stream<String> suggestProfessions(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            WizType type = player.getData(ModAttachments.TYPE_DATA.get()).getSelectedType();
            if (type == null) {
                return Stream.of();
            }
            return ProfessionNode.byType(type).stream().map(ProfessionNode::getId);
        } catch (Exception ignored) {
            return Stream.of();
        }
    }

    private static int professionList(CommandSourceStack source, ServerPlayer target) {
        PlayerTypeData data = target.getData(ModAttachments.TYPE_DATA.get());
        WizType type = data.getSelectedType();
        if (type == null) {
            source.sendFailure(Component.literal("?cPlayer has no selected type."));
            return 0;
        }
        List<ProfessionNode> nodes = ProfessionNode.byType(type);
        source.sendSuccess(() -> Component.literal("?6--- " + target.getName().getString()
                + " Profession Tree (" + type.getDisplayName() + ") ---"), false);
        for (ProfessionNode node : nodes) {
            boolean unlocked = data.hasUnlockedProfession(node.getId());
            boolean selected = node.getId().equals(data.getSelectedProfessionId());
            String marker = selected ? "?b?" : unlocked ? "?a?" : "?7?";
            source.sendSuccess(() -> Component.literal(" " + marker + " ?f" + node.getDisplayName()
                    + " ?8(" + node.getId() + ") ?7[" + node.getPointCost() + " PP]"), false);
        }
        return 1;
    }

    private static int professionInfo(CommandSourceStack source, ServerPlayer target) {
        PlayerTypeData data = target.getData(ModAttachments.TYPE_DATA.get());
        WizType type = data.getSelectedType();
        if (type == null) {
            source.sendFailure(Component.literal("?cPlayer has no selected type."));
            return 0;
        }
        String activeId = data.getSelectedProfessionId();
        ProfessionNode active = activeId == null ? null : ProfessionNode.byId(activeId);
        source.sendSuccess(() -> Component.literal("?6--- Profession Info ---"), false);
        source.sendSuccess(() -> Component.literal("?7Type: ?f" + type.getDisplayName()), false);
        source.sendSuccess(() -> Component.literal("?7Points: ?f" + data.getProfessionPoints()
                + " ?7(" + data.getTotalProfessionPointsEarned() + " total earned)"), false);
        source.sendSuccess(() -> Component.literal("?7Unlocked: ?f" + data.getUnlockedProfessions().size()), false);
        source.sendSuccess(() -> Component.literal("?7Active: ?f"
                + (active == null ? "None" : active.getDisplayName() + " (" + active.getId() + ")")), false);
        return 1;
    }

    private static int professionUnlock(CommandSourceStack source, ServerPlayer player, String professionId) {
        ProfessionNode node = ProfessionNode.byId(professionId);
        if (node == null) {
            source.sendFailure(Component.literal("?cUnknown profession: " + professionId));
            return 0;
        }
        ProfessionSystemAPI.UnlockCheck check = ProfessionSystemAPI.evaluateUnlock(player, node);
        if (!check.allowed()) {
            source.sendFailure(Component.literal("?cCannot unlock: " + check.reason()));
            return 0;
        }
        if (!ProfessionSystemAPI.tryUnlock(player, node.getId())) {
            source.sendFailure(Component.literal("?cFailed to unlock profession."));
            return 0;
        }
        PlayerTypeData data = player.getData(ModAttachments.TYPE_DATA.get());
        TypeDataSyncS2CPacket.syncToPlayer(player, false);
        NeoForge.EVENT_BUS.post(new TypeEvents.PlayerProfessionUnlockedEvent(player, node, data.getProfessionPoints()));
        source.sendSuccess(() -> Component.literal("?aUnlocked profession " + node.getDisplayName()
                + " for " + player.getName().getString()), false);
        return 1;
    }

    private static int professionSelect(CommandSourceStack source, ServerPlayer player, String professionId) {
        ProfessionNode node = ProfessionNode.byId(professionId);
        if (node == null) {
            source.sendFailure(Component.literal("?cUnknown profession: " + professionId));
            return 0;
        }
        ProfessionSystemAPI.UnlockCheck check = ProfessionSystemAPI.evaluateSelect(player, node);
        if (!check.allowed()) {
            source.sendFailure(Component.literal("?cCannot select: " + check.reason()));
            return 0;
        }
        if (!ProfessionSystemAPI.trySelect(player, node.getId())) {
            source.sendFailure(Component.literal("?cFailed to select profession."));
            return 0;
        }
        PlayerTypeData data = player.getData(ModAttachments.TYPE_DATA.get());
        TypeDataSyncS2CPacket.syncToPlayer(player, false);
        NeoForge.EVENT_BUS.post(new TypeEvents.PlayerProfessionSelectedEvent(player, node, data.getProfessionPoints()));
        source.sendSuccess(() -> Component.literal("?aActive profession set to "
                + node.getDisplayName() + " for " + player.getName().getString()), false);
        return 1;
    }

    private static int professionPoints(CommandSourceStack source, ServerPlayer player) {
        PlayerTypeData data = player.getData(ModAttachments.TYPE_DATA.get());
        source.sendSuccess(() -> Component.literal("?7Profession Points for ?f"
                + player.getName().getString() + "?7: ?f" + data.getProfessionPoints()
                + " ?7(" + data.getTotalProfessionPointsEarned() + " total earned)"), false);
        return 1;
    }

    private static int professionAddPoints(CommandSourceStack source, ServerPlayer player, int amount) {
        PlayerTypeData data = player.getData(ModAttachments.TYPE_DATA.get());
        data.addProfessionPoints(amount);
        TypeDataSyncS2CPacket.syncToPlayer(player, false);
        source.sendSuccess(() -> Component.literal("?aAdded " + amount + " profession points to "
                + player.getName().getString()), false);
        return 1;
    }

    private static int professionSetPoints(CommandSourceStack source, ServerPlayer player, int amount) {
        PlayerTypeData data = player.getData(ModAttachments.TYPE_DATA.get());
        data.setProfessionPoints(amount);
        TypeDataSyncS2CPacket.syncToPlayer(player, false);
        source.sendSuccess(() -> Component.literal("?aSet profession points for "
                + player.getName().getString() + " to " + amount), false);
        return 1;
    }

    private static int professionReset(CommandSourceStack source, ServerPlayer player) {
        PlayerTypeData data = player.getData(ModAttachments.TYPE_DATA.get());
        data.resetProfessionProgress();
        TypeDataSyncS2CPacket.syncToPlayer(player, false);
        NeoForge.EVENT_BUS.post(new TypeEvents.PlayerProfessionResetEvent(player));
        source.sendSuccess(() -> Component.literal("?eReset profession progress for "
                + player.getName().getString()), false);
        return 1;
    }

}
