package at.koopro.neo.command;

import at.koopro.neo.data.PlayerTypeData;
import at.koopro.neo.event.TypeEvents;
import at.koopro.neo.network.TypeDataSyncS2CPacket;
import at.koopro.neo.registry.ModAttachments;
import at.koopro.neo.type.TypeSystemAPI;
import at.koopro.neo.type.WizSubtype;
import at.koopro.neo.type.WizType;
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

public final class WizTypeCommands {

    private WizTypeCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("wiztype")
                .then(Commands.literal("info")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> info(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("set")
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
                .then(Commands.literal("reset")
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
                "\u00A76--- " + target.getName().getString() + "'s Heritage ---"), false);

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
}
