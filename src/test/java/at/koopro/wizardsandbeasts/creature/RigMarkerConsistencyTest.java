package at.koopro.wizardsandbeasts.creature;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code PLACEHOLDER box rig} marker, checked against the rig it is talking about.
 *
 * <p>{@code CURRENT_STATE.md} carried the claim that 93 of these markers were <b>stale</b> — left on
 * creatures whose rigs had since been rebuilt, "misrepresenting shipped work and corrupting audits".
 * It was wrong, and it contradicted the entry directly above it in the same list, which said 102 of
 * 111 rigs were still box-per-bone. Both cannot be true. This test is why the question does not have
 * to be re-litigated by hand: it decides it from the files, in both directions.
 *
 * <h2>What a generated box rig looks like</h2>
 * {@code tools/creature_gen.py} emits a rig with a cube-less {@code root} bone and exactly one cube
 * on every other bone. A hand-built rig has neither property — it has bones carrying two or three
 * cubes, and its bone names are the creature's own. The two populations do not overlap anywhere near
 * the boundary: generated rigs top out at nine cubes and the hand-built ones start at seventeen.
 *
 * <p>Pure file I/O, no Minecraft bootstrap, so it runs in milliseconds and names the file that is
 * wrong.
 */
class RigMarkerConsistencyTest {

    private static final Gson GSON = new Gson();

    private static final String MARKER = "PLACEHOLDER box rig";

    /**
     * The generator emits six to nine cubes; the hand-built rigs start at seventeen. Nothing on disk
     * sits between the two, which is the same threshold {@code AlphaRoster}'s javadoc names.
     */
    private static final int GENERATED_CUBE_CEILING = 9;

    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "wizards_and_beasts");
    private static final Path DATA = Path.of("src", "main", "resources", "data", "wizards_and_beasts");
    private static final Path RIGS = ASSETS.resolve(Path.of("geckolib", "models", "entity"));
    private static final Path ANIMS = ASSETS.resolve(Path.of("geckolib", "animations", "entity"));
    private static final Path CREATURES = DATA.resolve("creatures");

    /**
     * Rigs that are not creatures. Brooms, the shield and the player rig are hand-authored props with
     * no creature definition and no marker to be consistent with.
     *
     * <p>{@code dementor} is here for a different reason: it is a single cube because it is drawn
     * almost entirely by {@code DementorRenderer}'s cloak layer rather than by the rig, so cube count
     * says nothing useful about it. {@code kelpie_disguise} is the Kelpie's tame-horse guise, a second
     * rig for a creature that has its own entry.
     */
    private static final Set<String> NOT_A_CREATURE = Set.of(
            "player", "protego_shield", "broom", "broom_cleansweep_seven", "broom_comet_260",
            "broom_firebolt", "broom_firebolt_supreme", "broom_nimbus_2000", "broom_nimbus_2001",
            "broom_oakshaft_79", "kelpie_disguise", "dementor");

    // ── the two directions ───────────────────────────────────────────────────

    /**
     * No marker sits on a rig somebody has since rebuilt.
     *
     * <p>This is the direction {@code CURRENT_STATE.md} claimed was broken 93 times. If it ever does
     * break, the fix is to delete the marker from the two files named in the failure — not to
     * rewrite the claim in a document.
     */
    @Test
    void noMarkerSitsOnAHandBuiltRig() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String id : markedIds()) {
            if (!Files.exists(rig(id))) {
                continue; // marked animation for a rig that does not ship; a different test's problem
            }
            if (!isGeneratedBoxRig(id)) {
                problems.add(id + ": rig at " + rig(id) + " is hand-built, but its marker still says "
                        + "PLACEHOLDER — delete the marker from the creature JSON and the animation");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * Every generated box rig admits to being one.
     *
     * <p>The direction nobody was watching, and the one that actually costs something: an unmarked
     * box rig reads as finished art in every audit that greps for the marker, which is how a
     * creature ends up on a roster it has not earned. Only creatures that ship a
     * {@code CreatureDefinition} are checked, because that file is where the marker lives.
     */
    @Test
    void everyGeneratedBoxRigCarriesTheMarker() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String id : riggedCreatureIds()) {
            Path definition = CREATURES.resolve(id + ".json");
            if (!Files.exists(definition) || !isGeneratedBoxRig(id)) {
                continue;
            }
            if (!Files.readString(definition).contains(MARKER)) {
                problems.add(id + ": rig is a generated box rig but " + definition
                        + " does not say so, so every audit reads it as finished art");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * A creature's definition and its animation file agree about the rig.
     *
     * <p>They are written by the same generator pass, so a disagreement means one of them was edited
     * by hand and the other forgotten — and whichever one an audit happens to grep decides the answer.
     */
    @Test
    void theDefinitionAndTheAnimationAgree() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String id : riggedCreatureIds()) {
            Path definition = CREATURES.resolve(id + ".json");
            Path animation = anim(id);
            if (!Files.exists(definition) || !Files.exists(animation)) {
                continue;
            }
            boolean markedDefinition = Files.readString(definition).contains(MARKER);
            boolean markedAnimation = Files.readString(animation).contains(MARKER);
            if (markedDefinition != markedAnimation) {
                problems.add(id + ": " + (markedDefinition ? "definition" : "animation")
                        + " carries the placeholder marker and the other does not");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * The marker is still load-bearing.
     *
     * <p>If this ever fails because the count reached zero, the art backlog is finished and the
     * marker, this test and the {@code KNOWN_ISSUES.md} section that lists them should all go.
     */
    @Test
    void theArtBacklogIsStillTheSizeTheDocsSayItIs() throws IOException {
        Set<String> marked = markedIds();
        assertFalse(marked.isEmpty(), "no rig carries the marker any more; retire it and this test");
        assertTrue(marked.size() >= 80,
                "the documented backlog is 83 creatures; found " + marked.size()
                        + ". If rigs were finished, update KNOWN_ISSUES.md 4.2 in the same change.");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Path rig(String id) {
        return RIGS.resolve(id + ".geo.json");
    }

    private static Path anim(String id) {
        return ANIMS.resolve(id + ".animation.json");
    }

    /**
     * True when this rig has the shape {@code tools/creature_gen.py} emits.
     *
     * <p>Two signals, and both are needed. The cube-less {@code root} bone is the generator's
     * signature — the five hand-authored small creatures ({@code augurey}, {@code bowtruckle},
     * {@code cornish_pixie}, {@code niffler}, {@code streeler}) do not have one, and are otherwise
     * indistinguishable by size. The nine-cube ceiling is the other half: {@code ghoul},
     * {@code hippogriff} and {@code werewolf} were rebuilt on top of the generated skeleton, so they
     * kept the {@code root} bone while growing to 17–27 cubes, and a root-only test calls them
     * generated. Together the two agree with every marker on disk and disagree with none.
     */
    private static boolean isGeneratedBoxRig(String id) throws IOException {
        JsonObject geometry = GSON.fromJson(Files.readString(rig(id)), JsonObject.class)
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonArray bones = geometry.getAsJsonArray("bones");
        boolean hasEmptyRoot = false;
        int totalCubes = 0;
        for (var element : bones) {
            JsonObject bone = element.getAsJsonObject();
            int cubes = bone.has("cubes") ? bone.getAsJsonArray("cubes").size() : 0;
            totalCubes += cubes;
            if ("root".equals(bone.get("name").getAsString()) && cubes == 0) {
                hasEmptyRoot = true;
            }
        }
        return hasEmptyRoot && totalCubes <= GENERATED_CUBE_CEILING;
    }

    /** Every creature id carrying the marker, in either of the two files that can carry it. */
    private static Set<String> markedIds() throws IOException {
        Set<String> marked = new TreeSet<>();
        collectMarked(CREATURES, ".json", marked);
        collectMarked(ANIMS, ".animation.json", marked);
        return marked;
    }

    private static void collectMarked(Path directory, String suffix, Set<String> into) throws IOException {
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.toString().endsWith(suffix)).toList()) {
                if (Files.readString(file).contains(MARKER)) {
                    into.add(file.getFileName().toString().replace(suffix, ""));
                }
            }
        }
    }

    /** Every id that ships a rig and is a creature rather than a prop. */
    private static List<String> riggedCreatureIds() throws IOException {
        List<String> ids = new ArrayList<>();
        try (var files = Files.list(RIGS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".geo.json")).sorted().toList()) {
                String id = file.getFileName().toString().replace(".geo.json", "");
                if (!NOT_A_CREATURE.contains(id)) {
                    ids.add(id);
                }
            }
        }
        assertFalse(ids.isEmpty(), "expected creature rigs on disk");
        return ids;
    }
}
