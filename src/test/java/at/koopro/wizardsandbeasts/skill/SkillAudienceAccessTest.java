package at.koopro.wizardsandbeasts.skill;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the audience/access ruling: audience = tradition of origin (variant-aware), capability tags
 * gate content inside a web, and the per-audience point cap ships as a zero-delta hook.
 */
class SkillAudienceAccessTest {

    // ── Totality: every (heritage, variant) resolves, no default branch ──

    @Test
    void everyVariantResolvesToAnAudience() {
        for (HeritageVariant variant : HeritageVariant.values()) {
            assertNotNull(SkillTreeId.audienceForVariant(variant),
                    "variant " + variant + " has no audience");
        }
    }

    @Test
    void everyHeritageFallbackResolves() {
        for (Heritage heritage : Heritage.values()) {
            assertNotNull(SkillTreeId.audienceForHeritage(heritage, null),
                    "heritage " + heritage + " has no fallback audience");
        }
    }

    // ── The ruling, verbatim ──

    @Test
    void wizardAudienceHeritages() {
        // Wizardkind incl. squib.
        assertEquals(SkillTreeId.Audience.WIZARD, SkillTreeId.audienceForVariant(HeritageVariant.PURE_BLOOD));
        assertEquals(SkillTreeId.Audience.WIZARD, SkillTreeId.audienceForVariant(HeritageVariant.SQUIB));
        // Werewolf, obscurial, vampire.
        assertEquals(SkillTreeId.Audience.WIZARD, SkillTreeId.audienceForVariant(HeritageVariant.WEREWOLF_BITTEN));
        assertEquals(SkillTreeId.Audience.WIZARD, SkillTreeId.audienceForVariant(HeritageVariant.SUPPRESSED));
        assertEquals(SkillTreeId.Audience.WIZARD, SkillTreeId.audienceForVariant(HeritageVariant.UNLEASHED));
        assertEquals(SkillTreeId.Audience.WIZARD, SkillTreeId.audienceForVariant(HeritageVariant.VAMPIRE_TURNED));
        assertEquals(SkillTreeId.Audience.WIZARD, SkillTreeId.audienceForVariant(HeritageVariant.VAMPIRE_BORN));
        assertEquals(SkillTreeId.Audience.WIZARD, SkillTreeId.audienceForVariant(HeritageVariant.VAMPIRE_DHAMPIR));
    }

    @Test
    void veelaSplitsByVariant() {
        assertEquals(SkillTreeId.Audience.VEELA, SkillTreeId.audienceForVariant(HeritageVariant.VEELA_FULL));
        assertEquals(SkillTreeId.Audience.WIZARD, SkillTreeId.audienceForVariant(HeritageVariant.VEELA_HALF));
        assertEquals(SkillTreeId.Audience.WIZARD, SkillTreeId.audienceForVariant(HeritageVariant.VEELA_QUARTER));
    }

    @Test
    void giantSplitsByLineage() {
        // Only half-giant is the mixed (human) lineage → WIZARD; full-giant and clan-warden are pure → GIANT.
        assertEquals(SkillTreeId.Audience.WIZARD, SkillTreeId.audienceForVariant(HeritageVariant.GIANT_HALF));
        assertEquals(SkillTreeId.Audience.GIANT, SkillTreeId.audienceForVariant(HeritageVariant.GIANT_FULL));
        assertEquals(SkillTreeId.Audience.GIANT, SkillTreeId.audienceForVariant(HeritageVariant.GIANT_CLAN));
    }

    @Test
    void ownTraditionAudiences() {
        assertEquals(SkillTreeId.Audience.GOBLIN, SkillTreeId.audienceForVariant(HeritageVariant.GOBLIN_COMMON));
        assertEquals(SkillTreeId.Audience.HOUSE_ELF, SkillTreeId.audienceForVariant(HeritageVariant.ELF_BOUND));
        assertEquals(SkillTreeId.Audience.CENTAUR, SkillTreeId.audienceForVariant(HeritageVariant.CENTAUR_FOREST));
        assertEquals(SkillTreeId.Audience.MERPEOPLE, SkillTreeId.audienceForVariant(HeritageVariant.MERPEOPLE_MERROW));
    }

    // ── Region requirement mapping ──

    @Test
    void regionRequirementsMatchTheRuling() {
        assertEquals(SkillTreeId.Requirement.WAND, SkillTreeId.WANDLORE.getRequirement());
        assertEquals(SkillTreeId.Requirement.CASTING, SkillTreeId.SPELL_MASTERY.getRequirement());
        assertEquals(SkillTreeId.Requirement.CASTING, SkillTreeId.DARK_ARTS.getRequirement());
        assertEquals(SkillTreeId.Requirement.NONE, SkillTreeId.MAGIZOOLOGY.getRequirement());
        assertEquals(SkillTreeId.Requirement.NONE, SkillTreeId.HERBOLOGY.getRequirement());
        assertEquals(SkillTreeId.Requirement.NONE, SkillTreeId.ALCHEMY.getRequirement());
        assertEquals(SkillTreeId.Requirement.NONE, SkillTreeId.GOBLIN_CRAFT.getRequirement());
        assertEquals(SkillTreeId.Requirement.NONE, SkillTreeId.ELF_BOND.getRequirement());
    }

    // ── Capability gating: obscurial + squib access profile ──

    @Test
    void obscurialSealsWandAndCastingButNotOpenRegions() {
        Heritage h = Heritage.OBSCURIAL;
        HeritageVariant v = HeritageVariant.SUPPRESSED;
        assertFalse(SkillTreeId.meetsRequirement(SkillTreeId.Requirement.WAND, h, v),
                "obscurial cannot use a wand → wandlore sealed");
        assertFalse(SkillTreeId.meetsRequirement(SkillTreeId.Requirement.CASTING, h, v),
                "obscurial has no_casting → spell_mastery/dark_arts sealed");
        assertTrue(SkillTreeId.meetsRequirement(SkillTreeId.Requirement.NONE, h, v),
                "open regions stay allocatable");
    }

    @Test
    void squibHasSameAccessProfileAsObscurial() {
        Heritage h = Heritage.WIZARDKIND;
        HeritageVariant v = HeritageVariant.SQUIB;
        assertEquals(SkillTreeId.Audience.WIZARD, SkillTreeId.audienceForVariant(v));
        assertFalse(SkillTreeId.meetsRequirement(SkillTreeId.Requirement.WAND, h, v));
        assertFalse(SkillTreeId.meetsRequirement(SkillTreeId.Requirement.CASTING, h, v));
        assertTrue(SkillTreeId.meetsRequirement(SkillTreeId.Requirement.NONE, h, v));
    }

    @Test
    void ordinaryWizardsSatisfyEveryRegion() {
        Heritage h = Heritage.WIZARDKIND;
        HeritageVariant v = HeritageVariant.PURE_BLOOD;
        assertTrue(SkillTreeId.meetsRequirement(SkillTreeId.Requirement.WAND, h, v));
        assertTrue(SkillTreeId.meetsRequirement(SkillTreeId.Requirement.CASTING, h, v));
        assertTrue(SkillTreeId.meetsRequirement(SkillTreeId.Requirement.NONE, h, v));
    }

    @Test
    void werewolfAndVampireAndHalfVeelaKeepFullWizardWeb() {
        for (HeritageVariant v : new HeritageVariant[]{
                HeritageVariant.WEREWOLF_BITTEN, HeritageVariant.VAMPIRE_TURNED, HeritageVariant.VEELA_HALF}) {
            Heritage h = v.getParentHeritage();
            assertTrue(SkillTreeId.meetsRequirement(SkillTreeId.Requirement.CASTING, h, v),
                    v + " should keep casting regions");
            assertTrue(SkillTreeId.meetsRequirement(SkillTreeId.Requirement.WAND, h, v),
                    v + " should keep wandlore");
        }
    }

    // ── Per-audience cap hook is a no-op today ──

    @Test
    void perAudienceCapIsUniformSixty() {
        for (SkillTreeId.Audience audience : SkillTreeId.Audience.values()) {
            assertEquals(SkillSystemAPI.MAX_SKILL_POINTS, SkillSystemAPI.pointCapFor(audience),
                    "audience " + audience + " cap must equal MAX_SKILL_POINTS (zero behavior delta)");
        }
    }
}
