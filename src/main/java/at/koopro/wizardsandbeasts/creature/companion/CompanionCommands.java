package at.koopro.wizardsandbeasts.creature.companion;

import at.koopro.wizardsandbeasts.registry.ModAttachments;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;

/**
 * {@code /wandb player companion …} — whom your creatures trust and whom they treat as enemies.
 *
 * <p>Kept on the owner ({@link CompanionRoster}) and copied onto each companion when it next sees its owner, so
 * one command covers every creature, and a creature left at home still knows the lists. Player-facing: it only
 * ever changes the caller's own companions.
 */
@NullMarked
public final class CompanionCommands {

    private CompanionCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("companion")
                .then(Commands.literal("trust").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> relation(ctx, true, false))))
                .then(Commands.literal("untrust").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> relation(ctx, false, false))))
                .then(Commands.literal("hostile").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> relation(ctx, false, true))))
                .then(Commands.literal("unhostile").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> relation(ctx, false, false))))
                .then(Commands.literal("list").executes(CompanionCommands::list));
    }

    private static int relation(CommandContext<CommandSourceStack> ctx, boolean trust, boolean hostile)
            throws CommandSyntaxException {
        ServerPlayer owner = ctx.getSource().getPlayerOrException();
        ServerPlayer other = EntityArgument.getPlayer(ctx, "player");
        if (other == owner) {
            ctx.getSource().sendFailure(Component.translatable("companion.wizards_and_beasts.cmd.self"));
            return 0;
        }
        setRelation(owner, other.getUUID(), other.getGameProfile().name(), trust, hostile);
        String key = trust ? "trusted" : hostile ? "hostile" : "neutral";
        ctx.getSource().sendSuccess(() -> Component.translatable("companion.wizards_and_beasts.cmd." + key,
                other.getDisplayName()), false);
        return 1;
    }

    /** Sets how the owner's creatures regard {@code other}; loaded companions pick it up on their next tick. */
    public static void setRelation(ServerPlayer owner, UUID other, String name, boolean trust, boolean hostile) {
        owner.setData(ModAttachments.COMPANION_ROSTER.get(),
                owner.getData(ModAttachments.COMPANION_ROSTER.get()).withRelation(other, name, trust, hostile));
        Companions.markDirty(owner);
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer owner = ctx.getSource().getPlayerOrException();
        CompanionRoster roster = owner.getData(ModAttachments.COMPANION_ROSTER.get());
        ctx.getSource().sendSuccess(() -> Component.translatable("companion.wizards_and_beasts.cmd.list",
                roster.entries().size(), roster.trusted().size(), roster.hostile().size()), false);
        roster.entries().forEach((id, entry) -> ctx.getSource().sendSuccess(() -> Component.literal(" - ")
                .append(entry.name().isEmpty() ? Companions.speciesName(entry.species()) : Component.literal(entry.name()))
                .append(" · ").append(Component.translatable(entry.stage().key()))
                .append(" · " + entry.bond() + "/" + entry.maxBond()), false));
        return roster.entries().size();
    }
}
