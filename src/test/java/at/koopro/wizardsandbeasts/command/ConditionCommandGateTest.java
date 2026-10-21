package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.command.debug.DebugModuleRegistry;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@code /wandb player condition}: looking at your own conditions is open; writing them, clearing them and reading
 * someone else's are administrator-only. {@code set} and {@code clear} used to have no gate at all, so any player
 * could wipe their own corruption or petrify and corrupt any other player online. Builder-only, like
 * {@link VaultCommandGateTest}.
 */
class ConditionCommandGateTest {

    private static CommandNode<CommandSourceStack> condition;

    @BeforeAll
    static void buildTree() {
        DebugModuleRegistry.bootstrap();
        CommandNode<CommandSourceStack> root = WandbCommands.buildRoot("wandb").build();
        CommandNode<CommandSourceStack> player = root.getChild("player");
        assertNotNull(player);
        condition = player.getChild("condition");
        assertNotNull(condition);
    }

    @Test
    void writingVerbsRequireAnAdministrator() {
        for (String verb : new String[] {"set", "clear", "target"}) {
            CommandNode<CommandSourceStack> node = condition.getChild(verb);
            assertNotNull(node, "no condition " + verb);
            assertSame(WizardsAndBeastsCommandPermissions.ADMIN, node.getRequirement(),
                    "/wandb player condition " + verb + " is not administrator-only");
        }
    }

    @Test
    void readingYourOwnConditionsStaysOpen() {
        assertNotNull(condition.getCommand(), "bare /wandb player condition should run");
    }
}
