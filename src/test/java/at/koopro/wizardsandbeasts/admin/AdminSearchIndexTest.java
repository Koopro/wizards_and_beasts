package at.koopro.wizardsandbeasts.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.admin.search.AdminSearchIndex;
import at.koopro.wizardsandbeasts.client.admin.search.AdminSearchIndex.Entry;
import at.koopro.wizardsandbeasts.client.admin.search.AdminSearchIndex.Kind;
import at.koopro.wizardsandbeasts.client.admin.search.AdminSearchIndex.Target;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The global search's matching and ranking, on hand-made entries shaped like the ones the client builds: "phoenix"
 * must find the creature, its variants, its settings and the phoenix-feather core, with the creature first.
 */
class AdminSearchIndexTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    private static List<Entry> sample() {
        List<Entry> out = new ArrayList<>();
        out.add(Entry.of(Kind.SECTION, "Creatures", "Roster, spawning and variants", new Target.Section(AdminCategory.CREATURES)));
        out.add(Entry.of(Kind.CREATURE, "Phoenix", "Creature · Bird", new Target.Creature("phoenix"), "phoenix", "noble"));
        out.add(Entry.of(Kind.CREATURE, "Augurey", "Creature · Bird", new Target.Creature("augurey"), "augurey",
                "The Irish Phoenix"));
        out.add(Entry.of(Kind.VARIANT, "Phoenix · Scarlet — Weight", "Creatures",
                new Target.Setting(id("creature/phoenix/variant/scarlet/weight"), AdminCategory.CREATURES),
                "creature/phoenix/variant/scarlet/weight"));
        out.add(Entry.of(Kind.SETTING, "Phoenix — Natural spawning", "Creatures",
                new Target.Setting(id("creature/phoenix/natural_spawn"), AdminCategory.CREATURES), "creature/phoenix/natural_spawn"));
        out.add(Entry.of(Kind.WAND_PART, "Phoenix Feather", "Wand core", new Target.WandPart(true, "phoenix_feather"),
                "phoenix_feather"));
        out.add(Entry.of(Kind.SETTING, "Broom speed", "Travel", new Target.Setting(id("broom_server_speed_scale"),
                AdminCategory.TRAVEL), "broom_server_speed_scale", "How fast every broom flies."));
        out.add(Entry.of(Kind.SPELL, "Stupefy", "Spell · Charm", new Target.Spell("wizards_and_beasts:stupefy"), "stunning"));
        return out;
    }

    @Test
    void phoenixFindsTheCreatureFirstThenEverythingAboutIt() {
        AdminSearchIndex.Result result = AdminSearchIndex.query(sample(), "phoenix", 20);
        List<Entry> hits = result.entries();
        assertEquals(Kind.CREATURE, hits.get(0).kind(), "an exact title match leads");
        assertEquals("Phoenix", hits.get(0).title());
        List<Kind> kinds = hits.stream().map(Entry::kind).toList();
        assertTrue(kinds.contains(Kind.VARIANT), "a variant of the phoenix is found");
        assertTrue(kinds.contains(Kind.SETTING), "a phoenix setting is found");
        assertTrue(kinds.contains(Kind.WAND_PART), "the phoenix-feather core is found");
        assertTrue(hits.stream().anyMatch(e -> e.title().equals("Augurey")), "a keyword-only match is still found");
        assertEquals("Augurey", hits.get(hits.size() - 1).title(), "a keyword-only match ranks last");
        assertFalse(hits.stream().anyMatch(e -> e.title().equals("Stupefy")));
    }

    @Test
    void everyWordMustMatchAndCaseAndSeparatorsDoNotMatter() {
        List<Entry> hits = AdminSearchIndex.query(sample(), "PHOENIX  scarlet", 20).entries();
        assertEquals(1, hits.size());
        assertEquals(Kind.VARIANT, hits.get(0).kind());
        // An id typed with its separators finds the same thing.
        assertEquals(Kind.SETTING, AdminSearchIndex.query(sample(), "broom_server_speed", 5).entries().get(0).kind());
        // A description word finds its setting.
        assertEquals("Broom speed", AdminSearchIndex.query(sample(), "fast", 5).entries().get(0).title());
    }

    @Test
    void anEmptyQueryFindsNothingAndTheLimitKeepsTheTotal() {
        assertTrue(AdminSearchIndex.query(sample(), "   ", 20).entries().isEmpty());
        AdminSearchIndex.Result limited = AdminSearchIndex.query(sample(), "phoenix", 2);
        assertEquals(2, limited.entries().size());
        assertTrue(limited.total() > 2, "the total counts every match, not just the shown ones");
    }

    @Test
    void theIndexRebuildsOnlyWhenTheSourcesChange() {
        AdminSearchIndex index = new AdminSearchIndex();
        assertTrue(index.stale(7L));
        index.rebuild(7L, sample());
        assertFalse(index.stale(7L));
        assertTrue(index.stale(8L));
        assertEquals(sample().size(), index.size());
    }

    @Test
    void idsReadAsWords() {
        assertEquals("creature phoenix variant scarlet red", AdminSearchIndex.normalise("creature/phoenix/variant/scarlet_red"));
        assertEquals("Scarlet Red", AdminSearchIndex.humanise("scarlet_red"));
        assertEquals("", AdminSearchIndex.normalise("  //  "));
    }
}
