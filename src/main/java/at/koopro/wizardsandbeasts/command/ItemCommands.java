package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.broom.command.BroomCommands;
import at.koopro.wizardsandbeasts.trunk.command.TrunkCommands;
import at.koopro.wizardsandbeasts.wand.command.WandCommands;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /wandb item …} — spawning and inspecting the mod's three stateful items.
 *
 * <p>All of these hand out gear or rewrite what a player is carrying, so the admin gate sits once
 * on the group rather than on each child.
 */
@NullMarked
public final class ItemCommands {

    private ItemCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("item")
                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                .then(WandCommands.register())
                .then(BroomCommands.register())
                .then(TrunkCommands.register());
    }
}
