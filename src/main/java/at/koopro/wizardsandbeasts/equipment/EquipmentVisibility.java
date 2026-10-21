package at.koopro.wizardsandbeasts.equipment;

import at.koopro.wizardsandbeasts.equipment.curios.CuriosEquipment;
import at.koopro.wizardsandbeasts.item.armor.RobeHood;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NullMarked;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which vanilla armour pieces are drawn on a wearer this frame. A <b>visual</b> question only.
 *
 * <p>Hiding never touches the equipment: the chestplate under a robe stays in
 * {@link EquipmentSlot#CHEST}, keeps its armour, toughness, enchantments and durability, and is
 * simply left out of that frame's render state by the client
 * ({@code client.equipment.EquipmentRenderStateModifier}). Nothing here calls {@code setItemSlot}.
 *
 * <p>Two inputs, deliberately read from different views of the Curios inventory:
 * <ul>
 *   <li><b>Covering</b> comes from what is <em>drawn</em> — a cosmetic robe hides armour, a robe whose
 *       render toggle is off does not, because a hidden robe covering a visible chestplate would just
 *       be a missing chestplate.</li>
 *   <li><b>Concealment</b> (the Deathly Hallow) comes from what is <em>worn</em>, because it is the
 *       cloak's magic, not its looks: toggling the cloak's render off must not unhide its wearer.</li>
 * </ul>
 */
@NullMarked
public final class EquipmentVisibility {

    /** Nothing hidden, nothing concealed — shared so the common case allocates nothing. */
    public static final Result NONE = new Result(Set.of(), false);

    private EquipmentVisibility() {}

    /**
     * The outcome for one wearer.
     *
     * @param hiddenArmor armour slots covered by a drawn piece
     * @param concealed   the wearer's whole outfit is withheld from rendering
     */
    public record Result(Set<EquipmentSlot> hiddenArmor, boolean concealed) {

        public boolean isArmorVisible(EquipmentSlot slot) {
            return !concealed && !hiddenArmor.contains(slot);
        }
    }

    /** Accumulates pieces into a {@link Result}. Pure — the unit tests drive it directly. */
    public static final class Builder {
        private final EnumSet<EquipmentSlot> hidden = EnumSet.noneOf(EquipmentSlot.class);
        private boolean concealed;

        /** A piece that is drawn on the wearer. */
        public Builder drawn(WearableDefinition definition, boolean hoodUp) {
            hidden.addAll(definition.hiddenSlots(hoodUp));
            return this;
        }

        /** A piece that is worn for real (gameplay slot, not cosmetic). */
        public Builder worn(WearableDefinition definition) {
            concealed |= definition.concealsWearer();
            return this;
        }

        public Result build() {
            return hidden.isEmpty() && !concealed ? NONE : new Result(hidden, concealed);
        }
    }

    /** Resolves a wearer from their Curios inventory. Works on either side; the client uses it. */
    public static Result resolve(LivingEntity wearer) {
        Builder builder = new Builder();
        CuriosEquipment.forEachRendered(wearer, (slotId, index, stack) -> {
            WearableDefinition definition = WearableDefinitions.get(stack);
            if (definition != null) {
                builder.drawn(definition, RobeHood.isUp(stack));
            }
        });
        CuriosEquipment.forEachWorn(wearer, (slotId, index, stack) -> {
            WearableDefinition definition = WearableDefinitions.get(stack);
            if (definition != null) {
                builder.worn(definition);
            }
        });
        return builder.build();
    }

    /** {@code isArmorVisible(player, slot)} — the one-off form of {@link #resolve}. */
    public static boolean isArmorVisible(LivingEntity wearer, EquipmentSlot slot) {
        return resolve(wearer).isArmorVisible(slot);
    }
}
