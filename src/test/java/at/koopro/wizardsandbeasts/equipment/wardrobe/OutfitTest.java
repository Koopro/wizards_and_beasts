package at.koopro.wizardsandbeasts.equipment.wardrobe;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutfitTest {

    private static final Identifier LION = Identifier.fromNamespaceAndPath("wizards_and_beasts", "house_lion");
    private static final Identifier PLAIN = Identifier.fromNamespaceAndPath("wizards_and_beasts", "school_plain");

    @Test
    void anEmptyComponentReadsAsTheDefault() {
        Outfit outfit = Outfit.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
        assertEquals(Outfit.DEFAULT, outfit);
    }

    @Test
    void aRankOutsideTheBoneGroupsDoesNotDecode() {
        assertFalse(Outfit.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"rank\": 4}")).isSuccess());
        assertFalse(Outfit.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"rank\": -1}")).isSuccess());
    }

    @Test
    void theConstructorClampsRank() {
        assertEquals(Outfit.MAX_RANK, new Outfit(Optional.empty(), 9).rank());
        assertEquals(0, new Outfit(Optional.empty(), -2).rank());
    }

    @Test
    void roundTrips() {
        Outfit outfit = new Outfit(Optional.of(LION), 2);
        var json = Outfit.CODEC.encodeStart(JsonOps.INSTANCE, outfit).getOrThrow();
        assertEquals(outfit, Outfit.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void theDefaultLiveryDrawsTheBaseSheetAndAnyOtherItsOwn() {
        assertNull(Wardrobe.liveryTexture("student_robe", PLAIN, Optional.empty()));
        assertNull(Wardrobe.liveryTexture("student_robe", PLAIN, Optional.of(PLAIN)));
        assertEquals(Identifier.fromNamespaceAndPath("wizards_and_beasts", "textures/armor/student_robe/house_lion.png"),
                Wardrobe.liveryTexture("student_robe", PLAIN, Optional.of(LION)));
    }

    /**
     * Cosmetic progression carries no advantage: stats come from the item's wearables entry, and no
     * code that grants or checks stats reads the outfit. Structural, so a later change has to break
     * it on purpose.
     */
    @Test
    void noStatCodeReadsTheOutfit() throws IOException {
        Path equipment = Path.of("src", "main", "java", "at", "koopro", "wizardsandbeasts", "equipment");
        List<Path> statCode;
        try (Stream<Path> files = Files.walk(equipment)) {
            statCode = files.filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> !f.toString().contains("wardrobe"))
                    .toList();
        }
        assertTrue(statCode.size() > 3, "the equipment package moved; point this test at it");
        for (Path file : statCode) {
            assertFalse(Files.readString(file).contains("Outfit"), file + " reads the outfit");
        }
    }
}
