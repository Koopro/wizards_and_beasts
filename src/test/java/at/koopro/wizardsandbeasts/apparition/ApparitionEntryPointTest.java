package at.koopro.wizardsandbeasts.apparition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Apparition has one door, and every refusal it can give has something to say.
 *
 * <h2>Why part of this is a source scan</h2>
 *
 * <p>Modelled on {@code FlooModuleGateTest}, and for the same reason: the gate reads datapack-driven module
 * state off a running server, so exercising it needs a world. The regression worth catching is not whether
 * the gate works — it is whether every way in still goes through it. The keybind, the destination selector
 * and a spell entry are three different code paths into one mechanic, and a fourth is exactly the kind of
 * thing that ships calling {@code ApparitionChargeManager.begin} directly and quietly skipping the licence
 * check.
 */
class ApparitionEntryPointTest {

    private static final Path SRC = Path.of("src/main/java/at/koopro/wizardsandbeasts");
    private static final Path EN_US =
            Path.of("src/main/resources/assets/wizards_and_beasts/lang/en_us.json");

    /**
     * Files allowed to start an attempt.
     *
     * <p>{@code ApparitionService} is the door. {@code ApparitionChargeManager} is what the door opens, and
     * it owns the gate, so it is the one other place the call is legitimate. Anything else calling
     * {@code begin} has gone around the front.
     */
    private static final List<String> MAY_BEGIN = List.of(
            "apparition/ApparitionService.java",
            "apparition/charge/ApparitionChargeManager.java");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void nothingButTheServiceStartsACharge() throws IOException {
        try (var files = Files.walk(SRC)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String relative = SRC.relativize(file).toString().replace('\\', '/');
                if (MAY_BEGIN.contains(relative)) {
                    continue;
                }
                assertTrue(!read(file).contains("ChargeManager.begin("),
                        relative + " begins an Apparition charge directly. Every entry point goes through "
                                + "ApparitionService so the licence, ward, cooldown and module gates cannot "
                                + "fork between them.");
            }
        }
    }

    @Test
    void theModuleIsCheckedWhereAnAttemptBegins() throws IOException {
        String gate = read(SRC.resolve("apparition/ApparitionServerLogic.java"));
        assertTrue(gate.contains("Module.APPARITION"),
                "the Apparition module is not checked at the start of an attempt — mirroring FLOO_NETWORK "
                        + "and BROOM_FLIGHT is the whole reason it exists as its own module");
    }

    /** A refusal the player is shown must have something to show them. */
    @Test
    void everyRefusalMessageResolvesToALangKey() throws IOException {
        JsonObject lang = JsonParser.parseString(read(EN_US)).getAsJsonObject();
        for (ApparitionStartResult result : ApparitionStartResult.values()) {
            String key = result.messageKey();
            if (key == null) {
                continue;
            }
            assertTrue(lang.has(key),
                    result + " names lang key \"" + key + "\", which is not in en_us.json — the player "
                            + "would be shown the raw key as their refusal");
        }
    }

    /**
     * The silent refusals are silent on purpose, and each for its own reason: an absent feature does not
     * scold, and a key press that lands on a charge already running should do nothing at all.
     */
    @Test
    void onlyTheDeliberatelySilentRefusalsHaveNoMessage() {
        List<ApparitionStartResult> silent = List.of(
                ApparitionStartResult.STARTED,
                ApparitionStartResult.REJECTED_MODULE_OFF,
                ApparitionStartResult.REJECTED_ALREADY_CHARGING);
        for (ApparitionStartResult result : ApparitionStartResult.values()) {
            assertEquals(silent.contains(result), result.messageKey() == null,
                    result + " disagrees with the list of refusals that are meant to say nothing");
        }
    }
}
