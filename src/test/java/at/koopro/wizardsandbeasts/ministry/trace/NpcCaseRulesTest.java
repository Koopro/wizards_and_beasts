package at.koopro.wizardsandbeasts.ministry.trace;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a posted case's culprit hides, what a witness says, and which cases an Auror is trusted with. */
class NpcCaseRulesTest {

    @AfterEach
    void clearTemplates() {
        NpcCaseTemplates.replaceAll(Map.of());
    }

    @Test
    void theCulpritHidesWithinTheTrailsReachWhateverTheRoll() {
        BlockPos scene = new BlockPos(100, 70, -40);
        for (int i = 0; i < 64; i++) {
            double angle = i * Math.PI / 32.0;
            for (int asked : new int[] {0, 30, 1000}) {
                double distance = Math.sqrt(scene.distSqr(NpcCaseRules.hideout(scene, angle, asked)));
                assertTrue(distance >= NpcCaseRules.HIDEOUT_MIN - 1 && distance <= NpcCaseRules.HIDEOUT_MAX + 1,
                        "hideout " + distance + " blocks off");
            }
        }
    }

    @Test
    void bearingsFollowMinecraftsCompass() {
        assertEquals("north", NpcCaseRules.bearing(0, -10));
        assertEquals("south", NpcCaseRules.bearing(0, 10));
        assertEquals("east", NpcCaseRules.bearing(10, 0));
        assertEquals("west", NpcCaseRules.bearing(-10, 0));
        assertEquals("north_east", NpcCaseRules.bearing(10, -10));
        assertEquals("south_west", NpcCaseRules.bearing(-10, 10));
        assertEquals("here", NpcCaseRules.bearing(0, 0));
    }

    @Test
    void witnessesSpeakInRoughDistances() {
        assertEquals("close", NpcCaseRules.distanceBand(10));
        assertEquals("near", NpcCaseRules.distanceBand(50));
        assertEquals("far", NpcCaseRules.distanceBand(200));
        assertEquals("distant", NpcCaseRules.distanceBand(1000));
    }

    @Test
    void aTraineeIsNeverDrawnACaseAboveTheirRank() {
        Identifier easy = Identifier.fromNamespaceAndPath("wizards_and_beasts", "easy");
        Identifier hard = Identifier.fromNamespaceAndPath("wizards_and_beasts", "hard");
        NpcCaseTemplates.replaceAll(Map.of(
                easy, new NpcCaseTemplates.Template(Identifier.withDefaultNamespace("witch"), "confundo",
                        LegalClass.RESTRICTED, 290L, 0, 10),
                hard, new NpcCaseTemplates.Template(Identifier.withDefaultNamespace("illusioner"), "imperio",
                        LegalClass.UNFORGIVABLE, 2465L, 2, 10)));
        RandomSource random = RandomSource.create(42L);
        Set<Identifier> drawn = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            drawn.add(NpcCaseTemplates.draw(random, 0).getKey());
        }
        assertEquals(Set.of(easy), drawn);
        Set<Identifier> senior = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            senior.add(NpcCaseTemplates.draw(random, 2).getKey());
        }
        assertEquals(Set.of(easy, hard), senior);
    }

    @Test
    void anEmptyOfficePostsNothing() {
        assertNull(NpcCaseTemplates.draw(RandomSource.create(1L), 3));
    }

    @Test
    void aSceneExaminedAndItsTrailFollowedIdentifiesTheCulpritWithoutAWitness() {
        long id = 9L;
        var evidence = java.util.List.of(
                Evidence.of(EvidenceKind.SCENE_RESIDUE, id, 0L),
                Evidence.of(EvidenceKind.RESIDUE_TRAIL, id, 0L));
        assertTrue(EvidenceRules.identifies(evidence));
        assertTrue(!EvidenceRules.identifies(java.util.List.of(Evidence.of(EvidenceKind.SCENE_RESIDUE, id, 0L))),
                "the scene alone does not name anyone");
        assertNotNull(evidence);
    }
}
