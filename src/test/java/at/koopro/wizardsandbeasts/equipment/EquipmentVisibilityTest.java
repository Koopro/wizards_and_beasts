package at.koopro.wizardsandbeasts.equipment;

import net.minecraft.world.entity.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The armour-hiding rule on its own, without a world or Curios.
 *
 * <p>The rule is small but it decides what every player looks like, and two of its distinctions are
 * easy to collapse by accident: covering comes from what is <em>drawn</em> and concealment from what
 * is <em>worn</em>, and the hood adds slots only while it is up.
 */
class EquipmentVisibilityTest {

    private static WearableDefinition def(EquipmentType type, Set<EquipmentSlot> hides,
                                          Set<EquipmentSlot> hood, boolean conceals) {
        return new WearableDefinition(type, type.defaultLayer(), hides, hood, conceals, Optional.empty(), List.of());
    }

    private static final WearableDefinition ROBE =
            def(EquipmentType.BODY_CLOTHING, Set.of(EquipmentSlot.CHEST), Set.of(EquipmentSlot.HEAD), false);
    private static final WearableDefinition HALLOW = def(EquipmentType.CLOAK, Set.of(), Set.of(), true);
    private static final WearableDefinition PLAIN_CLOAK = def(EquipmentType.CLOAK, Set.of(), Set.of(), false);

    @Test
    void nothingWornHidesNothingAndAllocatesNothing() {
        EquipmentVisibility.Result result = new EquipmentVisibility.Builder().build();
        assertSame(EquipmentVisibility.NONE, result);
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            assertTrue(result.isArmorVisible(slot));
        }
    }

    @Test
    void aDrawnRobeHidesOnlyTheChest() {
        EquipmentVisibility.Result result = new EquipmentVisibility.Builder().drawn(ROBE, false).build();
        assertFalse(result.isArmorVisible(EquipmentSlot.CHEST));
        assertTrue(result.isArmorVisible(EquipmentSlot.HEAD));
        assertTrue(result.isArmorVisible(EquipmentSlot.LEGS));
        assertFalse(result.concealed());
    }

    @Test
    void aRaisedHoodAlsoHidesTheHelmet() {
        EquipmentVisibility.Result result = new EquipmentVisibility.Builder().drawn(ROBE, true).build();
        assertFalse(result.isArmorVisible(EquipmentSlot.HEAD));
        assertFalse(result.isArmorVisible(EquipmentSlot.CHEST));
    }

    @Test
    void aRobeThatIsWornButNotDrawnCoversNothing() {
        // Render toggle off: the robe still counts for gameplay but must not leave a missing chestplate.
        EquipmentVisibility.Result result = new EquipmentVisibility.Builder().worn(ROBE).build();
        assertTrue(result.isArmorVisible(EquipmentSlot.CHEST));
    }

    @Test
    void concealmentComesFromWhatIsWornNotWhatIsDrawn() {
        assertTrue(new EquipmentVisibility.Builder().worn(HALLOW).build().concealed());
        assertFalse(new EquipmentVisibility.Builder().drawn(HALLOW, false).build().concealed(),
                "a cosmetic Hallow must not grant real concealment");
    }

    @Test
    void concealmentHidesEveryArmourSlot() {
        EquipmentVisibility.Result result = new EquipmentVisibility.Builder().worn(HALLOW).build();
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            assertFalse(result.isArmorVisible(slot), slot.getName());
        }
    }

    @Test
    void anOrdinaryCloakNeitherCoversNorConceals() {
        EquipmentVisibility.Result result = new EquipmentVisibility.Builder()
                .drawn(PLAIN_CLOAK, false).worn(PLAIN_CLOAK).build();
        assertSame(EquipmentVisibility.NONE, result);
    }
}
