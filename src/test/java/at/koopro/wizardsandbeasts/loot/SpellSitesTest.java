package at.koopro.wizardsandbeasts.loot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpellSitesTest {

    @AfterEach
    void clear() {
        SpellSites.replaceAll(Map.of());
    }

    @Test
    void bareAndNamespacedIdsFindTheSamePlaces() {
        SpellSites.replaceAll(Map.of("village_library", List.of("stupefy", "lumos"), "ruins", List.of("stupefy")));
        assertEquals(List.of("ruins", "village_library"), SpellSites.placesOf("stupefy"));
        assertEquals(List.of("ruins", "village_library"), SpellSites.placesOf("wizards_and_beasts:stupefy"));
        assertEquals(List.of("village_library"), SpellSites.placesOf("lumos"));
        assertEquals(List.of(), SpellSites.placesOf("avada_kedavra"));
    }
}
