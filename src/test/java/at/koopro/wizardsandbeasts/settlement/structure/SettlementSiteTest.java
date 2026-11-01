package at.koopro.wizardsandbeasts.settlement.structure;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementSiteTest {

    @Test
    void levelDryGroundInAListedBiomeSuits() {
        assertTrue(SettlementSite.suits(true, false, 70, 70 + SettlementSite.MAX_RELIEF));
    }

    @Test
    void groundOneBlockTooUnevenIsRefused() {
        assertFalse(SettlementSite.suits(true, false, 70, 71 + SettlementSite.MAX_RELIEF));
    }

    @Test
    void waterOrAnUnlistedBiomeIsRefusedHoweverLevel() {
        assertFalse(SettlementSite.suits(true, true, 70, 70));
        assertFalse(SettlementSite.suits(false, false, 70, 70));
    }

    @Test
    void aHamletMissingAnyRequiredBuildingIsIncomplete() {
        assertTrue(SettlementSite.complete(SettlementSite.REQUIRED_ROLES));
        for (String missing : SettlementSite.REQUIRED_ROLES) {
            List<String> roles = new ArrayList<>(SettlementSite.REQUIRED_ROLES);
            roles.remove(missing);
            assertFalse(SettlementSite.complete(roles), missing);
        }
    }

    @Test
    void anExtraBuildingDoesNotMakeASiteIncomplete() {
        List<String> roles = new ArrayList<>(SettlementSite.REQUIRED_ROLES);
        roles.add("general_store");
        assertTrue(SettlementSite.complete(roles));
    }

    @Test
    void aTemplatesRoleIsTheFolderItSitsIn() {
        assertEquals("bank", SettlementSite.roleOf("wizarding_hamlet/bank/greybox"));
        assertEquals("", SettlementSite.roleOf("chamber"));
    }

    @Test
    void groundIsSampledOnEveryArmWhicheverWayThePlazaTurns() {
        for (int[] sample : SettlementSite.SAMPLES) {
            boolean mirrored = SettlementSite.SAMPLES.stream().anyMatch(o -> o[0] == sample[1] && o[1] == -sample[0]);
            assertTrue(mirrored, sample[0] + "," + sample[1] + " has no quarter-turned partner");
        }
    }
}
