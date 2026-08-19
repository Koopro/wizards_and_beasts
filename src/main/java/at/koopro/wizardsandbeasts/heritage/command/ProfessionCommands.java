package at.koopro.wizardsandbeasts.heritage.command;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.event.heritage.HeritageEvents;
import at.koopro.wizardsandbeasts.network.heritage.HeritageDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.profession.ProfessionNode;
import at.koopro.wizardsandbeasts.heritage.profession.ProfessionSystemAPI;
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

import java.util.List;
import java.util.stream.Stream;

/**
 * {@code /wandb player profession …}
 *
 * <p>Split out of {@link HeritageCommands}, where it was the larger half of the file. A profession
 * reads heritage to decide which tree you get, but it is its own progression system with its own
 * points, unlocks and events — nesting it made {@code heritage} the biggest node in the tree and
 * pushed every profession command a level deeper than the skills and vocations it sits beside.
 */
public final class ProfessionCommands {

    private ProfessionCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("profession")
                .then(Commands.literal("list")
                        .executes(ctx -> professionList(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                                .executes(ctx -> professionList(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("info")
                        .executes(ctx -> professionInfo(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
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
                                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                                .executes(ctx -> professionPoints(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> professionAddPoints(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "amount")))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(ctx -> professionSetPoints(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "amount")))))))
                .then(Commands.literal("reset")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> professionReset(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))));
    }

    private static Stream<String> suggestProfessions(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            Heritage type = player.getData(ModAttachments.HERITAGE_DATA.get()).getSelectedHeritage();
            if (type == null) {
                return Stream.of();
            }
            return ProfessionNode.byHeritage(type).stream().map(ProfessionNode::getId);
        } catch (Exception ignored) {
            return Stream.of();
        }
    }

    private static int professionList(CommandSourceStack source, ServerPlayer target) {
        PlayerHeritageData data = target.getData(ModAttachments.HERITAGE_DATA.get());
        Heritage type = data.getSelectedHeritage();
        if (type == null) {
            source.sendFailure(Component.literal("Player has no selected heritage.").withStyle(ChatFormatting.RED));
            return 0;
        }
        List<ProfessionNode> nodes = ProfessionNode.byHeritage(type);
        ChatReport report = ChatReport.of(target.getName().getString()
                + " · Profession Tree (" + type.getDisplayName() + ")");
        for (ProfessionNode node : nodes) {
            boolean unlocked = data.hasUnlockedProfession(node.getId());
            boolean selected = node.getId().equals(data.getSelectedProfessionId());
            String marker = selected ? "[*] " : unlocked ? "[+] " : "[ ] ";
            ChatFormatting markerStyle = selected ? ChatFormatting.AQUA : unlocked ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            report.item(Component.literal(marker).withStyle(markerStyle)
                    .append(Component.literal(node.getDisplayName()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" (" + node.getId() + ") [" + node.getPointCost() + " PP]")
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }
        report.send(source);
        return 1;
    }

    private static int professionInfo(CommandSourceStack source, ServerPlayer target) {
        PlayerHeritageData data = target.getData(ModAttachments.HERITAGE_DATA.get());
        Heritage type = data.getSelectedHeritage();
        if (type == null) {
            source.sendFailure(Component.literal("Player has no selected heritage.").withStyle(ChatFormatting.RED));
            return 0;
        }
        String activeId = data.getSelectedProfessionId();
        ProfessionNode active = activeId == null ? null : ProfessionNode.byId(activeId);
        ChatReport.of("Profession Info")
                .row("Heritage", type.getDisplayName())
                .row("Points", data.getProfessionPoints()
                        + " (" + data.getTotalProfessionPointsEarned() + " earned)")
                .row("Unlocked", String.valueOf(data.getUnlockedProfessions().size()))
                .row("Active", active == null ? "none" : active.getDisplayName() + " (" + active.getId() + ")")
                .send(source);
        return 1;
    }

    private static int professionUnlock(CommandSourceStack source, ServerPlayer player, String professionId) {
        ProfessionNode node = ProfessionNode.byId(professionId);
        if (node == null) {
            source.sendFailure(Component.literal("Unknown profession: " + professionId).withStyle(ChatFormatting.RED));
            return 0;
        }
        ProfessionSystemAPI.UnlockCheck check = ProfessionSystemAPI.evaluateUnlock(player, node);
        if (!check.allowed()) {
            source.sendFailure(Component.literal("Cannot unlock: " + check.reason()).withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!ProfessionSystemAPI.tryUnlock(player, node.getId())) {
            source.sendFailure(Component.literal("Failed to unlock profession.").withStyle(ChatFormatting.RED));
            return 0;
        }
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        HeritageDataSyncS2CPayload.syncToPlayer(player, false);
        NeoForge.EVENT_BUS.post(new HeritageEvents.PlayerProfessionUnlockedEvent(player, node, data.getProfessionPoints()));
        source.sendSuccess(() -> Component.literal("Unlocked profession " + node.getDisplayName()
                + " for " + player.getName().getString()).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int professionSelect(CommandSourceStack source, ServerPlayer player, String professionId) {
        ProfessionNode node = ProfessionNode.byId(professionId);
        if (node == null) {
            source.sendFailure(Component.literal("Unknown profession: " + professionId).withStyle(ChatFormatting.RED));
            return 0;
        }
        ProfessionSystemAPI.UnlockCheck check = ProfessionSystemAPI.evaluateSelect(player, node);
        if (!check.allowed()) {
            source.sendFailure(Component.literal("Cannot select: " + check.reason()).withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!ProfessionSystemAPI.trySelect(player, node.getId())) {
            source.sendFailure(Component.literal("Failed to select profession.").withStyle(ChatFormatting.RED));
            return 0;
        }
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        HeritageDataSyncS2CPayload.syncToPlayer(player, false);
        NeoForge.EVENT_BUS.post(new HeritageEvents.PlayerProfessionSelectedEvent(player, node, data.getProfessionPoints()));
        source.sendSuccess(() -> Component.literal("Active profession set to "
                + node.getDisplayName() + " for " + player.getName().getString()).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int professionPoints(CommandSourceStack source, ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        source.sendSuccess(() -> Component.literal("Profession Points for " + player.getName().getString() + ": ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(data.getProfessionPoints())).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (" + data.getTotalProfessionPointsEarned() + " total earned)")
                        .withStyle(ChatFormatting.DARK_GRAY)), false);
        return 1;
    }

    private static int professionAddPoints(CommandSourceStack source, ServerPlayer player, int amount) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        data.addProfessionPoints(amount);
        HeritageDataSyncS2CPayload.syncToPlayer(player, false);
        source.sendSuccess(() -> Component.literal("Added " + amount + " profession points to "
                + player.getName().getString()).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int professionSetPoints(CommandSourceStack source, ServerPlayer player, int amount) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        data.setProfessionPoints(amount);
        HeritageDataSyncS2CPayload.syncToPlayer(player, false);
        source.sendSuccess(() -> Component.literal("Set profession points for "
                + player.getName().getString() + " to " + amount).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int professionReset(CommandSourceStack source, ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        data.resetProfessionProgress();
        HeritageDataSyncS2CPayload.syncToPlayer(player, false);
        NeoForge.EVENT_BUS.post(new HeritageEvents.PlayerProfessionResetEvent(player));
        source.sendSuccess(() -> Component.literal("Reset profession progress for "
                + player.getName().getString()).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }
}
