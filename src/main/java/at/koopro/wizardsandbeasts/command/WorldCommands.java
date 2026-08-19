package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.apparition.command.ApparitionCommands;
import at.koopro.wizardsandbeasts.azkaban.command.AzkabanCommands;
import at.koopro.wizardsandbeasts.floo.command.FlooCommands;
import at.koopro.wizardsandbeasts.trunk.command.PocketDebugCommands;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /wandb world …} — places, and the network between them.
 *
 * <p>This node was previously a leftovers bin: it held the Apparition wards, an Apparition-test
 * shortcut, the trunk impound tools and the pocket-dimension debug tree, which between them span
 * three unrelated systems. What is left is what the name actually promises — Azkaban, the Floo
 * network, Apparition wards and pocket dimensions are all *locations or routes between locations*.
 * The trunk moved to {@link ItemCommands}; the Apparition test to {@code debug}.
 */
@NullMarked
public final class WorldCommands {

    private WorldCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("world")
                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                .then(AzkabanCommands.register())
                .then(FlooCommands.register())
                .then(ApparitionCommands.registerWard())
                .then(PocketDebugCommands.register());
    }
}
