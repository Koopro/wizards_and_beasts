package at.koopro.wizardsandbeasts.equipment;

import at.koopro.wizardsandbeasts.equipment.curios.CuriosEquipment;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The one door to "what is this wizard wearing".
 *
 * <p>Gameplay asks in {@link EquipmentType}s and gets stacks back; it never learns whether a piece
 * came from a vanilla armour slot or a Curios slot, and never names a slot string. Three sources sit
 * behind it:
 * <ul>
 *   <li><b>Vanilla</b> — helmet, chestplate, leggings, boots. Read-only here: armour keeps every
 *       vanilla mechanic and the mod never re-implements one.</li>
 *   <li><b>Curios</b> — clothing, cloaks, headwear, masks, jewellery, belts, gloves, pouches.</li>
 *   <li><b>W&amp;B special</b> — wand, broom, forms. Not equipment and not routed through here; their
 *       own systems stay authoritative.</li>
 * </ul>
 *
 * <p><b>Authority.</b> Every query reads the live inventory. Called on the server it is the truth;
 * called on a client it is the synced copy, fit for rendering and UI only — never for damage,
 * protection, spells, invisibility or resistance.
 */
@NullMarked
public final class EquipmentAPI {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private EquipmentAPI() {}

    // -- Typed queries ----------------------------------------------------------------------------

    /** Every real stack of this type the entity wears, in slot order. */
    public static List<ItemStack> getEquipped(LivingEntity entity, EquipmentType type) {
        List<ItemStack> inSlot = CuriosEquipment.worn(entity, type.slotId());
        if (inSlot.isEmpty()) {
            return List.of();
        }
        List<ItemStack> result = new ArrayList<>(inSlot.size());
        for (ItemStack stack : inSlot) {
            if (WearableDefinitions.is(stack, type)) {
                result.add(stack);
            }
        }
        return result;
    }

    /** The first real stack of this type, or {@link ItemStack#EMPTY}. */
    public static ItemStack getFirst(LivingEntity entity, EquipmentType type) {
        List<ItemStack> worn = getEquipped(entity, type);
        return worn.isEmpty() ? ItemStack.EMPTY : worn.getFirst();
    }

    public static boolean hasEquipment(LivingEntity entity, EquipmentType type) {
        return !getFirst(entity, type).isEmpty();
    }

    public static ItemStack getEquippedCloak(LivingEntity entity) {
        return getFirst(entity, EquipmentType.CLOAK);
    }

    public static ItemStack getEquippedBodyClothing(LivingEntity entity) {
        return getFirst(entity, EquipmentType.BODY_CLOTHING);
    }

    public static ItemStack getEquippedHat(LivingEntity entity) {
        return getFirst(entity, EquipmentType.HEADWEAR);
    }

    public static ItemStack getEquippedMask(LivingEntity entity) {
        return getFirst(entity, EquipmentType.FACE);
    }

    public static ItemStack getEquippedNecklace(LivingEntity entity) {
        return getFirst(entity, EquipmentType.NECKLACE);
    }

    /** All worn rings. How many there can be is data (the Curios ring slot size), not code. */
    public static List<ItemStack> getEquippedRings(LivingEntity entity) {
        return getEquipped(entity, EquipmentType.RING);
    }

    /** The first worn piece of any type that matches, or {@link ItemStack#EMPTY}. */
    public static ItemStack findWorn(LivingEntity entity, Predicate<ItemStack> filter) {
        ItemStack[] found = {ItemStack.EMPTY};
        CuriosEquipment.forEachWorn(entity, (slotId, index, stack) -> {
            if (found[0].isEmpty() && filter.test(stack)) {
                found[0] = stack;
            }
        });
        return found[0];
    }

    /** Vanilla armour in one of the four armour slots. */
    public static ItemStack getArmor(LivingEntity entity, EquipmentSlot slot) {
        return entity.getItemBySlot(slot);
    }

    /** How many slots of this type the entity has (ring count, for instance). */
    public static int slotCount(LivingEntity entity, EquipmentType type) {
        return CuriosEquipment.slotCount(entity, type.slotId());
    }

    public static @Nullable WearableDefinition definition(ItemStack stack) {
        return WearableDefinitions.get(stack);
    }

    /** Whether this entity can wear Curios equipment at all. */
    public static boolean canWear(LivingEntity entity) {
        return CuriosEquipment.hasInventory(entity);
    }

    // -- Changes (server side) --------------------------------------------------------------------

    /**
     * Puts one of {@code stack} on the entity in the slot its definition names, if a matching slot is
     * free. On success one item is moved out of {@code stack}; on failure nothing changes.
     *
     * <p>Never displaces what is already worn — a second cloak does not knock the first one off.
     */
    public static boolean tryEquip(LivingEntity entity, ItemStack stack) {
        WearableDefinition definition = WearableDefinitions.get(stack);
        if (definition == null) {
            return false;
        }
        ItemStack single = stack.copyWithCount(1);
        if (!CuriosEquipment.equip(entity, definition.type().slotId(), single)) {
            return false;
        }
        stack.shrink(1);
        return true;
    }

    /** Whether {@link #tryEquip} would succeed: the stack is defined and a matching slot is free. */
    public static boolean canEquip(LivingEntity entity, ItemStack stack) {
        WearableDefinition definition = WearableDefinitions.get(stack);
        return definition != null && CuriosEquipment.canEquip(entity, definition.type().slotId(), stack);
    }

    /**
     * Right-click-to-wear for any wearable item: moves one of the held stack into its slot.
     *
     * <p>The move happens on the server only; the client answers from its synced copy so the arm
     * swings, and Curios syncs the result back. Returns {@code PASS} when there is nowhere to put it,
     * so a full slot never eats the click.
     */
    public static InteractionResult equipFromHand(Player player, InteractionHand hand, Holder<SoundEvent> sound) {
        ItemStack held = player.getItemInHand(hand);
        if (!canEquip(player, held)) {
            return InteractionResult.PASS;
        }
        if (!player.level().isClientSide() && tryEquip(player, held)) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Takes every worn W&amp;B wearable off the entity unless {@code keep} says otherwise, and returns
     * the stacks so the caller decides where they go. Only items with a wearable definition are
     * touched; another mod's curios are not this mod's to strip.
     */
    public static List<ItemStack> stripWearables(LivingEntity entity, Predicate<ItemStack> keep) {
        return CuriosEquipment.removeIf(entity,
                stack -> WearableDefinitions.get(stack) != null && !keep.test(stack));
    }

    // -- Snapshot (UI / debug) --------------------------------------------------------------------

    /** One occupied Curios slot. {@code definition} is null for another mod's curio. */
    public record WornPiece(String slotId, int index, ItemStack stack, @Nullable WearableDefinition definition) {}

    /** Everything worn, for display. Armour is keyed by slot; worn pieces are in visual-layer order. */
    public record Snapshot(Map<EquipmentSlot, ItemStack> armor, List<WornPiece> worn,
                           EquipmentVisibility.Result visibility) {

        /** Worn pieces of one type, in slot order. */
        public List<WornPiece> of(EquipmentType type) {
            List<WornPiece> result = new ArrayList<>();
            for (WornPiece piece : worn) {
                if (piece.definition() != null && piece.definition().type() == type) {
                    result.add(piece);
                }
            }
            return result;
        }
    }

    public static Snapshot snapshot(LivingEntity entity) {
        Map<EquipmentSlot, ItemStack> armor = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            armor.put(slot, entity.getItemBySlot(slot));
        }
        List<WornPiece> worn = new ArrayList<>();
        CuriosEquipment.forEachWorn(entity, (slotId, index, stack) ->
                worn.add(new WornPiece(slotId, index, stack, WearableDefinitions.get(stack))));
        worn.sort((a, b) -> Integer.compare(layerOrder(a), layerOrder(b)));
        return new Snapshot(armor, List.copyOf(worn), EquipmentVisibility.resolve(entity));
    }

    private static int layerOrder(WornPiece piece) {
        return piece.definition() == null ? VisualLayer.values().length : piece.definition().layer().ordinal();
    }
}
