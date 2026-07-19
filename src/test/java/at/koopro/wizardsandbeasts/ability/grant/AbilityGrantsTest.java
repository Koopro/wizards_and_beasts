package at.koopro.wizardsandbeasts.ability.grant;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the source-tracked grant model: correct source attribution, and the revoke-safety matrix that is
 * the whole reason the layer exists — a refund strips exactly SKILL_NODE grants, a vocation clear strips
 * exactly VOCATION grants, and heritage grants survive both. All exercised over the pure
 * {@link AbilityGrants#of} builder (the same one the server recompute and the client mirror use), so no
 * Minecraft bootstrap or live player is needed.
 */
class AbilityGrantsTest {

    // Real vocation grantedAbilities (from the shipped vocation JSONs), to prove the VOCATION source
    // carries exactly what the vocations declare.
    private static final List<String> DUELIST = List.of("duelist_spell_power", "duelist_cast_speed");
    private static final List<String> DARK_ARTS = List.of("curse_power", "dark_corruption_accrual");
    private static final List<String> HERBOLOGIST = List.of("crop_yield");
    private static final List<String> MAGIZOOLOGIST = List.of("beast_capacity", "mount_loyalty");

    private static final List<String> HERITAGE_TAGS = List.of("no_wand", "nature_speech");
    private static final List<String> NODE_ABILITIES = List.of("niffler_friend", "green_thumb");

    // ── Source attribution ──

    @Test
    void eachSourceAttributesItsOwnKeys() {
        AbilityGrants g = AbilityGrants.of(HERITAGE_TAGS, DUELIST, NODE_ABILITIES);

        assertTrue(g.hasFrom(AbilityKey.of("no_wand"), AbilityGrants.Source.HERITAGE));
        assertTrue(g.hasFrom(AbilityKey.of("duelist_spell_power"), AbilityGrants.Source.VOCATION));
        assertTrue(g.hasFrom(AbilityKey.of("niffler_friend"), AbilityGrants.Source.SKILL_NODE));

        // No cross-attribution: a vocation flag is not a heritage grant.
        assertFalse(g.hasFrom(AbilityKey.of("duelist_spell_power"), AbilityGrants.Source.HERITAGE));
        assertFalse(g.hasFrom(AbilityKey.of("no_wand"), AbilityGrants.Source.SKILL_NODE));
    }

    @Test
    void vocationSourceCarriesExactlyDeclaredFlags() {
        for (List<String> vocation : List.of(DUELIST, DARK_ARTS, HERBOLOGIST, MAGIZOOLOGIST)) {
            AbilityGrants g = AbilityGrants.of(List.of(), vocation, List.of());
            for (String flag : vocation) {
                assertTrue(g.hasFrom(AbilityKey.of(flag), AbilityGrants.Source.VOCATION), flag);
            }
            assertEquals(vocation.size(), g.keysFrom(AbilityGrants.Source.VOCATION).size());
        }
    }

    @Test
    void sameKeyFromTwoSourcesMergesWithBothSources() {
        AbilityGrants g = AbilityGrants.of(List.of("shared"), List.of("shared"), List.of());
        assertEquals(2, g.sourcesOf(AbilityKey.of("shared")).size());
        assertTrue(g.hasFrom(AbilityKey.of("shared"), AbilityGrants.Source.HERITAGE));
        assertTrue(g.hasFrom(AbilityKey.of("shared"), AbilityGrants.Source.VOCATION));
    }

    // ── Revoke-safety matrix ──

    @Test
    void refundStripsExactlySkillNodeGrants() {
        AbilityGrants before = AbilityGrants.of(HERITAGE_TAGS, DUELIST, NODE_ABILITIES);
        // Refund = allocated nodes cleared → recompute with empty skill-node input.
        AbilityGrants after = AbilityGrants.of(HERITAGE_TAGS, DUELIST, List.of());

        // SKILL_NODE grants gone.
        assertFalse(after.has(AbilityKey.of("niffler_friend")));
        assertFalse(after.has(AbilityKey.of("green_thumb")));
        // HERITAGE + VOCATION untouched.
        assertTrue(after.hasFrom(AbilityKey.of("no_wand"), AbilityGrants.Source.HERITAGE));
        assertTrue(after.hasFrom(AbilityKey.of("duelist_spell_power"), AbilityGrants.Source.VOCATION));
        assertTrue(before.has(AbilityKey.of("niffler_friend"))); // sanity: they were present before
    }

    @Test
    void vocationClearStripsExactlyVocationGrants() {
        AbilityGrants after = AbilityGrants.of(HERITAGE_TAGS, List.of(), NODE_ABILITIES);

        // VOCATION grants gone.
        assertFalse(after.has(AbilityKey.of("duelist_spell_power")));
        assertFalse(after.has(AbilityKey.of("duelist_cast_speed")));
        // HERITAGE + SKILL_NODE untouched.
        assertTrue(after.hasFrom(AbilityKey.of("no_wand"), AbilityGrants.Source.HERITAGE));
        assertTrue(after.hasFrom(AbilityKey.of("niffler_friend"), AbilityGrants.Source.SKILL_NODE));
    }

    @Test
    void heritageGrantsSurviveBothRefundAndVocationClear() {
        AbilityGrants stripped = AbilityGrants.of(HERITAGE_TAGS, List.of(), List.of());
        for (String tag : HERITAGE_TAGS) {
            assertTrue(stripped.hasFrom(AbilityKey.of(tag), AbilityGrants.Source.HERITAGE), tag);
        }
        assertEquals(HERITAGE_TAGS.size(), stripped.keys().size());
    }

    @Test
    void sharedKeyOutlivesLosingOneSource() {
        // A key granted by both heritage and skill node survives a refund via the heritage source.
        AbilityGrants after = AbilityGrants.of(List.of("dual"), List.of(), List.of());
        assertTrue(after.has(AbilityKey.of("dual")));
        assertTrue(after.hasFrom(AbilityKey.of("dual"), AbilityGrants.Source.HERITAGE));
        assertFalse(after.hasFrom(AbilityKey.of("dual"), AbilityGrants.Source.SKILL_NODE));
    }

    // ── Status source (replaces the heritage-tag grant path) ──

    /** Namespaced ids, exactly as {@code AbilityIds.*.toString()} and the resolver's key both spell them. */
    private static final String APPARITION = "wizards_and_beasts:apparition";
    private static final String LEGILIMENCY = "wizards_and_beasts:legilimency";
    private static final String ANIMAGUS_FORM = "wizards_and_beasts:animagus_form";

    @Test
    void statusSourceCarriesTheMigratedAbilityIds() {
        AbilityGrants g = AbilityGrants.ofSources(Map.of(
                AbilityGrants.Source.STATUS, List.of(APPARITION, LEGILIMENCY, ANIMAGUS_FORM)));

        for (String id : List.of(APPARITION, LEGILIMENCY, ANIMAGUS_FORM)) {
            assertTrue(g.hasFrom(AbilityKey.of(id), AbilityGrants.Source.STATUS), id);
        }
        assertEquals(3, g.keysFrom(AbilityGrants.Source.STATUS).size());
    }

    @Test
    void losingStatusStripsExactlyStatusGrants() {
        // Licence revoked / ritual state cleared → recompute yields nothing from STATUS, others untouched.
        AbilityGrants after = AbilityGrants.ofSources(Map.of(
                AbilityGrants.Source.STATUS, List.of(),
                AbilityGrants.Source.VOCATION, DUELIST,
                AbilityGrants.Source.SKILL_NODE, NODE_ABILITIES));

        assertFalse(after.has(AbilityKey.of(APPARITION)));
        assertTrue(after.hasFrom(AbilityKey.of("duelist_spell_power"), AbilityGrants.Source.VOCATION));
        assertTrue(after.hasFrom(AbilityKey.of("niffler_friend"), AbilityGrants.Source.SKILL_NODE));
    }

    /**
     * The migration's premise: heritage tags are bare strings and ability keys are namespaced ids, so no tag
     * has ever resolved to an ability. Locks that the two key spaces stay disjoint, so nobody "restores" the
     * tag grant path expecting it to work.
     */
    @Test
    void heritageTagsDoNotResolveToAbilityIds() {
        List<String> shippedTags = List.of("innate_apparition", "transformation", "no_wand", "nature_speech",
                "enhanced_bond", "dark_resistance", "obscurus_form", "divination_sight");
        AbilityGrants g = AbilityGrants.ofSources(Map.of(AbilityGrants.Source.HERITAGE, shippedTags));

        for (String id : List.of(APPARITION, LEGILIMENCY, ANIMAGUS_FORM)) {
            assertFalse(g.has(AbilityKey.of(id)), id + " must not come from a heritage tag");
        }
        // The closest tag by name is still a different key entirely.
        assertNotEquals(AbilityKey.of(APPARITION), AbilityKey.of("innate_apparition"));
    }

    // ── Key normalization + empties ──

    @Test
    void keysNormalizeCaseAndWhitespace() {
        assertEquals(AbilityKey.of("duelist_spell_power"), AbilityKey.of("  Duelist_Spell_Power "));
        AbilityGrants g = AbilityGrants.of(List.of("  NO_WAND  "), List.of(), List.of());
        assertTrue(g.has(AbilityKey.of("no_wand")));
    }

    @Test
    void blankInputsAreIgnored() {
        AbilityGrants g = AbilityGrants.of(List.of("", "  "), List.of(), List.of());
        assertTrue(g.isEmpty());
        assertEquals(AbilityGrants.EMPTY.keys().size(), g.keys().size());
    }

    @Test
    void missingKeyHasNoSources() {
        AbilityGrants g = AbilityGrants.of(HERITAGE_TAGS, DUELIST, NODE_ABILITIES);
        assertFalse(g.has(AbilityKey.of("nonexistent")));
        assertTrue(g.sourcesOf(AbilityKey.of("nonexistent")).isEmpty());
    }
}
