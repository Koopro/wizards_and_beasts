package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.form.FormSystemAPI;
import at.koopro.wizardsandbeasts.form.PlayerForm;
import at.koopro.wizardsandbeasts.network.FormSyncS2CPacket;
import at.koopro.wizardsandbeasts.form.SizeProfile;
import at.koopro.wizardsandbeasts.form.SizeProfileRegistry;
import at.koopro.wizardsandbeasts.form.SizeSystemAPI;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commands for the size system: {@code /WizardsAndBeastsMod wizsize set|reset|profile}.
 */
public final class WizSizeCommands {

    private WizSizeCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("wizsize")
                .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("scaleX", FloatArgumentType.floatArg(0.1f, 5.0f))
                                        .then(Commands.argument("scaleY", FloatArgumentType.floatArg(0.1f, 5.0f))
                                                .then(Commands.argument("scaleZ", FloatArgumentType.floatArg(0.1f, 5.0f))
                                                        .executes(ctx -> setSize(
                                                                ctx.getSource(),
                                                                EntityArgument.getPlayer(ctx, "player"),
                                                                FloatArgumentType.getFloat(ctx, "scaleX"),
                                                                FloatArgumentType.getFloat(ctx, "scaleY"),
                                                                FloatArgumentType.getFloat(ctx, "scaleZ"))))))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> resetSize(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("profile")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> showProfile(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))
                                .then(Commands.argument("profileId", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                SizeProfileRegistry.getAll().keySet(), builder))
                                        .executes(ctx -> applyProfile(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "profileId"))))));
    }

    private static int setSize(CommandSourceStack source, ServerPlayer target,
                                float scaleX, float scaleY, float scaleZ) {
        SizeProfile override = new SizeProfile("cmd_override", scaleX, scaleY, scaleZ, 0.0f, 0.0f, 0.0f);
        SizeSystemAPI.applyProfile(target, override);
        FormSyncS2CPacket.syncToTracking(target);

        source.sendSuccess(() -> Component.literal(String.format(
                "\u00A7aSet %s size to %.2f / %.2f / %.2f",
                target.getName().getString(), scaleX, scaleY, scaleZ)), false);
        return 1;
    }

    private static int resetSize(CommandSourceStack source, ServerPlayer target) {
        // Re-apply from current form, or remove if no form
        PlayerForm form = FormSystemAPI.getPlayerForm(target);
        if (form != null) {
            SizeProfile profile = SizeProfileRegistry.getOrDefault(form.sizeProfileId());
            SizeSystemAPI.applyProfile(target, profile);
        } else {
            SizeSystemAPI.removeProfile(target);
        }
        FormSyncS2CPacket.syncToTracking(target);

        source.sendSuccess(() -> Component.literal(
                "\u00A7eReset " + target.getName().getString() + "'s size to default."), false);
        return 1;
    }

    private static int showProfile(CommandSourceStack source, ServerPlayer target) {
        PlayerForm form = FormSystemAPI.getPlayerForm(target);
        String formId = form != null ? form.formId() : "none";
        String profileId = form != null ? form.sizeProfileId() : "none";
        SizeProfile profile = form != null
                ? SizeProfileRegistry.getOrDefault(form.sizeProfileId())
                : SizeProfile.DEFAULT;

        source.sendSuccess(() -> Component.literal(
                "\u00A76--- " + target.getName().getString() + "'s Size ---"), false);
        source.sendSuccess(() -> Component.literal(
                "\u00A77Form: \u00A7f" + formId), false);
        source.sendSuccess(() -> Component.literal(
                "\u00A77Profile: \u00A7f" + profileId), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "\u00A77Scale: \u00A7f%.2f / %.2f / %.2f",
                profile.scaleX(), profile.scaleY(), profile.scaleZ())), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "\u00A77Reach: \u00A7f%+.1f  KB Res: \u00A7f%.2f  Step: \u00A7f%+.1f",
                profile.reachBonus(), profile.knockbackResistance(), profile.stepHeight())), false);
        return 1;
    }

    private static int applyProfile(CommandSourceStack source, ServerPlayer target, String profileId) {
        SizeProfile profile = SizeProfileRegistry.get(profileId);
        if (profile == null) {
            source.sendFailure(Component.literal("\u00A7cUnknown profile: " + profileId));
            return 0;
        }

        SizeSystemAPI.applyProfile(target, profile);
        FormSyncS2CPacket.syncToTracking(target);

        source.sendSuccess(() -> Component.literal(String.format(
                "\u00A7aApplied profile '%s' to %s (scale %.2f/%.2f/%.2f)",
                profileId, target.getName().getString(),
                profile.scaleX(), profile.scaleY(), profile.scaleZ())), false);
        return 1;
    }
}
