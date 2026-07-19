package at.koopro.wizardsandbeasts.heritage.command;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.form.FormRegistry;
import at.koopro.wizardsandbeasts.form.FormSystemAPI;
import at.koopro.wizardsandbeasts.form.PlayerForm;
import at.koopro.wizardsandbeasts.heritage.HeritageFormBridge;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class WizFormCommands {

    private WizFormCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("form")
                .then(Commands.literal("set")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("form", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                FormRegistry.getAllFormIds(), builder))
                                        .executes(ctx -> setForm(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "form"))))))
                .then(Commands.literal("reset")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> resetForm(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("list")
                        .executes(ctx -> listForms(ctx.getSource(), null))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        java.util.Arrays.stream(Heritage.values()).map(Heritage::getId), builder))
                                .executes(ctx -> listForms(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "type")))));
    }

    private static int setForm(CommandSourceStack source, ServerPlayer target, String formId) {
        PlayerForm form = FormSystemAPI.setPlayerForm(target, formId);
        if (form == null) {
            source.sendFailure(Component.literal("Unknown form: " + formId).withStyle(ChatFormatting.RED));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Set " + target.getName().getString()
                + " form to " + form.displayName()).withStyle(ChatFormatting.GREEN), false);
        target.displayClientMessage(Component.literal(
                "Your form changed to " + form.displayName()).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int resetForm(CommandSourceStack source, ServerPlayer target) {
        FormSystemAPI.resetToDefault(target);
        source.sendSuccess(() -> Component.literal(
                "Reset " + target.getName().getString() + "'s form to default.").withStyle(ChatFormatting.YELLOW), false);
        target.displayClientMessage(Component.literal(
                "Your form has been reset.").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int listForms(CommandSourceStack source, String typeFilter) {
        source.sendSuccess(() -> Component.literal("--- Forms ---").withStyle(ChatFormatting.GOLD), false);

        if (typeFilter != null) {
            Heritage type = Heritage.byId(typeFilter);
            if (type == null) {
                source.sendFailure(Component.literal("Unknown type: " + typeFilter).withStyle(ChatFormatting.RED));
                return 0;
            }
            source.sendSuccess(() -> Component.literal(
                    "Forms for " + type.getDisplayName() + ":").withStyle(ChatFormatting.YELLOW), false);
            for (String formId : HeritageFormBridge.getAvailableFormIds(type)) {
                PlayerForm form = FormRegistry.get(formId);
                if (form != null) {
                    source.sendSuccess(() -> Component.literal(
                            "  " + form.formId() + " (" + form.modelType().getDisplayName()
                                    + ") - " + form.displayName()).withStyle(ChatFormatting.GRAY), false);
                }
            }
        } else {
            for (var entry : FormRegistry.getAll().entrySet()) {
                PlayerForm form = entry.getValue();
                source.sendSuccess(() -> Component.literal(
                        "  " + form.formId() + " (" + form.modelType().getDisplayName()
                                + ") - " + form.displayName()
                                + " [" + form.sizeProfileId() + "]").withStyle(ChatFormatting.GRAY), false);
            }
        }
        return 1;
    }
}
