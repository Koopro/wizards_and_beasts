package at.koopro.wizardsandbeasts.floo;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every door into the Floo Network checks whether the Network is switched on.
 *
 * <h2>Why this is a source scan and not a behaviour test</h2>
 * <p>{@code ModuleManager.isEnabled} reads datapack-driven module state off a running server, so
 * exercising the real gate needs a world. The regression worth catching is not "does the gate work"
 * — it is a single boolean and it works — but <b>does every entry point still have one</b>. Doors get
 * added: the flames block, the registration form and the handbook path were all added after the
 * original three, and each was a new way into the system that could have shipped ungated.
 *
 * <p>An ungated door does not crash and does not look wrong. It silently lets a player use a feature
 * the server has turned off, which is the kind of bug that is only ever found by somebody complaining
 * that a disabled module still works.
 *
 * <p>Deliberately crude: it asserts the constant is mentioned, not that it is checked correctly. A
 * test that tried to prove correctness here would be a parser. What it buys is that a new file in
 * this list cannot be forgotten entirely, and the list itself is the documentation of what counts as
 * a door.
 */
class FlooModuleGateTest {

    private static final Path SRC = Path.of("src/main/java/at/koopro/wizardsandbeasts");

    /**
     * Every class a player can reach the Floo Network through.
     *
     * <p>Adding a door means adding it here. That is the point of the list.
     */
    private static final List<String> DOORS = List.of(
            "item/floo/FlooPowderItem.java",
            "block/floo/FlooFireplaceBlock.java",
            "block/floo/FlooFlamesBlock.java",
            "floo/FlooTravelHandler.java",
            "floo/FlooRegistrationService.java",
            "floo/call/FlooCallService.java",
            "floo/command/FlooCommands.java",
            "item/MinistryHandbookItem.java");

    @Test
    void everyEntryPointGatesOnTheFlooModule() throws IOException {
        for (String door : DOORS) {
            String body = Files.readString(SRC.resolve(door), StandardCharsets.UTF_8);
            assertTrue(body.contains("Module.FLOO_NETWORK"),
                    door + " is a way into the Floo Network but never mentions Module.FLOO_NETWORK — "
                            + "a server that has switched the module off would find this door still open");
        }
    }

    @Test
    void everyListedDoorActuallyExists() throws IOException {
        // Guards the list against rot in the other direction: a renamed or deleted file would make
        // the check above silently vacuous for that door.
        for (String door : DOORS) {
            assertTrue(Files.exists(SRC.resolve(door)),
                    door + " is listed as a Floo entry point but does not exist — the gate check for "
                            + "it is passing on a file that is not there");
        }
    }

    /**
     * The refusal has to say something.
     *
     * <p>A gate that returns silently is indistinguishable from a bug: the player clicks, nothing
     * happens, and there is nothing on screen to say the whole feature is switched off on this world.
     * Every door that gates therefore also references the message key for it.
     */
    @Test
    void theModuleRefusalIsSpoken() throws IOException {
        List<String> speakingDoors = List.of(
                "item/floo/FlooPowderItem.java",
                "block/floo/FlooFireplaceBlock.java",
                "floo/FlooTravelHandler.java",
                "floo/FlooRegistrationService.java",
                "floo/command/FlooCommands.java");
        for (String door : speakingDoors) {
            String body = Files.readString(SRC.resolve(door), StandardCharsets.UTF_8);
            assertTrue(body.contains("fail.module_off") || body.contains("moduleDisabledMsg")
                            || body.contains("Outcome.NOT_A_HEARTH"),
                    door + " gates on the module but has no way to say so");
        }
    }
}
