package at.koopro.wizardsandbeasts.animagus;

import at.koopro.wizardsandbeasts.ability.AnimagusForms;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every selectable Animagus form has a body definition, and every asset a definition names is on disk
 * (KNOWN_ISSUES.md §8a.3).
 *
 * <p>Four of the six selectable forms — the stag among them — had no {@code animagus_forms} file, so a
 * wizard who became one got no attributes and no capabilities, and the innate-form draw (which picks only
 * among defined forms) made every new Animagus a cat or a dog. Meanwhile the four files that did exist
 * named GeckoLib models, textures and animations that were never shipped. Both halves are pinned here: the
 * join between {@link AnimagusForms#IDS} and the data, and the join between the data and the assets.
 */
class AnimagusFormDataParityTest {

    private static final Path FORMS = Path.of("src/main/resources/data/wizards_and_beasts/animagus_forms");
    private static final Path ASSETS = Path.of("src/main/resources/assets");

    @Test
    void everySelectableFormHasADefinitionThatGivesItABody() throws IOException {
        for (String formId : AnimagusForms.IDS) {
            String beast = formId.substring("animagus_".length());
            Path file = FORMS.resolve(beast + ".json");
            assertTrue(Files.exists(file), formId + " is selectable but has no animagus_forms/" + beast + ".json");
            AnimagusFormDefinition def = parse(file);
            assertFalse(def.attributes().isEmpty() && def.capabilities().isEmpty(),
                    formId + " has a definition that changes nothing about the body");
        }
    }

    @Test
    void everyDeclaredAssetExistsAndEveryMappedClipIsInItsFile() throws IOException {
        try (var files = Files.list(FORMS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String name = file.getFileName().toString();
                AnimagusFormDefinition def = parse(file);
                for (Optional<Identifier> asset : java.util.List.of(def.model(), def.texture(), def.animations())) {
                    asset.ifPresent(id -> assertTrue(Files.exists(assetPath(id)),
                            name + " names " + id + ", which does not exist"));
                }
                if (def.animations().isPresent()) {
                    JsonObject clips = JsonParser.parseString(Files.readString(assetPath(def.animations().get())))
                            .getAsJsonObject().getAsJsonObject("animations");
                    Set<String> present = clips.keySet();
                    def.animationMap().forEach((role, clip) -> assertTrue(present.contains(clip),
                            name + " maps " + role + " to " + clip + ", which its animation file does not have"));
                }
            }
        }
    }

    private static Path assetPath(Identifier id) {
        return ASSETS.resolve(id.getNamespace()).resolve(id.getPath());
    }

    private static AnimagusFormDefinition parse(Path file) throws IOException {
        return AnimagusFormDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)))
                .getOrThrow(msg -> new AssertionError(file.getFileName() + " failed to decode: " + msg));
    }
}
