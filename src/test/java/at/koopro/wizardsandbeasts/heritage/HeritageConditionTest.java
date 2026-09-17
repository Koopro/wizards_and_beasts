package at.koopro.wizardsandbeasts.heritage;

import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lycanthropy and the Obscurus as conditions rather than peoples.
 *
 * <p>The thing worth pinning is the separation: a character who carries a condition still <em>is</em> their heritage
 * and their lineage, keeps their POWER roll and their training, and gains the condition's traits on top. The old model
 * replaced all of it, which is how a bitten half-blood stopped being a wizard of any family at all.
 */
class HeritageConditionTest {

    private static PlayerHeritageData wizard(HeritageVariant lineage) {
        PlayerHeritageData data = new PlayerHeritageData();
        data.setSelectedHeritage(Heritage.WIZARDKIND);
        data.setSelectedHeritageVariant(lineage);
        return data;
    }

    @Test
    void werewolfAndObscurialAreNotHeritages() {
        assertNull(Heritage.byId("werewolf"),
                "lycanthropy is a curse a wizard carries, not a people to be born among");
        assertNull(Heritage.byId("obscurial"), "an Obscurus grows in a witch or wizard; it is not a heritage");
        for (HeritageVariant variant : HeritageVariant.values()) {
            assertNotNull(variant.getParentHeritage(), variant.getId() + " has no parent heritage");
        }
    }

    @Test
    void aBittenWizardKeepsTheirLineageAndGainsTheConditionsTraits() {
        PlayerHeritageData data = wizard(HeritageVariant.MUGGLE_BORN);
        data.setCondition(ConditionOrigin.BITTEN);

        assertEquals(Heritage.WIZARDKIND, data.getSelectedHeritage());
        assertEquals(HeritageVariant.MUGGLE_BORN, data.getSelectedHeritageVariant());
        assertTrue(data.hasCondition(MagicalCondition.LYCANTHROPY));
        assertFalse(data.hasCondition(MagicalCondition.OBSCURUS));

        assertTrue(data.hasTrait("moon_sensitive"), "the moon has a claim on them now");
        assertTrue(data.hasTrait("transformation"));
        assertTrue(data.hasTrait("muggle_raised"), "and they are still who they were");
        assertTrue(data.canUseWand(), "Remus Lupin taught Defence Against the Dark Arts with a wand");
        assertTrue(data.canCast());
    }

    @Test
    void anObscurusSealsWandworkWithoutChangingWhoTheyAre() {
        PlayerHeritageData data = wizard(HeritageVariant.PURE_BLOOD);
        assertTrue(data.canCast());
        assertTrue(data.canUseWand());

        data.setCondition(ConditionOrigin.SUPPRESSED);
        assertFalse(data.canCast(), "an Obscurus leaves nothing to direct");
        assertFalse(data.canUseWand());
        assertEquals(HeritageVariant.PURE_BLOOD, data.getSelectedHeritageVariant());
        assertTrue(data.hasTrait("old_family"), "the family they came from did not change");
    }

    @Test
    void conditionsBelongToWitchesAndWizards() {
        assertTrue(MagicalCondition.LYCANTHROPY.canBeCarriedBy(Heritage.WIZARDKIND, HeritageVariant.SQUIB),
                "a bite is a curse of the body; a Squib has a body");
        assertFalse(MagicalCondition.OBSCURUS.canBeCarriedBy(Heritage.WIZARDKIND, HeritageVariant.SQUIB),
                "an Obscurus is suppressed magic, and a Squib has none to suppress");
        assertFalse(MagicalCondition.LYCANTHROPY.canBeCarriedBy(Heritage.GOBLIN, HeritageVariant.GOBLIN_COMMON));
        assertFalse(MagicalCondition.OBSCURUS.canBeCarriedBy(null, null));
    }

    @Test
    void everyConditionHasOriginsAndEveryOriginBelongsToOne() {
        for (MagicalCondition condition : MagicalCondition.values()) {
            List<ConditionOrigin> origins = condition.origins();
            assertFalse(origins.isEmpty(), condition.getId() + " has no way of coming about");
            for (ConditionOrigin origin : origins) {
                assertEquals(condition, origin.condition());
                assertEquals(origin, ConditionOrigin.byId(condition, origin.getId()));
                assertFalse(origin.traits().isEmpty(), origin.getId() + " changes nothing about the character");
            }
        }
        assertNull(ConditionOrigin.byId(MagicalCondition.OBSCURUS, "bitten"),
                "an origin belongs to its own condition and no other");
        assertNull(ConditionOrigin.byId(MagicalCondition.LYCANTHROPY, null));
    }

    @Test
    void aConditionSurvivesASaveAndALoad() {
        PlayerHeritageData saved = wizard(HeritageVariant.HALF_BLOOD);
        saved.setCondition(ConditionOrigin.SAVAGE_BITTEN);

        CompoundTag tag = saved.save();

        PlayerHeritageData loaded = new PlayerHeritageData();
        loaded.load(tag);

        assertEquals(ConditionOrigin.SAVAGE_BITTEN, loaded.getCondition());
        assertEquals(Heritage.WIZARDKIND, loaded.getSelectedHeritage());
        assertEquals(HeritageVariant.HALF_BLOOD, loaded.getSelectedHeritageVariant());
    }

    @Test
    void resetClearsTheCondition() {
        PlayerHeritageData data = wizard(HeritageVariant.HALF_BLOOD);
        data.setCondition(ConditionOrigin.BITTEN);
        data.reset();
        assertNull(data.getCondition(), "a cleared character carries nothing forward");
        assertFalse(data.hasTrait("moon_sensitive"));
    }
}
