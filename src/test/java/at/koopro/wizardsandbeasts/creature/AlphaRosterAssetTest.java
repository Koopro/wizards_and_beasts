package at.koopro.wizardsandbeasts.creature;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The claim {@link AlphaRoster} makes, checked against the files on disk.
 *
 * <p>"Alpha-ready" is an assertion about four separate artefacts agreeing — a rig, an animation, a
 * skin and a loot table — and none of them are compiled, so nothing else in the build would notice
 * any of them going missing or drifting apart. The failure mode this exists to stop is the
 * expensive one: GeckoLib does not degrade when a model asks for a clip its file does not define or
 * a bone the rig does not have, it throws inside the render pass, which crashes the client the
 * moment the creature comes into view.
 *
 * <p>Pure file I/O deliberately — no Minecraft bootstrap, no registry — so it runs in milliseconds
 * and fails with the path that is wrong.
 */
class AlphaRosterAssetTest {

    private static final Gson GSON = new Gson();

    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "wizards_and_beasts");
    private static final Path DATA = Path.of("src", "main", "resources", "data", "wizards_and_beasts");

    private static Path geo(String id) {
        return ASSETS.resolve(Path.of("geckolib", "models", "entity", id + ".geo.json"));
    }

    private static Path anim(String id) {
        return ASSETS.resolve(Path.of("geckolib", "animations", "entity", id + ".animation.json"));
    }

    private static Path skin(String id) {
        return ASSETS.resolve(Path.of("textures", "entity", id + ".png"));
    }

    private static JsonObject json(Path path) throws IOException {
        return GSON.fromJson(Files.readString(path), JsonObject.class);
    }

    /** The one geometry block, unwrapped. */
    private static JsonObject geometry(String id) throws IOException {
        return json(geo(id)).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
    }

    // -- the roster is real ----------------------------------------------------------------------

    @Test
    void rosterIsTheSizeItClaims() {
        assertEquals(21, AlphaRoster.SHIPPED.size(),
                "the alpha slice is 21 creatures; update KNOWN_ISSUES.md alongside this");
    }

    @Test
    void everyAlphaCreatureShipsItsThreeGeckoLibAssets() {
        for (String id : AlphaRoster.SHIPPED) {
            assertTrue(Files.exists(geo(id)), "missing rig for " + id + ": " + geo(id));
            assertTrue(Files.exists(anim(id)), "missing animation for " + id + ": " + anim(id));
            assertTrue(Files.exists(skin(id)), "missing skin for " + id + ": " + skin(id));
        }
    }

    /**
     * Bedrock stores UVs as absolute pixels against the sheet size the geometry declares, so a
     * declared size that disagrees with the PNG shifts every island on the model.
     */
    @Test
    void declaredSheetSizeMatchesTheSkinOnDisk() throws IOException {
        for (String id : AlphaRoster.SHIPPED) {
            JsonObject desc = geometry(id).getAsJsonObject("description");
            BufferedImage png = ImageIO.read(skin(id).toFile());
            assertNotNull(png, "unreadable skin for " + id);
            assertEquals(desc.get("texture_width").getAsInt(), png.getWidth(),
                    id + ": geometry texture_width disagrees with " + skin(id));
            assertEquals(desc.get("texture_height").getAsInt(), png.getHeight(),
                    id + ": geometry texture_height disagrees with " + skin(id));
        }
    }

    // -- the clips the entity classes ask for exist ----------------------------------------------

    /**
     * The movement controller binds its two clips by name at registration, so a rig without them
     * throws on first render rather than falling back to a still pose.
     */
    @Test
    void everyAlphaCreatureHasTheClipsItsLocomotionClassBinds() throws IOException {
        for (String id : AlphaRoster.SHIPPED) {
            Set<String> clips = clipNames(id);
            assertTrue(clips.contains("idle"), id + ": no idle clip; every locomotion class binds one");
            boolean moves = clips.contains("walk") || clips.contains("fly") || clips.contains("swim");
            assertTrue(moves, id + ": no walk/fly/swim clip, so the movement controller has nothing to bind");
        }
    }

    /**
     * A creature may only declare clips its animation file actually defines: {@code declaredClips()}
     * is what {@code GenericBeastEntity} registers triggerables from, and triggering a clip the file
     * does not contain throws inside the render pass.
     */
    @Test
    void declaredClipsAllExistInTheAnimationFile() throws IOException {
        List<String> problems = new ArrayList<>();
        try (var files = Files.list(DATA.resolve("creatures"))) {
            for (Path def : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String id = def.getFileName().toString().replace(".json", "");
                JsonObject obj = json(def);
                if (!obj.has("clips")) {
                    continue;
                }
                Set<String> available = clipNames(id);
                for (var declared : obj.getAsJsonArray("clips")) {
                    String clip = declared.getAsString();
                    if (!available.contains(clip)) {
                        problems.add(id + " declares clip '" + clip + "' that " + anim(id) + " does not define");
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * Every bone an animation drives must exist in the rig it belongs to.
     *
     * <p>Repo-wide rather than roster-only, deliberately. GeckoLib does not throw on an unknown bone
     * the way it does on an unknown clip — it drops the channel — so this is silent: the animation
     * plays, the creature does not move, and nothing in a log says why. Three shipped creatures were
     * in that state when this test was written. The Niffler's walk drove {@code left_front_leg} and
     * three siblings against a rig whose legs are {@code leg_fl}/{@code leg_rf}/{@code leg_lb}/
     * {@code leg_rb}, so the mod's flagship creature slid along the ground with its legs locked; the
     * Horned Serpent and the Sea Serpent both drove a {@code body} bone that neither rig has, so
     * both swam as a rigid bar.
     */
    @Test
    void everyAnimatedBoneExistsInItsRig() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String id : everyRiggedId()) {
            Set<String> bones = new HashSet<>();
            for (var bone : geometry(id).getAsJsonArray("bones")) {
                bones.add(bone.getAsJsonObject().get("name").getAsString());
            }
            JsonObject animations = json(anim(id)).getAsJsonObject("animations");
            for (String clip : animations.keySet()) {
                JsonObject body = animations.getAsJsonObject(clip);
                if (!body.has("bones")) {
                    continue;
                }
                for (String driven : body.getAsJsonObject("bones").keySet()) {
                    if (!bones.contains(driven)) {
                        problems.add(id + ": clip '" + clip + "' drives bone '" + driven + "', which the rig has not got");
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    // -- the rest of the alpha contract ----------------------------------------------------------

    /**
     * A loot table, or a named exception. The Obscurus disperses rather than dying and there is no
     * item for it to leave — recorded here so "no loot table" stays a decision rather than a gap
     * nobody noticed.
     */
    @Test
    void everyAlphaCreatureDropsSomething() {
        Set<String> dropsNothingOnPurpose = Set.of("obscurus");
        for (String id : AlphaRoster.SHIPPED) {
            if (dropsNothingOnPurpose.contains(id)) {
                continue;
            }
            Path table = DATA.resolve(Path.of("loot_table", "entities", id + ".json"));
            assertTrue(Files.exists(table), "no loot table for alpha creature " + id + ": " + table);
        }
    }

    /**
     * The bestiary page has to be reachable: an entry with no {@code entityType} is never matched by
     * a sighting or a kill, so it can never leave {@code UNDISCOVERED}.
     */
    @Test
    void everyAlphaCreatureHasAReachableBestiaryEntry() throws IOException {
        for (String id : AlphaRoster.SHIPPED) {
            Path entry = DATA.resolve(Path.of("bestiary", "entries", id + ".json"));
            assertTrue(Files.exists(entry), "no bestiary entry for alpha creature " + id);
            JsonObject obj = json(entry);
            assertTrue(obj.has("entityType"),
                    id + ": bestiary entry names no entityType, so it can never be discovered");
            assertEquals("wizards_and_beasts:" + id, obj.get("entityType").getAsString(),
                    id + ": bestiary entry points at a different entity");
        }
    }

    /**
     * Every shipped entry, not just the alpha ones — an entry that names no entity is dead weight in
     * the book. {@code toad} shipped in that state.
     */
    @Test
    void noShippedBestiaryEntryIsUnreachable() throws IOException {
        List<String> problems = new ArrayList<>();
        try (var files = Files.list(DATA.resolve(Path.of("bestiary", "entries")))) {
            for (Path entry : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                if (!json(entry).has("entityType")) {
                    problems.add(entry.getFileName() + " names no entityType and can never be discovered");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** Alpha creatures are past the bulk-generated rig, by the repo's own marker for one. */
    @Test
    void noAlphaCreatureStillCarriesThePlaceholderMarker() throws IOException {
        for (String id : AlphaRoster.SHIPPED) {
            Path def = DATA.resolve(Path.of("creatures", id + ".json"));
            if (!Files.exists(def)) {
                continue; // bespoke entity class; no CreatureDefinition to mark
            }
            assertFalse(Files.readString(def).contains("PLACEHOLDER"),
                    id + " is on the alpha roster but its definition still says PLACEHOLDER");
        }
    }

    /** Every id that ships both a rig and an animation file, whatever it is used for. */
    private static List<String> everyRiggedId() throws IOException {
        List<String> ids = new ArrayList<>();
        try (var files = Files.list(ASSETS.resolve(Path.of("geckolib", "models", "entity")))) {
            for (Path rig : files.filter(p -> p.toString().endsWith(".geo.json")).sorted().toList()) {
                String id = rig.getFileName().toString().replace(".geo.json", "");
                if (Files.exists(anim(id))) {
                    ids.add(id);
                }
            }
        }
        assertFalse(ids.isEmpty(), "expected rigs on disk");
        return ids;
    }

    private static Set<String> clipNames(String id) throws IOException {
        Set<String> names = new HashSet<>();
        for (String key : json(anim(id)).getAsJsonObject("animations").keySet()) {
            names.add(key.substring(key.lastIndexOf('.') + 1));
        }
        return names;
    }
}
