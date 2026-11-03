package at.koopro.wizardsandbeasts.settlement.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementGroundTest {

    @Test
    void everyColumnHasThePlate() {
        for (int x = -9; x <= 9; x++) {
            for (int z = -9; z <= 9; z++) {
                for (int depth = 1; depth <= SettlementGround.PLATE_DEPTH; depth++) {
                    assertTrue(SettlementGround.footed(x, z, depth, SettlementGround.NO_ROCK), x + "," + z);
                }
            }
        }
    }

    @Test
    void belowThePlateOnlyPiersGoOnSoAGapStaysAGap() {
        int piers = 0;
        int total = 0;
        for (int x = -32; x < 32; x++) {
            for (int z = -32; z < 32; z++) {
                total++;
                piers += SettlementGround.footed(x, z, SettlementGround.PLATE_DEPTH + 1, SettlementGround.PIER_REACH) ? 1 : 0;
            }
        }
        assertEquals(total / (SettlementGround.PIER_SPACING * SettlementGround.PIER_SPACING), piers);
    }

    @Test
    void aPierRunsDownToRockWithinReach() {
        int rock = SettlementGround.PIER_REACH;
        assertTrue(SettlementGround.footed(4, -8, rock - 1, rock));
        assertFalse(SettlementGround.footed(5, -8, rock - 1, rock));
    }

    @Test
    void noPierIsBegunOverAGapTooDeepToReach() {
        for (int rock : new int[] {SettlementGround.PIER_REACH + 1, 200, SettlementGround.NO_ROCK}) {
            assertTrue(SettlementGround.footed(4, -8, SettlementGround.PLATE_DEPTH, rock), "the plate still spans it");
            assertFalse(SettlementGround.footed(4, -8, SettlementGround.PLATE_DEPTH + 1, rock), "a pier hanging in the air");
        }
    }

    @Test
    void aPierCannotOutgrowItsReach() {
        assertTrue(SettlementGround.PIER_REACH <= 32, "a pier taller than the hamlet's own spire is no foundation");
        assertTrue(SettlementGround.PIER_REACH > SettlementGround.PLATE_DEPTH);
    }
}
