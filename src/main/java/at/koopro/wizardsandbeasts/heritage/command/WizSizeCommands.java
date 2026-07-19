package at.koopro.wizardsandbeasts.heritage.command;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.form.FormSystemAPI;
import at.koopro.wizardsandbeasts.form.PlayerForm;
import at.koopro.wizardsandbeasts.network.form.FormSyncS2CPayload;
import at.koopro.wizardsandbeasts.form.SizeProfile;
import at.koopro.wizardsandbeasts.form.SizeProfileRegistry;
import at.koopro.wizardsandbeasts.form.SizeSystemAPI;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class WizSizeCommands {

    private WizSizeCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("size")
                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("hitboxHeight", FloatArgumentType.floatArg(0.1f, 9.0f))
                                        .then(Commands.argument("modelScale", FloatArgumentType.floatArg(0.1f, 5.0f))
                                                .executes(ctx -> setSize(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        FloatArgumentType.getFloat(ctx, "hitboxHeight"),
                                                        FloatArgumentType.getFloat(ctx, "modelScale")))))))
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
                                float hitboxHeight, float modelScale) {
        float hitboxWidth = hitboxHeight / 3.0f;
        SizeProfile override = new SizeProfile("cmd_override",
                hitboxWidth, hitboxHeight, modelScale, 1.0f, 1.0f,
                0.0f, 0.0f, 0.0f);
        SizeSystemAPI.applyProfile(target, override);
        FormSyncS2CPayload.syncToTracking(target);
        source.sendSuccess(() -> Component.literal(String.format(
                "Set %s hitbox=%.2fh model=%.2f",
                target.getName().getString(), hitboxHeight, modelScale)).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int resetSize(CommandSourceStack source, ServerPlayer target) {
        PlayerForm form = FormSystemAPI.getPlayerForm(target);
        if (form != null) {
            SizeProfile profile = SizeProfileRegistry.getOrDefault(form.sizeProfileId());
            SizeSystemAPI.applyProfile(target, profile);
        } else {
            SizeSystemAPI.removeProfile(target);
        }
        FormSyncS2CPayload.syncToTracking(target);
        source.sendSuccess(() -> Component.literal(
                "Reset " + target.getName().getString() + "'s size to default.").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int showProfile(CommandSourceStack source, ServerPlayer target) {
        PlayerForm form = FormSystemAPI.getPlayerForm(target);
        String formId    = form != null ? form.formId()        : "none";
        String profileId = form != null ? form.sizeProfileId() : "none";
        SizeProfile profile = form != null
                ? SizeProfileRegistry.getOrDefault(form.sizeProfileId())
                : SizeProfile.DEFAULT;

        source.sendSuccess(() -> Component.literal(
                "--- " + target.getName().getString() + "'s Size ---").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("Form: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(formId).withStyle(ChatFormatting.WHITE)), false);
        source.sendSuccess(() -> Component.literal("Profile: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(profileId).withStyle(ChatFormatting.WHITE)), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "Hitbox: %.2fw x %.2fh",
                profile.hitboxWidth(), profile.hitboxHeight())).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "Model: scale=%.2f  ax=%.3f  az=%.3f",
                profile.modelScale(), profile.modelAspectX(), profile.modelAspectZ())).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "Reach: %+.1f  KB Res: %.2f  Step: %+.1f",
                profile.reachBonus(), profile.knockbackResistance(), profile.stepHeight())).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int applyProfile(CommandSourceStack source, ServerPlayer target, String profileId) {
        SizeProfile profile = SizeProfileRegistry.get(profileId);
        if (profile == null) {
            source.sendFailure(Component.literal("Unknown profile: " + profileId).withStyle(ChatFormatting.RED));
            return 0;
        }
        SizeSystemAPI.applyProfile(target, profile);
        FormSyncS2CPayload.syncToTracking(target);
        source.sendSuccess(() -> Component.literal(String.format(
                "Applied profile '%s' to %s (hitbox %.2fh, model %.2f)",
                profileId, target.getName().getString(),
                profile.hitboxHeight(), profile.modelScale())).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
