package at.koopro.wizardsandbeasts.trunk;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every pocket the mod creates is SEALED, so the sneak-cast "shared entrance" admitted nobody: the invited ally
 * was refused by the same access check as a stranger. The owner's invitation is the grant; a plain entry still
 * is not.
 */
class SharedDoorwayAccessTest {

    private static TrunkRecord sealedPocket(UUID owner) {
        return TrunkRecord.create(owner, PocketArchetype.FIELD_CAMP, "trunk_lock_1", BlockPos.ZERO, 12)
                .withAccessMode(TrunkAccessMode.SEALED);
    }

    @Test
    void anInvitedAllyMayEnterASealedPocket() {
        TrunkRecord pocket = sealedPocket(UUID.randomUUID());
        assertTrue(pocket.mayEnter(UUID.randomUUID(), true));
    }

    @Test
    void aStrangerWithoutAnInvitationStaysOut() {
        TrunkRecord pocket = sealedPocket(UUID.randomUUID());
        assertFalse(pocket.mayEnter(UUID.randomUUID(), false));
        assertFalse(pocket.canAccess(UUID.randomUUID()));
    }

    @Test
    void theOwnerAlwaysEnters() {
        UUID owner = UUID.randomUUID();
        assertTrue(sealedPocket(owner).mayEnter(owner, false));
    }
}
