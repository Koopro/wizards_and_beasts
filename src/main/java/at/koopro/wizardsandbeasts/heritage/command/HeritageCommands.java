package at.koopro.wizardsandbeasts.heritage.command;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.owl.command.OWLCommands;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.event.heritage.HeritageEvents;
import at.koopro.wizardsandbeasts.network.heritage.HeritageDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.profession.ProfessionNode;
import at.koopro.wizardsandbeasts.heritage.profession.ProfessionSystemAPI;
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
import java.util.List;
import java.util.stream.Stream;

public final class HeritageCommands {

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
                .then(Commands.literal("profession")
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
                                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> professionReset(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("reset")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reset(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())))
                .then(OWLCommands.register())
                .then(WizFormCommands.register())
                .then(WizSizeCommands.register());
    }

    private static int info(CommandSourceStack source, ServerPlayer target) {
        PlayerHeritageData data = target.getData(ModAttachments.HERITAGE_DATA.get());

        source.sendSuccess(() -> Component.literal("--- " + target.getName().getString() + "'s Heritage Profile ---")
                .withStyle(ChatFormatting.GOLD), false);

        if (!data.hasHeritageSelected()) {
            source.sendSuccess(() -> Component.literal("No heritage selected.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        Heritage type = data.getSelectedHeritage();
        HeritageVariant subtype = data.getSelectedHeritageVariant();

        source.sendSuccess(() -> Component.literal("Heritage: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(type.getDisplayName()).withStyle(ChatFormatting.WHITE)), false);
        source.sendSuccess(() -> Component.literal("Variant: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(subtype.getDisplayName()).withStyle(ChatFormatting.WHITE)), false);
        source.sendSuccess(() -> Component.literal("Locked: ").withStyle(ChatFormatting.GRAY)
                .append(data.isLocked()
                        ? Component.literal("Yes").withStyle(ChatFormatting.GREEN)
                        : Component.literal("No").withStyle(ChatFormatting.RED)), false);
        source.sendSuccess(() -> Component.literal("Magic: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(type.getMagicSource().getDisplayName()).withStyle(ChatFormatting.WHITE)), false);
        source.sendSuccess(() -> Component.literal("Wand: ").withStyle(ChatFormatting.GRAY)
                .append(type.canUseWand()
                        ? Component.literal("Yes").withStyle(ChatFormatting.GREEN)
                        : Component.literal("No").withStyle(ChatFormatting.RED)), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "Stats: HP %+.0f  SPD %+.3f  ARM %+.0f",
                subtype.getTotalHealth(), subtype.getTotalSpeed(), subtype.getTotalArmor()))
                .withStyle(ChatFormatting.GRAY), false);

        if (!subtype.getTags().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Tags: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.join(", ", subtype.getTags())).withStyle(ChatFormatting.WHITE)), false);
        }
        String selectedProfessionId = data.getSelectedProfessionId();
        String professionName = selectedProfessionId == null ? "None" : selectedProfessionId;
        ProfessionNode selectedProfession = selectedProfessionId == null ? null : ProfessionNode.byId(selectedProfessionId);
        if (selectedProfession != null) {
            professionName = selectedProfession.getDisplayName() + " (" + selectedProfession.getId() + ")";
        }
        final String finalProfessionName = professionName;
        source.sendSuccess(() -> Component.literal("Profession: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(finalProfessionName).withStyle(ChatFormatting.WHITE)), false);
        source.sendSuccess(() -> Component.literal("Profession Points: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(data.getProfessionPoints())).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (" + data.getTotalProfessionPointsEarned() + " total earned)")
                        .withStyle(ChatFormatting.DARK_GRAY)), false);
        source.sendSuccess(() -> Component.literal("Transform: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(data.getTransformationState().name()).withStyle(ChatFormatting.WHITE)), false);

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
        data.setSelectedHeritage(type);
        data.setSelectedHeritageVariant(subtype);
        data.setLocked(true);
        data.resetProfessionProgress();
        data.addProfessionPoints(3);

        HeritageAPI.applyStats(target);
        HeritageDataSyncS2CPayload.syncToPlayer(target, false);

        NeoForge.EVENT_BUS.post(new HeritageEvents.PlayerHeritageChangedEvent(target, type, subtype));

        source.sendSuccess(() -> Component.literal("Set " + target.getName().getString()
                + " to " + type.getDisplayName() + " (" + subtype.getDisplayName() + ")").withStyle(ChatFormatting.GREEN), false);
        target.displayClientMessage(Component.literal(
                "Your heritage has been set to " + type.getDisplayName()
                        + " (" + subtype.getDisplayName() + ")").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int reset(CommandSourceStack source, ServerPlayer target) {
        PlayerHeritageData data = target.getData(ModAttachments.HERITAGE_DATA.get());
        data.reset();

        HeritageAPI.removeStats(target);
        HeritageDataSyncS2CPayload.syncToPlayer(target, true);

        NeoForge.EVENT_BUS.post(new HeritageEvents.PlayerHeritageResetEvent(target));

        source.sendSuccess(() -> Component.literal(
                "Reset " + target.getName().getString() + "'s heritage.").withStyle(ChatFormatting.YELLOW), false);
        target.displayClientMessage(Component.literal(
                "Your heritage has been reset. Choose again.").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("--- Heritages & Variants ---").withStyle(ChatFormatting.GOLD), false);

        for (Heritage type : Heritage.values()) {
            source.sendSuccess(() -> Component.literal(
                    " " + type.getDisplayName() + " (" + type.getId() + ")"
                            + " — " + type.getMagicSource().getDisplayName()
                            + ", " + type.getSizeCategory().getDisplayName())
                    .withStyle(ChatFormatting.YELLOW), false);

            for (HeritageVariant sub : type.getSubtypes()) {
                source.sendSuccess(() -> Component.literal(
                        "   " + sub.getDisplayName() + " (" + sub.getId() + ")" + " — ")
                        .append(Component.translatable(sub.getDescriptionTranslationKey()))
                        .withStyle(ChatFormatting.GRAY), false);
            }
        }
        return 1;
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
        source.sendSuccess(() -> Component.literal("--- " + target.getName().getString()
                + " Profession Tree (" + type.getDisplayName() + ") ---").withStyle(ChatFormatting.GOLD), false);
        for (ProfessionNode node : nodes) {
            boolean unlocked = data.hasUnlockedProfession(node.getId());
            boolean selected = node.getId().equals(data.getSelectedProfessionId());
            String marker = selected ? "[*] " : unlocked ? "[+] " : "[ ] ";
            ChatFormatting markerStyle = selected ? ChatFormatting.AQUA : unlocked ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            source.sendSuccess(() -> Component.literal(marker).withStyle(markerStyle)
                    .append(Component.literal(node.getDisplayName()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" (" + node.getId() + ") [" + node.getPointCost() + " PP]")
                            .withStyle(ChatFormatting.DARK_GRAY)), false);
        }
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
        source.sendSuccess(() -> Component.literal("--- Profession Info ---").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("Heritage: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(type.getDisplayName()).withStyle(ChatFormatting.WHITE)), false);
        source.sendSuccess(() -> Component.literal("Points: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(data.getProfessionPoints())).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (" + data.getTotalProfessionPointsEarned() + " total earned)")
                        .withStyle(ChatFormatting.DARK_GRAY)), false);
        source.sendSuccess(() -> Component.literal("Unlocked: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(data.getUnlockedProfessions().size())).withStyle(ChatFormatting.WHITE)), false);
        source.sendSuccess(() -> Component.literal("Active: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(active == null ? "None" : active.getDisplayName() + " (" + active.getId() + ")")
                        .withStyle(ChatFormatting.WHITE)), false);
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
