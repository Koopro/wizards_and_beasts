package at.koopro.wizardsandbeasts.apparition.command;

import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.apparition.ApparitionWard;
import at.koopro.wizardsandbeasts.apparition.ApparitionWardRegistry;
import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.network.apparition.ApparitionWardsSyncS2CPayload;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

public final class ApparitionCommands {
    private ApparitionCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> registerWard() {
        return Commands.literal("apparitionward")
                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                .then(Commands.literal("add")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                .executes(ctx -> addWard(
                                                        ctx,
                                                        StringArgumentType.getString(ctx, "id"),
                                                        null))
                                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                                        .executes(ctx -> addWard(
                                                                ctx,
                                                                StringArgumentType.getString(ctx, "id"),
                                                                StringArgumentType.getString(ctx, "message"))))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx -> removeWard(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("list")
                        .executes(ctx -> listWards(ctx.getSource())));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> registerTest() {
        return Commands.literal("apparitiontest")
                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> passTest(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))));
    }

    private static int addWard(CommandContext<CommandSourceStack> context, String idValue, String customMessage) {
        CommandSourceStack source = context.getSource();
        Identifier id = Identifier.tryParse(idValue);
        if (id == null) {
            source.sendFailure(Component.literal("Invalid ward id: " + idValue).withStyle(ChatFormatting.RED));
            return 0;
        }
        net.minecraft.core.BlockPos from;
        net.minecraft.core.BlockPos to;
        try {
            from = BlockPosArgument.getLoadedBlockPos(context, "from");
            to = BlockPosArgument.getLoadedBlockPos(context, "to");
        } catch (CommandSyntaxException ex) {
            source.sendFailure(Component.literal("Invalid ward bounds: " + ex.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
        AABB box = new AABB(from).minmax(new AABB(to)).inflate(1.0);
        String message = customMessage == null || customMessage.isBlank()
                ? "Something prevents you from Apparating here."
                : customMessage;
        ApparitionWardRegistry.register(new ApparitionWard(
                id,
                source.getLevel().dimension().identifier(),
                box,
                Component.literal(message),
                false));
        ApparitionWardRegistry.save(source.getServer());
        ApparitionWardsSyncS2CPayload.syncToAllPlayers(source.getServer());
        source.sendSuccess(() -> Component.literal("Added Apparition ward " + id).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removeWard(CommandSourceStack source, String idValue) {
        Identifier id = Identifier.tryParse(idValue);
        if (id == null) {
            source.sendFailure(Component.literal("Invalid ward id: " + idValue).withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!ApparitionWardRegistry.remove(id)) {
            source.sendFailure(Component.literal("No ward found: " + id).withStyle(ChatFormatting.RED));
            return 0;
        }
        ApparitionWardRegistry.save(source.getServer());
        ApparitionWardsSyncS2CPayload.syncToAllPlayers(source.getServer());
        source.sendSuccess(() -> Component.literal("Removed Apparition ward " + id).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int listWards(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("--- Apparition Wards ---").withStyle(ChatFormatting.GOLD), false);
        for (ApparitionWard ward : ApparitionWardRegistry.all()) {
            final Component line = Component.literal("- " + ward.wardId() + " @ " + ward.dimensionId()
                    + " [" + ward.bounds().minX + "," + ward.bounds().minY + "," + ward.bounds().minZ
                    + " -> " + ward.bounds().maxX + "," + ward.bounds().maxY + "," + ward.bounds().maxZ + "]")
                    .withStyle(ChatFormatting.GRAY);
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int passTest(CommandSourceStack source, ServerPlayer player) {
        PlayerAbilityHelper.setApparitionUnlocked(player, true);
        PlayerAbilityHelper.setApparitionLicensed(player, true);
        player.displayClientMessage(Component.literal(
                "Congratulations — you have passed your Apparition test. You are now licensed to Apparate.").withStyle(ChatFormatting.GREEN), false);
        source.sendSuccess(() -> Component.literal("Licensed " + player.getName().getString() + " for Apparition.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
