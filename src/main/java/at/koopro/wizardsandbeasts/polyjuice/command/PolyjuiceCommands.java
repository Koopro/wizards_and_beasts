package at.koopro.wizardsandbeasts.polyjuice.command;

import at.koopro.wizardsandbeasts.polyjuice.PolyjuiceSample;
import at.koopro.wizardsandbeasts.polyjuice.PolyjuiceService;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * Putting somebody's hair in a bottle, until the cauldron can do it.
 *
 * <h2>Why this exists as a command</h2>
 * <p>A Polyjuice sample is an identity attached to a specific bottle. The station accepts arbitrary
 * ingredients but has no notion of an item that carries one, so there is no hair item to add yet —
 * and building one is a separate slice with its own art, drop source and tag. This command writes
 * exactly the component that a hair item will write, so the whole path downstream is already the real
 * one and only the input changes.
 *
 * <p>Admin-gated because it is scaffolding, not a feature. Nothing here is meant to survive into a
 * world where hair exists.
 */
@NullMarked
public final class PolyjuiceCommands {

    private static final String KEY = "polyjuice.wizards_and_beasts.";

    private PolyjuiceCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("polyjuice")
                .then(Commands.literal("sample")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> sample(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "target")))))
                .then(Commands.literal("revert")
                        .then(Commands.argument("who", EntityArgument.player())
                                .executes(ctx -> revert(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "who")))));
    }

    /**
     * Write the held bottle's sample.
     *
     * <p>Refuses anything that is not a brew bottle rather than stamping the component onto whatever
     * happens to be in hand — the component would persist invisibly on an unrelated item and confuse
     * the next person to read it.
     */
    private static int sample(CommandSourceStack source, ServerPlayer target)
            throws CommandSyntaxException {
        ServerPlayer holder = source.getPlayerOrException();
        ItemStack held = holder.getMainHandItem();
        if (!held.is(ConsumableItemRegistry.BREW.get())) {
            source.sendFailure(Component.translatable(KEY + "command.not_a_bottle"));
            return 0;
        }
        PolyjuiceSample.write(held, target.getUUID(), target.getName().getString());
        source.sendSuccess(() -> Component.translatable(KEY + "command.sampled",
                target.getName().getString()), true);
        return 1;
    }

    private static int revert(CommandSourceStack source, ServerPlayer who) {
        if (!PolyjuiceService.isDisguised(who)) {
            source.sendFailure(Component.translatable(KEY + "command.not_disguised"));
            return 0;
        }
        PolyjuiceService.revert(who, true);
        source.sendSuccess(() -> Component.translatable(KEY + "command.reverted",
                who.getDisplayName()), true);
        return 1;
    }
}
