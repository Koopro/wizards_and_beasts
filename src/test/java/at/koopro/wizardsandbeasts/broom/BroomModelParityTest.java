package at.koopro.wizardsandbeasts.broom;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Holds the master broom rig against the things that reference it by name.
 *
 * <p>Every failure this catches compiles cleanly and only shows up in a running game as an
 * invisible part, a dead animation or a missing-texture broom — GeckoLib resolves bones,
 * clips and textures by string at load time, so nothing upstream of the client notices.
 */
class BroomModelParityTest {

    private static final Gson GSON = new Gson();
    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "wizards_and_beasts");
    private static final Path GEO = ASSETS.resolve(Path.of("geckolib", "models", "entity", "broom.geo.json"));
    private static final Path ANIM = ASSETS.resolve(Path.of("geckolib", "animations", "entity", "broom.animation.json"));
    private static final Path SKIN = ASSETS.resolve(Path.of("textures", "entity", "broom.png"));
    private static final Path ENTITY_SRC =
            Path.of("src", "main", "java", "at", "koopro", "wizardsandbeasts", "entity", "broom", "BroomEntity.java");
    private static final Path DEFINITIONS =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "broom_definitions");
    private static final Path TEXTURES = ASSETS.resolve(Path.of("textures", "entity"));
    private static final Path SOUNDS = ASSETS.resolve("sounds.json");
    private static final Path LANG = ASSETS.resolve(Path.of("lang", "en_us.json"));

    /** Every slot in the enum must have at least one variant bone, or that slot cannot be selected. */
    @Test
    void everySlotHasVariantBonesInTheRig() throws IOException {
        Set<String> bones = boneNames();
        for (BroomSlot slot : BroomSlot.values()) {
            boolean any = bones.stream().anyMatch(b -> b.startsWith(slot.bonePrefix()));
            assertTrue(any, slot + " has no " + slot.bonePrefix() + "* bones in broom.geo.json");
        }
    }

    /** Each slot's container bone must exist: the animations key off it, not off the variants. */
    @Test
    void everySlotHasItsContainerBone() throws IOException {
        Set<String> bones = boneNames();
        for (BroomSlot slot : BroomSlot.values()) {
            assertTrue(bones.contains(slot.slotId()),
                    "missing container bone '" + slot.slotId() + "' — animations target it so one clip "
                            + "covers every variant of the slot");
        }
    }

    /**
     * {@code BroomSlot.variants()} must name exactly the variant bones the rig ships.
     *
     * <p>This is the seam between a Java list and a Python generator, and it fails silently in the
     * worst direction: the renderer shows one variant by hiding every other one it knows about, so a
     * bone the list has never heard of is never hidden and draws on top of whatever was selected.
     * A missing bone is the milder failure — a variant that simply cannot be chosen.
     */
    @Test
    void slotVariantListsMatchTheRig() throws IOException {
        for (BroomSlot slot : BroomSlot.values()) {
            Set<String> declared = new HashSet<>();
            slot.variants().forEach(v -> declared.add(slot.boneName(v)));

            // A variant is a *direct child of the slot container*, not merely a bone sharing the
            // prefix. A shaft variant is the root of a three-bone chain — shaft_swept_mid and
            // shaft_swept_grip carry the sweep and are hidden with their parent by
            // skipChildrenRender, so they are not separately selectable and must not be listed.
            Set<String> inRig = childrenOf(slot.slotId());

            assertEquals(inRig, declared,
                    slot + ": BroomSlot.variants() and broom.geo.json disagree. Bones only in the "
                            + "rig are never hidden and will draw over the selected variant; bones "
                            + "only in the list cannot be selected.");
        }
    }

    /** The default silhouette must be drawable, or an unconfigured broom renders nothing. */
    @Test
    void defaultVariantBonesExist() throws IOException {
        Set<String> bones = boneNames();
        BroomSlot.defaults().forEach((slot, id) ->
                assertTrue(bones.contains(slot.boneName(id.getPath())),
                        "default variant " + id + " for " + slot + " has no bone "
                                + slot.boneName(id.getPath())));
    }

    /** Bones the renderer and the FX pass reach for by literal name. */
    @Test
    void contractBonesSurvive() throws IOException {
        Set<String> bones = boneNames();
        assertTrue(bones.contains("broom_body"), "BroomRenderer applies tilt to broom_body by name");
        assertTrue(bones.contains("root"), "the shipped clips animate root");
        for (String fx : new String[]{"fx_tip", "fx_tail", "fx_mount"}) {
            assertTrue(bones.contains(fx), "missing FX anchor " + fx);
        }
    }

    /** Every animation BroomEntity names must exist, and every clip must target real bones. */
    @Test
    void animationsResolveAgainstEntityAndRig() throws IOException {
        JsonObject clips = GSON.fromJson(Files.readString(ANIM), JsonObject.class)
                .getAsJsonObject("animations");
        Set<String> bones = boneNames();

        Matcher m = Pattern.compile("\"(animation\\.broom\\.[a-z_]+)\"")
                .matcher(Files.readString(ENTITY_SRC));
        int referenced = 0;
        while (m.find()) {
            referenced++;
            assertTrue(clips.has(m.group(1)),
                    "BroomEntity plays " + m.group(1) + " but broom.animation.json has no such clip");
        }
        assertTrue(referenced > 0, "expected BroomEntity to reference animations by name");

        for (String clip : clips.keySet()) {
            JsonObject targets = clips.getAsJsonObject(clip).getAsJsonObject("bones");
            if (targets == null) continue;
            for (String bone : targets.keySet()) {
                assertTrue(bones.contains(bone),
                        clip + " animates '" + bone + "', which is not a bone in broom.geo.json");
            }
        }
    }

    /** The rig's declared sheet size must match the PNG, and no island may fall off it. */
    @Test
    void everyCubeIslandFitsTheDeclaredSheet() throws IOException {
        JsonObject geometry = geometry();
        JsonObject desc = geometry.getAsJsonObject("description");
        int w = desc.get("texture_width").getAsInt();
        int h = desc.get("texture_height").getAsInt();

        assertTrue(Files.exists(SKIN), "broom.geo.json has no texture at " + SKIN);
        var png = javax.imageio.ImageIO.read(SKIN.toFile());
        assertEquals(w, png.getWidth(), "declared texture_width does not match broom.png");
        assertEquals(h, png.getHeight(), "declared texture_height does not match broom.png");

        geometry.getAsJsonArray("bones").forEach(el -> {
            JsonObject bone = el.getAsJsonObject();
            if (!bone.has("cubes")) return;
            bone.getAsJsonArray("cubes").forEach(ce -> {
                JsonObject cube = ce.getAsJsonObject();
                var size = cube.getAsJsonArray("size");
                var uv = cube.getAsJsonArray("uv");
                int cw = ceil(size.get(0).getAsDouble());
                int ch = ceil(size.get(1).getAsDouble());
                int cd = ceil(size.get(2).getAsDouble());
                int u = uv.get(0).getAsInt();
                int v = uv.get(1).getAsInt();
                assertTrue(u + 2 * cd + 2 * cw <= w && v + cd + ch <= h,
                        bone.get("name").getAsString() + " has a box-UV island running off the "
                                + w + "x" + h + " sheet at uv [" + u + ", " + v + "]");
            });
        });
    }

    /**
     * Every variant a shipped broom names must be one the renderer knows how to show.
     *
     * <p>The renderer displays a variant by hiding every <em>other</em> variant of that slot, so a
     * definition naming something outside {@link BroomSlot#variants()} does not fall back and does
     * not warn: it hides all the real variants and draws a broom with no shaft. This is the seam a
     * datapack author is most likely to get wrong, and the shipped brooms are the worked examples.
     */
    @Test
    void shippedDefinitionsNameKnownVariants() throws IOException {
        forEachDefinition((file, json) -> {
            JsonObject slots = json.getAsJsonObject("model_slots");
            if (slots == null) return;
            for (String slotId : slots.keySet()) {
                BroomSlot slot = BroomSlot.byId(slotId).orElseThrow(
                        () -> new AssertionError(file + " names unknown slot '" + slotId + "'"));
                String variant = Identifier.parse(slots.get(slotId).getAsString()).getPath();
                assertTrue(slot.variants().contains(variant),
                        file + " selects " + slot + " variant '" + variant + "', which BroomSlot "
                                + "does not list — the renderer would hide every real variant "
                                + "of that slot and draw a gap");
            }
        });
    }

    /**
     * A broom that names its own sheet must ship it, at the size the rig declares.
     *
     * <p>All the per-broom sheets share one UV layout, because there is one geometry. A sheet of the
     * wrong size does not fail to load: it samples the islands from the wrong texels and paints the
     * bristles onto the handle.
     */
    @Test
    void everyNamedTextureExistsAtTheDeclaredSize() throws IOException {
        JsonObject desc = geometry().getAsJsonObject("description");
        int w = desc.get("texture_width").getAsInt();
        int h = desc.get("texture_height").getAsInt();

        forEachDefinition((file, json) -> {
            if (!json.has("texture")) return;
            Identifier id = Identifier.parse(json.get("texture").getAsString());
            Path png = texturePath(id);
            assertTrue(Files.exists(png), file + " names texture " + id + " but " + png
                    + " does not exist — the broom would render untextured");
            try {
                var image = javax.imageio.ImageIO.read(png.toFile());
                assertEquals(w, image.getWidth(), png + " is not the width broom.geo.json declares");
                assertEquals(h, image.getHeight(), png + " is not the height broom.geo.json declares");
            } catch (IOException ex) {
                throw new AssertionError("could not read " + png, ex);
            }
        });
    }

    /**
     * {@code entity_texture} and {@code wood_tint} are alternatives, never layers.
     *
     * <p>A per-broom sheet is painted in final colour; the shared sheet is painted greyscale so the
     * tint can supply the colour. Setting both would multiply the wood in twice and drag the band
     * and the bristles down with it, because GeckoLib's render colour applies to the whole pass.
     * {@code BroomRenderer} refuses to tint a broom that has its own sheet, so authoring both fails
     * silently, as a tint that simply does nothing.
     */
    @Test
    void noShippedBroomBothPaintsAndTints() throws IOException {
        forEachDefinition((file, json) ->
                assertFalse(json.has("texture") && json.has("wood_tint"),
                        file + " sets both texture and wood_tint. Its sheet is already in "
                                + "final colour, so the tint is ignored — drop one of the two."));
    }

    /**
     * Every sound a broom names must be an event {@code sounds.json} defines.
     *
     * <p>A missing event is not a crash and not a log line — the sound simply never plays, which is
     * indistinguishable from a broom that authored no sound at all. That makes it exactly the kind
     * of typo that ships.
     */
    @Test
    void everyNamedSoundIsDefined() throws IOException {
        JsonObject sounds = GSON.fromJson(Files.readString(SOUNDS), JsonObject.class);
        forEachDefinition((file, json) -> {
            for (String key : new String[]{"boostSound", "idleLoopSound"}) {
                if (!json.has(key)) continue;
                Identifier id = Identifier.parse(json.get(key).getAsString());
                assertEquals("wizards_and_beasts", id.getNamespace(),
                        file + ": " + key + " names " + id + ", which this test cannot vouch for");
                assertTrue(sounds.has(id.getPath()),
                        file + ": " + key + " names " + id + ", which sounds.json does not define — "
                                + "the cue would be silent, exactly like a broom that named none");
            }
        });
    }

    /** Every sound event in {@code sounds.json} must have the subtitle line it declares. */
    @Test
    void everySoundSubtitleHasALangKey() throws IOException {
        JsonObject sounds = GSON.fromJson(Files.readString(SOUNDS), JsonObject.class);
        JsonObject lang = GSON.fromJson(Files.readString(LANG), JsonObject.class);
        for (String event : sounds.keySet()) {
            JsonObject entry = sounds.getAsJsonObject(event);
            if (!entry.has("subtitle")) continue;
            String key = entry.get("subtitle").getAsString();
            assertTrue(lang.has(key),
                    "sounds.json event '" + event + "' declares subtitle " + key
                            + ", which en_us.json does not define — subtitles show the raw key");
        }
    }

    /**
     * A trail particle must be one that can be spawned from its id alone.
     *
     * <p>{@code BroomAudio} resolves {@code trailParticle} from an id with no options, and falls back
     * to a plain wisp for anything it cannot build. So naming a type that carries options — the mod's
     * own tinted spell particles, which need a colour — fails silently: the broom keeps the default
     * trail and the authored value does nothing at all.
     *
     * <p>Checked against the source rather than the registry, which a unit test does not load. A
     * mod-namespaced particle has to be declared {@code SimpleParticleType} in {@code ModParticles}
     * <em>and</em> ship the {@code particles/} definition that gives it a sprite; vanilla ids are
     * taken on trust.
     */
    @Test
    void everyTrailParticleCanBeSpawnedFromItsIdAlone() throws IOException {
        Set<String> simpleTypes = modSimpleParticleTypes();
        forEachDefinition((file, json) -> {
            if (!json.has("trailParticle")) return;
            Identifier id = Identifier.parse(json.get("trailParticle").getAsString());
            if (id.getNamespace().equals("minecraft")) return;

            assertEquals("wizards_and_beasts", id.getNamespace(),
                    file + ": trailParticle " + id + " belongs to a mod this test cannot vouch for");
            assertTrue(simpleTypes.contains(id.getPath()),
                    file + ": trailParticle " + id + " is not registered as a SimpleParticleType in "
                            + "ModParticles. A type with options cannot be built from an id, so "
                            + "BroomAudio would silently fall back to the default wisp.");
            Path definition = ASSETS.resolve(Path.of("particles", id.getPath() + ".json"));
            assertTrue(Files.exists(definition),
                    file + ": trailParticle " + id + " has no " + definition + ", so it would "
                            + "register with no sprite and render nothing");
        });
    }

    /** Names registered as {@code SimpleParticleType} in {@code ModParticles}. */
    private static Set<String> modSimpleParticleTypes() throws IOException {
        Path source = Path.of("src", "main", "java", "at", "koopro", "wizardsandbeasts",
                "registry", "ModParticles.java");
        Set<String> names = new HashSet<>();
        Matcher m = Pattern.compile("\"([a-z0-9_]+)\"[^\"]*new SimpleParticleType")
                .matcher(Files.readString(source));
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    /**
     * A broom naming its own {@code model} must ship it, and it must be the master rig with exactly
     * that broom's slot variants left in.
     *
     * <p>These files are generated by {@code tools/broom_model.py} and must never be hand-edited.
     * That is the whole reason this test exists: the renderer addresses slot variants by name, so a
     * per-broom geometry that has drifted from the rig starts hiding bones that exist in one copy and
     * not the other — and a stale file looks exactly like a working one until something is invisible.
     *
     * <p>Container bones survive even for slots the broom leaves unset, because the shipped clips
     * animate them: {@code fly_forward} rotates {@code bristles}, not {@code bristles_streamlined}.
     */
    @Test
    void everyPerBroomModelMatchesTheMasterRig() throws IOException {
        Set<String> masterBones = boneNames();
        forEachDefinition((file, json) -> {
            if (!json.has("model")) return;
            Identifier model = Identifier.parse(json.get("model").getAsString());
            Path geo = ASSETS.resolve(Path.of("geckolib", "models", "entity",
                    model.getPath() + ".geo.json"));
            assertTrue(Files.exists(geo),
                    file + " names model " + model + " but " + geo + " does not exist. Run "
                            + "tools/broom_model.py -- these files are generated.");

            Set<String> expected = expectedBones(masterBones, json.getAsJsonObject("model_slots"));
            Set<String> actual;
            String identifier;
            try {
                JsonObject geometry = GSON.fromJson(Files.readString(geo), JsonObject.class)
                        .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
                actual = new HashSet<>();
                geometry.getAsJsonArray("bones").forEach(
                        el -> actual.add(el.getAsJsonObject().get("name").getAsString()));
                identifier = geometry.getAsJsonObject("description").get("identifier").getAsString();
            } catch (IOException ex) {
                throw new AssertionError("could not read " + geo, ex);
            }

            assertEquals(expected, actual, geo.getFileName()
                    + " has drifted from the master rig. Regenerate with tools/broom_model.py; "
                    + "bones only here are never hidden and draw over the selection, bones only in "
                    + "the rig cannot be selected.");
            assertEquals("geometry." + model.getPath(), identifier,
                    geo.getFileName() + " shares an identifier with another model, so GeckoLib would "
                            + "cache one under the other's name");
        });
    }

    /** The master rig minus every variant this definition does not select. */
    private static Set<String> expectedBones(Set<String> masterBones, JsonObject slots) {
        Set<String> selected = new HashSet<>();
        for (BroomSlot slot : BroomSlot.values()) {
            String id = slots.get(slot.slotId()).getAsString();
            selected.add(slot.boneName(Identifier.parse(id).getPath()));
        }
        Set<String> expected = new HashSet<>();
        for (String bone : masterBones) {
            boolean isVariant = false;
            for (BroomSlot slot : BroomSlot.values()) {
                if (bone.startsWith(slot.bonePrefix())) {
                    isVariant = true;
                    // A shaft variant is a three-bone chain; its children carry the sweep and ride
                    // with it, so they are kept or dropped together.
                    if (selected.stream().anyMatch(bone::startsWith)) {
                        expected.add(bone);
                    }
                    break;
                }
            }
            if (!isVariant) {
                expected.add(bone);
            }
        }
        return expected;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Resolves a {@code texture} value in either of the two forms {@code BroomAssets} accepts: a
     * complete path rooted at {@code textures/}, or a GeckoLib subpath under {@code textures/entity/}.
     */
    private static Path texturePath(Identifier id) {
        String path = id.getPath();
        Path root = path.startsWith("textures/") ? ASSETS : TEXTURES;
        String relative = path.startsWith("textures/") ? path : path + ".png";
        return root.resolve(relative.replace('/', java.io.File.separatorChar));
    }

    /** Runs a check over every shipped broom definition, naming the file in any failure. */
    private static void forEachDefinition(java.util.function.BiConsumer<String, JsonObject> check)
            throws IOException {
        try (var files = Files.list(DEFINITIONS)) {
            var jsons = files.filter(f -> f.toString().endsWith(".json")).sorted().toList();
            assertFalse(jsons.isEmpty(), "no broom definitions found in " + DEFINITIONS);
            for (Path file : jsons) {
                check.accept(file.getFileName().toString(),
                        GSON.fromJson(Files.readString(file), JsonObject.class));
            }
        }
    }


    private static int ceil(double v) {
        return (int) Math.round(v + 0.4999);
    }

    private static JsonObject geometry() throws IOException {
        return GSON.fromJson(Files.readString(GEO), JsonObject.class)
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
    }

    /** Names of the bones parented directly to {@code parent}. */
    private static Set<String> childrenOf(String parent) throws IOException {
        Set<String> names = new HashSet<>();
        geometry().getAsJsonArray("bones").forEach(el -> {
            JsonObject bone = el.getAsJsonObject();
            if (bone.has("parent") && parent.equals(bone.get("parent").getAsString())) {
                names.add(bone.get("name").getAsString());
            }
        });
        return names;
    }

    private static Set<String> boneNames() throws IOException {
        Set<String> names = new HashSet<>();
        geometry().getAsJsonArray("bones").forEach(
                el -> names.add(el.getAsJsonObject().get("name").getAsString()));
        return names;
    }
}
