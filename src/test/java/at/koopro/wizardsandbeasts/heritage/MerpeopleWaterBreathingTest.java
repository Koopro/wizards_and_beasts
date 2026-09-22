package at.koopro.wizardsandbeasts.heritage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Merpeople drowned in their own element.
 *
 * <p>{@code MerpeopleHeritageHandler} swapped a submerged Merperson into {@code merfolk_water} and
 * did nothing else, and {@code water_dwelling} was read by no code at all — so a Merrow could stand
 * in the lake it lives in and suffocate, while a wizard chewing Gillyweed breathed fine. The fix
 * hangs {@link at.koopro.wizardsandbeasts.form.sense.FormSense#WATER_BREATHING} off that trait.
 *
 * <p>These are the data-side guards for it. The trait is the hinge the whole grant turns on, so a
 * lineage silently losing it would put the drowning back without any code changing; and the grant is
 * keyed to the trait rather than to the merfolk form precisely because two of the three lineages
 * never change form at all. The behavioural half — that the effect actually lands, survives a
 * revert, and is not handed to anyone else — is covered by {@code MerpeopleBreathTests}.
 */
class MerpeopleWaterBreathingTest {

    private static final String TRAIT = "water_dwelling";

    @Test
    void everyMerpeopleLineageIsAtHomeInWater() {
        List<String> landlocked = new ArrayList<>();
        for (HeritageVariant variant : HeritageVariant.values()) {
            if (variant.getParentHeritage() != Heritage.MERPEOPLE) {
                continue;
            }
            if (!variant.hasTag(TRAIT)) {
                landlocked.add(variant.getId());
            }
        }
        assertTrue(landlocked.isEmpty(),
                "every Merpeople lineage must carry '" + TRAIT + "' or it drowns: " + landlocked);
    }

    /**
     * The trait must not spread. Water breathing is granted purely on its presence, so a lineage that
     * picked it up for flavour would quietly become amphibious.
     */
    @Test
    void nobodyElseCarriesTheWaterDwellingTrait() {
        List<String> unexpected = new ArrayList<>();
        for (HeritageVariant variant : HeritageVariant.values()) {
            if (variant.getParentHeritage() == Heritage.MERPEOPLE) {
                continue;
            }
            if (variant.hasTag(TRAIT)) {
                unexpected.add(variant.getParentHeritage().getId() + "/" + variant.getId());
            }
        }
        assertTrue(unexpected.isEmpty(),
                "'" + TRAIT + "' grants water breathing, so only Merpeople may carry it: " + unexpected);
    }

    /**
     * Two of the three lineages have no form to change into, which is the whole reason the grant
     * keys on the trait rather than on {@code merfolk_water}.
     */
    @Test
    void mostMerpeopleNeverChangeShapeAtAll() {
        List<String> shapeshifters = new ArrayList<>();
        for (HeritageVariant variant : HeritageVariant.values()) {
            if (variant.getParentHeritage() == Heritage.MERPEOPLE
                    && variant.hasTag(HeritageTransformService.TAG_TRANSFORMATION)) {
                shapeshifters.add(variant.getId());
            }
        }
        assertFalse(shapeshifters.isEmpty(),
                "the Selkie should still be able to change at the waterline");
        assertTrue(shapeshifters.size() < 3,
                "a form-gated grant would have left the non-shifting lineages drowning;"
                        + " lineages that transform: " + shapeshifters);
    }
}
