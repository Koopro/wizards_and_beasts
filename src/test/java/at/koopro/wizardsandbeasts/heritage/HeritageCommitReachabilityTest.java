package at.koopro.wizardsandbeasts.heritage;

import at.koopro.wizardsandbeasts.form.FormRegistry;
import at.koopro.wizardsandbeasts.form.PlayerForm;
import at.koopro.wizardsandbeasts.form.SizeProfile;
import at.koopro.wizardsandbeasts.form.SizeProfileRegistry;
import at.koopro.wizardsandbeasts.stats.PowerBandTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the four data lookups {@code HeritageAPI.commit} makes on the way through.
 *
 * <p>{@code HeritageGateReachabilityTest} covers whether the gate can be <em>completed</em>; this covers
 * whether completing it produces a whole character. Commit rolls POWER against
 * {@link PowerBandTable}, then hands {@code FormSystemAPI.resetToDefault} a form id from
 * {@link HeritageFormBridge}, which is looked up in {@link FormRegistry} and whose size profile is looked
 * up in {@link SizeProfileRegistry}. Every one of those lookups fails <b>silently</b>:
 * {@code setPlayerForm} returns null for an unknown form and changes nothing, and
 * {@code SizeProfileRegistry.getOrDefault} substitutes a standard-player profile for a missing one. A
 * Giant committing to a form id nobody registered would be a normal-sized human with no error anywhere.
 */
class HeritageCommitReachabilityTest {

    /**
     * Both transformation states, because a werewolf, an Obscurial, a Veela, a vampire and merpeople each
     * resolve to a different form id depending on which one they are in, and the transformed half is only
     * reached long after the gate.
     */
    private static final TransformationState[] STATES = {
        TransformationState.NORMAL, TransformationState.TRANSFORMED
    };

    @Test
    void everyHeritageAndLineageResolvesToARegisteredForm() {
        for (Heritage heritage : Heritage.values()) {
            for (HeritageVariant variant : heritage.getSubtypes()) {
                for (TransformationState state : STATES) {
                    String formId = HeritageFormBridge.getDefaultFormId(heritage, variant, state);
                    assertNotNull(formId,
                            heritage.getId() + "/" + variant.getId() + " in " + state + " has no default form");
                    PlayerForm form = FormRegistry.get(formId);
                    assertNotNull(form,
                            heritage.getId() + "/" + variant.getId() + " in " + state + " wants form '"
                                    + formId + "', which is not registered — setPlayerForm returns null and "
                                    + "the commit silently leaves the player in their previous body");
                }
            }
        }
    }

    @Test
    void everyDefaultFormHasItsOwnSizeProfile() {
        for (Heritage heritage : Heritage.values()) {
            for (HeritageVariant variant : heritage.getSubtypes()) {
                for (TransformationState state : STATES) {
                    PlayerForm form = FormRegistry.get(
                            HeritageFormBridge.getDefaultFormId(heritage, variant, state));
                    assertNotNull(form);
                    SizeProfile profile = SizeProfileRegistry.getOrDefault(form.sizeProfileId());
                    assertNotSame(SizeProfile.DEFAULT, profile,
                            form.formId() + " asks for size profile '" + form.sizeProfileId()
                                    + "', which is not registered — getOrDefault substitutes the standard "
                                    + "player build, so an authored heritage renders human-sized in silence");
                }
            }
        }
    }

    /**
     * A commit rolls into {@code [bandMin, bandMax]} and the character sheet draws the cap tick at
     * {@code bandMax}; a band whose ends crossed would make {@code nextIntBetweenInclusive} throw at the
     * one moment a new player cannot retry.
     */
    @Test
    void everyLineageHasAUsablePowerBand() {
        for (Heritage heritage : Heritage.values()) {
            for (HeritageVariant variant : heritage.getSubtypes()) {
                int min = PowerBandTable.getBandMin(variant);
                int max = PowerBandTable.getBandMax(variant);
                assertTrue(min <= max, variant.getId() + " has an inverted Power band " + min + "–" + max);
                assertTrue(min >= 0 && max <= 100,
                        variant.getId() + " Power band " + min + "–" + max + " leaves the 0–100 stat range");
                assertTrue(PowerBandTable.getGrowthCap(variant) >= 0,
                        variant.getId() + " has a negative growth cap");
            }
        }
    }

    /**
     * The one lineage the commit path treats specially: {@code getDefaultFormId} distinguishes Giants by
     * the literal id {@code "full_giant"}, so renaming it would quietly demote every Full Giant to the
     * half-giant body rather than failing.
     */
    @Test
    void theFullGiantLineageIdTheFormBridgeMatchesOnStillExists() {
        boolean present = Heritage.GIANT.getSubtypes().stream()
                .anyMatch(variant -> "full_giant".equals(variant.getId()));
        assertTrue(present,
                "HeritageFormBridge matches the Giant body on the literal lineage id 'full_giant'; "
                        + "with it gone every Giant silently resolves to half_giant_default");
    }
}
