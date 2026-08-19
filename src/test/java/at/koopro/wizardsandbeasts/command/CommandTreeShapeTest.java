package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.command.debug.DebugModuleRegistry;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The shape of {@code /wandb}.
 *
 * <p>The tree spent a long while as nineteen flat top-level nodes, because every system added its own
 * and nothing ever said no. This pins the eight categories that replaced them: a new node has to join
 * one of them or change this list on purpose, and it can no longer drift back by accident.
 *
 * <p>Builder-only — {@link WandbCommands#buildRoot} assembles brigadier nodes and never touches a
 * server, so the whole tree can be walked headless.
 */
class CommandTreeShapeTest {

    private static CommandNode<CommandSourceStack> root;

    @BeforeAll
    static void buildTree() {
        DebugModuleRegistry.bootstrap();
        root = WandbCommands.buildRoot("wandb").build();
    }

    // ── the category list ──

    @Test
    void theTopLevelIsExactlyTheEightCategories() {
        assertEquals(
                new TreeSet<>(Set.of("player", "magic", "item", "world", "beast", "ministry", "admin", "debug")),
                new TreeSet<>(root.getChildren().stream().map(CommandNode::getName).toList()));
    }

    @Test
    void everyTopLevelNodeIsACategoryRatherThanAVerb() {
        // A category groups; a verb executes. If a top-level node can be run on its own it is a verb
        // that escaped into the category list — which is exactly how the flat tree grew.
        //
        // `debug` is the one allowed exception: running it bare prints the list of debug modules, so
        // its executor describes the category instead of doing work inside it.
        for (CommandNode<CommandSourceStack> category : root.getChildren()) {
            if (category.getName().equals("debug")) {
                continue;
            }
            assertNull(category.getCommand(),
                    "top-level '" + category.getName() + "' is directly executable, so it is a verb, not a category");
        }
    }

    // ── the moves that motivated the regrouping ──

    @Test
    void theRegroupedPathsResolve() {
        assertPath("player", "heritage", "info");
        assertPath("player", "profession", "points");   // was: heritage profession points
        assertPath("player", "owls", "set_profession"); // was: heritage owls setprofession
        assertPath("player", "appearance", "form", "set");
        assertPath("player", "appearance", "size", "profile");
        assertPath("player", "stats", "grant_growth");  // was: /wandb stats
        assertPath("player", "skill", "force_unlock");  // was: skill forceunlock
        assertPath("player", "vocation", "set", "primary"); // was: skill vocation …
        assertPath("player", "ability", "grant");
        assertPath("player", "sheet");                  // was: /wandb character

        assertPath("magic", "spell", "learn_all");      // was: wand spell learnall
        assertPath("magic", "proficiency", "set_all");  // was: wand proficiency setall
        assertPath("magic", "patronus", "form", "reveal");
        assertPath("magic", "animagus", "transform");
        assertPath("magic", "apparate", "mark");
        assertPath("magic", "pact", "break");

        assertPath("item", "wand", "give");
        assertPath("item", "wand", "config", "presets");
        assertPath("item", "broom", "set_durability");  // was: /broom setdurability
        assertPath("item", "trunk", "impound");         // was: world trunk impound

        assertPath("world", "azkaban", "info");         // was: azkaban dump_info
        assertPath("world", "floo", "register");
        assertPath("world", "ward", "add");             // was: world apparitionward add
        assertPath("world", "pocket", "enter");

        assertPath("beast", "bestiary", "force", "unlock_all");
        assertPath("beast", "creature", "summon");
        assertPath("beast", "niffler", "spawn_baby");   // was: bestiary niffler spawnbaby
        assertPath("beast", "niffler", "empty_pouch");  // was: bestiary niffler pouchempty

        assertPath("admin", "module", "setting");

        assertPath("debug", "pose", "cast");            // was: /wandb pose cast
        assertPath("debug", "morph");                   // was: debug morph debug
        assertPath("debug", "apparition");              // was: world apparitiontest
        assertPath("debug", "blank_test");              // was: wand blanktest
        assertPath("debug", "spell", "rejects", "summary");
    }

    // ── what was deleted stays deleted ──

    @Test
    void theRemovedNodesAreGone() {
        // A dead stub that only ever printed "not currently implemented".
        assertNull(root.getChild("vault"));
        // Duplicated `debug spell rejects`, and collided by name with the player attribute tree.
        assertNull(root.getChild("debug").getChild("stats"));
        // A spawn tool that had been parented inside the discovery-tier tree.
        assertNull(root.getChild("beast").getChild("bestiary").getChild("niffler"));
        // Every one of these is now reachable only through its category.
        for (String promoted : List.of("stats", "skill", "wand", "heritage", "character",
                "animagus", "apparate", "ability", "pose", "floo", "azkaban",
                "bestiary", "creature", "pact", "module")) {
            assertNull(root.getChild(promoted),
                    "'" + promoted + "' is back at the top level");
        }
    }

    private static void assertPath(String... path) {
        CommandNode<CommandSourceStack> node = root;
        StringBuilder walked = new StringBuilder("/wandb");
        for (String part : path) {
            walked.append(' ').append(part);
            node = node.getChild(part);
            assertNotNull(node, "no such command: " + walked);
        }
    }
}
