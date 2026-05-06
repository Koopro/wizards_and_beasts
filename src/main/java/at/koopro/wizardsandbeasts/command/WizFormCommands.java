package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.form.FormRegistry;
import at.koopro.wizardsandbeasts.form.FormSystemAPI;
import at.koopro.wizardsandbeasts.form.PlayerForm;
import at.koopro.wizardsandbeasts.type.HeritageFormBridge;
import at.koopro.wizardsandbeasts.type.Heritage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commands for the form system: {@code /WizardsAndBeastsMod form set|reset|list}.
 */
public final class WizFormCommands {

    private WizFormCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("form")
                .then(Commands.literal("set")
                        .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("form", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                FormRegistry.getAllFormIds(), builder))
                                        .executes(ctx -> setForm(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "form"))))))
                .then(Commands.literal("reset")
                        .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
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
            source.sendFailure(Component.literal("\u00A7cUnknown form: " + formId));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("\u00A7aSet " + target.getName().getString()
                + " form to " + form.displayName()), false);
        target.displayClientMessage(Component.literal(
                "\u00A7eYour form changed to " + form.displayName()), false);
        return 1;
    }

    private static int resetForm(CommandSourceStack source, ServerPlayer target) {
        FormSystemAPI.resetToDefault(target);

        source.sendSuccess(() -> Component.literal(
                "\u00A7eReset " + target.getName().getString() + "'s form to default."), false);
        target.displayClientMessage(Component.literal(
                "\u00A7eYour form has been reset."), false);
        return 1;
    }

    private static int listForms(CommandSourceStack source, String typeFilter) {
        source.sendSuccess(() -> Component.literal("\u00A76--- Forms ---"), false);

        if (typeFilter != null) {
            Heritage type = Heritage.byId(typeFilter);
            if (type == null) {
                source.sendFailure(Component.literal("\u00A7cUnknown type: " + typeFilter));
                return 0;
            }
            source.sendSuccess(() -> Component.literal(
                    "\u00A7eForms for " + type.getDisplayName() + ":"), false);
            for (String formId : HeritageFormBridge.getAvailableFormIds(type)) {
                PlayerForm form = FormRegistry.get(formId);
                if (form != null) {
                    source.sendSuccess(() -> Component.literal(
                            "  \u00A7f" + form.formId() + " \u00A78(" + form.modelType().getDisplayName()
                                    + ") \u00A77- " + form.displayName()), false);
                }
            }
        } else {
            for (var entry : FormRegistry.getAll().entrySet()) {
                PlayerForm form = entry.getValue();
                source.sendSuccess(() -> Component.literal(
                        "  \u00A7f" + form.formId() + " \u00A78(" + form.modelType().getDisplayName()
                                + ") \u00A77- " + form.displayName()
                                + " \u00A78[" + form.sizeProfileId() + "]"), false);
            }
        }
        return 1;
    }
}
