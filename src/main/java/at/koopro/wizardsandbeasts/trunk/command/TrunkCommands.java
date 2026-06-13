package at.koopro.wizardsandbeasts.trunk.command;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.trunk.TrunkRecord;
import at.koopro.wizardsandbeasts.trunk.TrunkRegistryData;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

public final class TrunkCommands {
    private TrunkCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("trunk")
                .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
                .then(Commands.literal("impound")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> setImpounded(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"),
                                        true))))
                .then(Commands.literal("release")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> setImpounded(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"),
                                        false))));
    }

    private static int setImpounded(CommandSourceStack source, ServerPlayer target, boolean impound) {
        TrunkRegistryData data = TrunkRegistryData.get(source.getLevel().getServer().overworld());
        UUID ownerId = target.getUUID();

        Optional<TrunkRecord> found = data.getAllPockets().stream()
                .filter(r -> r.owner().equals(ownerId))
                .findFirst();

        if (found.isEmpty()) {
            source.sendFailure(Component.literal(
                    target.getName().getString() + " has no registered pocket.").withStyle(ChatFormatting.RED));
            return 0;
        }

        TrunkRecord record = found.get();
        data.setImpounded(record.pocketId(), impound);

        String action = impound ? "impounded" : "released";
        ChatFormatting color = impound ? ChatFormatting.RED : ChatFormatting.GREEN;
        source.sendSuccess(() -> Component.literal("Trunk " + record.pocketId().toString().substring(0, 8)
                + " (owner: " + target.getName().getString() + ") " + action + ".")
                .withStyle(color), true);
        return 1;
    }
}
