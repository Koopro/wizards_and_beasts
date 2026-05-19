package at.koopro.wizardsandbeasts.entity.niffler.command;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.Optional;

public final class NifflerCommands {

    private NifflerCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("niffler")
                .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
                .then(Commands.literal("bond")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> setBond(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "value"))))))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> spawnNiffler(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"),
                                        false))))
                .then(Commands.literal("spawnbaby")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> spawnNiffler(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"),
                                        true))))
                .then(Commands.literal("pouchempty")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> emptyPouch(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))));
    }

    private static int setBond(CommandSourceStack source, ServerPlayer target, int value) {
        Optional<NifflerEntity> niffler = findNearestBondedNiffler(target);
        if (niffler.isEmpty()) {
            source.sendFailure(Component.literal(target.getScoreboardName() + " has no bonded Niffler nearby.").withStyle(ChatFormatting.RED));
            return 0;
        }
        niffler.get().setForcedBond(value);
        source.sendSuccess(() -> Component.literal("Set " + target.getScoreboardName()
                + "'s Niffler bond to " + value + ".").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int spawnNiffler(CommandSourceStack source, ServerPlayer target, boolean baby) {
        if (!(target.level() instanceof ServerLevel sl)) return 0;
        NifflerEntity niffler = baby
                ? new at.koopro.wizardsandbeasts.entity.niffler.BabyNifflerEntity(ModEntities.BABY_NIFFLER.get(), sl)
                : new NifflerEntity(ModEntities.NIFFLER.get(), sl);
        niffler.setPos(target.getX(), target.getY(), target.getZ());
        sl.addFreshEntity(niffler);
        source.sendSuccess(() -> Component.literal("Spawned " + (baby ? "baby " : "") + "Niffler at "
                + target.getScoreboardName() + ".").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int emptyPouch(CommandSourceStack source, ServerPlayer target) {
        Optional<NifflerEntity> niffler = findNearestBondedNiffler(target);
        if (niffler.isEmpty()) {
            source.sendFailure(Component.literal(target.getScoreboardName() + " has no bonded Niffler nearby.").withStyle(ChatFormatting.RED));
            return 0;
        }
        niffler.get().getPouch().clearContent();
        source.sendSuccess(() -> Component.literal("Emptied " + target.getScoreboardName()
                + "'s Niffler pouch.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static Optional<NifflerEntity> findNearestBondedNiffler(ServerPlayer player) {
        AABB search = player.getBoundingBox().inflate(32);
        return player.level().getEntitiesOfClass(NifflerEntity.class, search,
                        n -> player.getUUID().equals(n.getOwnerUUID()))
                .stream()
                .min(Comparator.comparingDouble(player::distanceToSqr));
    }
}
