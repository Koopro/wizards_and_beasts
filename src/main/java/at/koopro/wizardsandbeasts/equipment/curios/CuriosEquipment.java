package at.koopro.wizardsandbeasts.equipment.curios;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.equipment.EquipmentType;
import at.koopro.wizardsandbeasts.equipment.WearableDefinition;
import at.koopro.wizardsandbeasts.equipment.WearableDefinitions;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosSlotTypes;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The Curios adapter: the only common-side class that touches the Curios API.
 *
 * <p>Everything above it — {@link at.koopro.wizardsandbeasts.equipment.EquipmentAPI}, the cloak
 * handler, the werewolf strip, the migration — speaks in {@link EquipmentType}s and item stacks. If
 * Curios changes shape, this file and {@code client/equipment/curios/} are the blast radius.
 *
 * <p>Reads go straight to the live stack handlers; there is no copy and no cache, so a server-side
 * read is exactly as authoritative as Curios itself.
 */
@NullMarked
public final class CuriosEquipment {

    /**
     * Slot validator named by every W&amp;B slot file
     * ({@code data/wizards_and_beasts/curios/slots/*.json}): a stack fits a slot iff its wearable
     * definition's type is stored in that slot. That is the whole of slot validation — a new robe
     * needs a data-map entry and nothing in this class.
     */
    public static final Identifier WEARABLE_VALIDATOR =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "wearable");

    /** Visits one occupied slot. */
    @FunctionalInterface
    public interface SlotVisitor {
        void visit(String slotId, int index, ItemStack stack);
    }

    private CuriosEquipment() {}

    /** Common-setup hook: registers {@link #WEARABLE_VALIDATOR}. */
    public static void registerValidator() {
        CuriosSlotTypes.registerPredicate(WEARABLE_VALIDATOR, (context, stack) -> {
            WearableDefinition definition = WearableDefinitions.get(stack);
            return definition != null && definition.type().slotId().equals(context.identifier());
        });
    }

    /** Whether this entity carries a Curios inventory at all (players do; most mobs do not). */
    public static boolean hasInventory(LivingEntity entity) {
        return CuriosApi.getCuriosInventoryOrNull(entity) != null;
    }

    /** Number of slots of this id the entity has — the ring count, for example. */
    public static int slotCount(LivingEntity entity, String slotId) {
        ICurioStacksHandler handler = stacksHandler(entity, slotId);
        return handler == null ? 0 : handler.getSlots();
    }

    /** Real (gameplay) stacks in one slot id, in index order, empties skipped. */
    public static List<ItemStack> worn(LivingEntity entity, String slotId) {
        ICurioStacksHandler handler = stacksHandler(entity, slotId);
        if (handler == null) {
            return List.of();
        }
        IDynamicStackHandler stacks = handler.getStacks();
        List<ItemStack> result = new ArrayList<>(stacks.getSlots());
        for (int i = 0; i < stacks.getSlots(); i++) {
            ItemStack stack = stacks.getStackInSlot(i);
            if (!stack.isEmpty()) {
                result.add(stack);
            }
        }
        return result;
    }

    /** Every occupied real slot, every slot id. Gameplay view: cosmetic slots are ignored. */
    public static void forEachWorn(LivingEntity entity, SlotVisitor visitor) {
        ICuriosItemHandler inventory = CuriosApi.getCuriosInventoryOrNull(entity);
        if (inventory == null) {
            return;
        }
        for (Map.Entry<String, ICurioStacksHandler> entry : inventory.getCurios().entrySet()) {
            IDynamicStackHandler stacks = entry.getValue().getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    visitor.visit(entry.getKey(), i, stack);
                }
            }
        }
    }

    /**
     * Every stack that will be <em>drawn</em>: the cosmetic stack where one is set, otherwise the real
     * stack if that slot's render toggle is on. Mirrors Curios' own render list exactly, so armour is
     * only hidden by what is actually on screen. Visual use only — never gameplay.
     */
    public static void forEachRendered(LivingEntity entity, SlotVisitor visitor) {
        ICuriosItemHandler inventory = CuriosApi.getCuriosInventoryOrNull(entity);
        if (inventory == null) {
            return;
        }
        for (Map.Entry<String, ICurioStacksHandler> entry : inventory.getCurios().entrySet()) {
            ICurioStacksHandler handler = entry.getValue();
            IDynamicStackHandler stacks = handler.getStacks();
            IDynamicStackHandler cosmetics = handler.getCosmeticStacks();
            NonNullList<Boolean> renders = handler.getRenders();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = i < cosmetics.getSlots() ? cosmetics.getStackInSlot(i) : ItemStack.EMPTY;
                if (stack.isEmpty() && renders.size() > i && renders.get(i)) {
                    stack = stacks.getStackInSlot(i);
                }
                if (!stack.isEmpty()) {
                    visitor.visit(entry.getKey(), i, stack);
                }
            }
        }
    }

    /** Whether {@link #equip} would find an empty slot of {@code slotId} that accepts the stack. */
    public static boolean canEquip(LivingEntity entity, String slotId, ItemStack stack) {
        return freeSlot(entity, slotId, stack) >= 0;
    }

    /**
     * Puts {@code stack} into the first empty slot of {@code slotId} that accepts it.
     *
     * <p>Validity is Curios' own check, so a datapack that narrows a slot is respected here too. The
     * caller owns the stack it passes; on success it now lives in the slot.
     *
     * @return {@code true} if the stack was placed
     */
    public static boolean equip(LivingEntity entity, String slotId, ItemStack stack) {
        int index = freeSlot(entity, slotId, stack);
        if (index < 0) {
            return false;
        }
        ICurioStacksHandler handler = stacksHandler(entity, slotId);
        if (handler == null) {
            return false;
        }
        handler.getStacks().setStackInSlot(index, stack);
        return true;
    }

    private static int freeSlot(LivingEntity entity, String slotId, ItemStack stack) {
        ICurioStacksHandler handler = stacksHandler(entity, slotId);
        if (handler == null || stack.isEmpty()) {
            return -1;
        }
        IDynamicStackHandler stacks = handler.getStacks();
        for (int i = 0; i < stacks.getSlots(); i++) {
            if (stacks.getStackInSlot(i).isEmpty()
                    && CuriosApi.isStackValid(new SlotContext(slotId, entity, i, false, true), stack)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Empties every real slot whose stack matches, returning what was taken in visit order.
     * Cosmetic slots are left alone — they grant nothing, so nothing gameplay-driven should strip them.
     */
    public static List<ItemStack> removeIf(LivingEntity entity, Predicate<ItemStack> filter) {
        ICuriosItemHandler inventory = CuriosApi.getCuriosInventoryOrNull(entity);
        if (inventory == null) {
            return List.of();
        }
        List<ItemStack> taken = new ArrayList<>();
        for (ICurioStacksHandler handler : inventory.getCurios().values()) {
            IDynamicStackHandler stacks = handler.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (!stack.isEmpty() && filter.test(stack)) {
                    taken.add(stack.copy());
                    stacks.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        }
        return taken;
    }

    private static @Nullable ICurioStacksHandler stacksHandler(LivingEntity entity, String slotId) {
        ICuriosItemHandler inventory = CuriosApi.getCuriosInventoryOrNull(entity);
        return inventory == null ? null : inventory.getStacksHandler(slotId).orElse(null);
    }
}
