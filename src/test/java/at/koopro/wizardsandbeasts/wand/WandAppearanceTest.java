package at.koopro.wizardsandbeasts.wand;

import at.koopro.wizardsandbeasts.wand.customization.WandConfiguration;
import at.koopro.wizardsandbeasts.wand.customization.WandModule;
import at.koopro.wizardsandbeasts.wand.customization.WandModuleRegistry;
import at.koopro.wizardsandbeasts.wand.customization.WandSlot;
import at.koopro.wizardsandbeasts.wand.registry.WandWoodAppearance;
import at.koopro.wizardsandbeasts.wand.registry.WandWoodDefinition;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A wand's colour and silhouette come from its wood's datapack entry.
 *
 * <p>The property that matters is not any particular colour or handle -- it is that <em>different
 * woods look different</em>, that every module a wood names really exists on the rig (a wood
 * pointing at a module nobody registered renders an empty slot, silently), and that an unknown wood
 * still gets a wood-looking colour.
 */
class WandAppearanceTest {

    private static final Path WOODS = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "wizards_and_beasts", "wand_woods");
    private static final Path WAND_GEO = Path.of("src", "main", "resources", "assets",
            "wizards_and_beasts", "geckolib", "models", "item", "wand.geo.json");

    private static final Map<String, WandWoodDefinition> SHIPPED = new HashMap<>();

    @BeforeAll
    static void load() throws IOException {
        WandModuleRegistry.bootstrap();
        try (Stream<Path> files = Files.list(WOODS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonElement json = JsonParser.parseString(Files.readString(file));
                String id = file.getFileName().toString().replace(".json", "");
                SHIPPED.put(id, WandWoodDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                        .getOrThrow(AssertionError::new));
            }
        }
        assertFalse(SHIPPED.isEmpty(), "no wand woods found under " + WOODS);
    }

    private static Identifier wood(String path) {
        return Identifier.fromNamespaceAndPath("wizards_and_beasts", path);
    }

    @Test
    void everyShippedWoodNamesItsOwnColourAndShape() {
        Set<Integer> tints = new HashSet<>();
        Set<WandConfiguration> shapes = new HashSet<>();
        SHIPPED.forEach((id, def) -> {
            assertTrue(def.appearance().tint().isPresent(), id + " names no tint");
            assertEquals(3, def.appearance().modules().size(), id + " must name a handle, a shaft and a tip");
            assertTrue(tints.add(WandAppearance.woodTint(def, wood(id))),
                    id + " shares a colour with another wood -- two wands would look alike");
            assertTrue(shapes.add(WandAppearance.configurationFor(def)),
                    id + " shares a silhouette with another wood");
        });
    }

    /** The join between two files that share no loader: wood JSON -> module registry -> geo bones. */
    @Test
    void everyModuleAWoodNamesExistsOnTheRig() throws IOException {
        String geo = Files.readString(WAND_GEO);
        SHIPPED.forEach((id, def) -> def.appearance().modules().forEach((slot, moduleId) -> {
            Optional<WandModule> module = WandModuleRegistry.get(moduleId);
            assertTrue(module.isPresent(), id + ": no wand module " + moduleId);
            assertEquals(slot, module.get().slot(), id + ": " + moduleId + " is not a " + slot.slotId());
            assertTrue(geo.contains("\"name\": \"" + module.get().boneName() + "\""),
                    id + ": wand.geo.json has no bone " + module.get().boneName());
        }));
    }

    @Test
    void aWoodsModulesAreLaidOverTheBaseWand() {
        WandWoodAppearance handleOnly = new WandWoodAppearance(Optional.empty(),
                Optional.of(wood("gnarled")), Optional.empty(), Optional.empty());
        WandConfiguration config = WandAppearance.configurationFor(withAppearance(handleOnly));
        assertEquals(Optional.of(wood("gnarled")), config.getModule(WandSlot.HANDLE));
        assertEquals(WandConfiguration.DEFAULT.getModule(WandSlot.SHAFT), config.getModule(WandSlot.SHAFT),
                "a slot the wood leaves open keeps the base wand's module");
        assertEquals(WandConfiguration.DEFAULT, WandAppearance.configurationFor(null));
    }

    @Test
    void anUnknownWoodStillGetsAStableColour() {
        // A datapack wood with no appearance must not fall back to "looks like whichever one the renderer picked".
        int first = WandAppearance.woodTint(null, wood("mahogany"));
        assertNotEquals(WandAppearance.UNTINTED, first);
        for (int i = 0; i < 100; i++) {
            assertEquals(first, WandAppearance.woodTint(null, wood("mahogany")),
                    "the derived colour must not change between calls");
        }
    }

    @Test
    void derivedColoursStayInTheTimberBand() {
        // The constraint that stops a datapack ever getting a hot magenta wand, which reads as a
        // missing texture rather than as an unfamiliar wood.
        for (String path : List.of("mahogany", "cedar", "bamboo", "driftwood", "zzz", "a", "wandwood_9000")) {
            int argb = WandAppearance.woodTint(null, wood(path));
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;
            assertEquals(0xFF, (argb >>> 24) & 0xFF, path + " must be fully opaque");
            assertTrue(r >= g && g >= b, path + " must read warm (r >= g >= b), got "
                    + r + "," + g + "," + b);
            assertTrue(r >= 120, path + " must not be near-black, got r=" + r);
        }
    }

    @Test
    void aWandWithNoWoodKeepsTheClassicBrown() {
        // The sheet is painted pale for the tint to colour; a woodless wand must not show it bare,
        // and multiplying by 0 would render it black.
        int tint = WandAppearance.woodTint(null, (Identifier) null);
        assertEquals(WandAppearance.NO_WOOD_TINT, tint);
        assertNotEquals(WandAppearance.UNTINTED, tint);
        assertNotEquals(0, tint & 0x00FFFFFF);
    }

    @Test
    void tintAcceptsBothHexForms() {
        String json = "{\"tint\": \"#7D5531\"}";
        WandWoodAppearance opaque = WandWoodAppearance.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(AssertionError::new);
        assertEquals(Optional.of(0xFF7D5531), opaque.tint(), "#RRGGBB is opaque");
        WandWoodAppearance written = WandWoodAppearance.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"tint\": \"#807D5531\"}")).getOrThrow(AssertionError::new);
        assertEquals(Optional.of(0x807D5531), written.tint(), "#AARRGGBB is taken as written");
    }

    private static WandWoodDefinition withAppearance(WandWoodAppearance appearance) {
        WandWoodDefinition any = SHIPPED.values().iterator().next();
        return new WandWoodDefinition(any.displayName(), any.affinityTags(), any.spellModifiers(),
                any.personalityAffinity(), any.rarity(), any.refuseThreshold(), any.castModifiers(),
                any.temperament(), appearance);
    }
}
