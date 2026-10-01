package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.command.debug.DebugModuleRegistry;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@code /wandb player vault}: reading your own vault is open; every verb that creates, destroys or reveals someone's
 * money carries the administrator gate. These verbs once had no gate at all, inside a branch that is deliberately
 * ungated, so any player could deposit unlimited Galleons. Builder-only, like {@link CommandTreeShapeTest}.
 */
class VaultCommandGateTest {

    private static CommandNode<CommandSourceStack> vault;

    @BeforeAll
    static void buildTree() {
        DebugModuleRegistry.bootstrap();
        CommandNode<CommandSourceStack> root = WandbCommands.buildRoot("wandb").build();
        CommandNode<CommandSourceStack> player = root.getChild("player");
        assertNotNull(player);
        vault = player.getChild("vault");
        assertNotNull(vault);
    }

    @Test
    void moneyVerbsRequireAnAdministrator() {
        for (String verb : new String[] {"deposit", "withdraw", "clear", "target"}) {
            CommandNode<CommandSourceStack> node = vault.getChild(verb);
            assertNotNull(node, "no vault " + verb);
            assertSame(WizardsAndBeastsCommandPermissions.ADMIN, node.getRequirement(),
                    "/wandb player vault " + verb + " is not administrator-only");
        }
    }

    @Test
    void readingYourOwnVaultStaysOpen() {
        assertNotNull(vault.getCommand(), "bare /wandb player vault should run");
        // The vault node itself carries no gate; the default requirement accepts everyone.
        assertNull(vault.getRedirect());
    }
}
