package at.koopro.wizardsandbeasts.heritage.command;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.event.heritage.HeritageEvents;
import at.koopro.wizardsandbeasts.network.heritage.HeritageDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.profession.ProfessionNode;
import at.koopro.wizardsandbeasts.util.ChatReport;
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
                        .executes(ctx -> list(ctx.getSource())));
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
