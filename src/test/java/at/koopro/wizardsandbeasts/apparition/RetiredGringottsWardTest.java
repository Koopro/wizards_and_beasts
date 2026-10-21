package at.koopro.wizardsandbeasts.apparition;

import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The old "gringotts_vaults" default warded the Overworld origin against Apparition (WIZARD_LIFE_AUDIT P0-1). Its
 * saved copy is dropped on load — but only the exact default; a ward an operator moved or resized is theirs.
 */
class RetiredGringottsWardTest {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("wizards_and_beasts", "gringotts_vaults");
    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theExactOriginDefaultIsRetired() {
        assertTrue(ApparitionWardRegistry.isRetiredDefault(ID, OVERWORLD, new AABB(-50, 0, -50, 50, 80, 50)));
    }

    @Test
    void anOperatorsWardIsKept() {
        assertFalse(ApparitionWardRegistry.isRetiredDefault(ID, OVERWORLD, new AABB(950, 0, -50, 1050, 80, 50)));
        assertFalse(ApparitionWardRegistry.isRetiredDefault(
                Identifier.fromNamespaceAndPath("wizards_and_beasts", "my_bank"), OVERWORLD,
                new AABB(-50, 0, -50, 50, 80, 50)));
        assertFalse(ApparitionWardRegistry.isRetiredDefault(ID, Identifier.withDefaultNamespace("the_nether"),
                new AABB(-50, 0, -50, 50, 80, 50)));
    }
}
