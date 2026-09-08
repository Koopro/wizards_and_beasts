package at.koopro.wizardsandbeasts.heritage.werewolf;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * What a wolf cannot wear.
 *
 * <p>Called once when the change completes. Every armour and hand slot is emptied unless the item is
 * on {@link WerewolfConfig#isEquipmentWhitelisted(String)}, and the stack goes either into the
 * player's own inventory (the default) or onto the ground.
 *
 * <p>Unequipping into the inventory rather than destroying or stashing the gear is deliberate. A stash
 * would need its own attachment, its own serialisation and its own answer to "what if the player dies
 * while it is held" — three new ways to lose somebody's diamond armour for a mechanic whose only real
 * requirement is that the armour stops <em>counting</em>. In the inventory it stops counting, it is
 * still theirs, and the change needs no bookkeeping to undo.
 *
 * <p>The whitelist is the escape hatch for gear that is meant to survive a change — a cloak bound to
 * the wearer, a cursed ring, anything an addon wants to keep on the body.
 */
@NullMarked
public final class WerewolfEquipment {

    /**
     * The slots a transformation empties. {@code SADDLE} is skipped: a player cannot fill it, and
     * touching it would only matter for an entity that is not one.
     */
    private static final List<EquipmentSlot> SLOTS = List.of(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.BODY,
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND);

    private WerewolfEquipment() {}

    /**
     * Empties every non-whitelisted equipment slot.
     *
     * @return the number of stacks removed, so the caller can decide whether the player is worth
     *         telling about it
     */
    public static int stripForTransformation(ServerPlayer player) {
        int removed = 0;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || isWhitelisted(stack)) {
                continue;
            }
            ItemStack taken = stack.copy();
            player.setItemSlot(slot, ItemStack.EMPTY);
            stow(player, taken);
            removed++;
        }
        if (removed > 0) {
            player.containerMenu.broadcastChanges();
        }
        return removed;
    }

    private static boolean isWhitelisted(ItemStack stack) {
        return WerewolfConfig.isEquipmentWhitelisted(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    /**
     * Puts a taken stack somewhere the player can get it back from. Falls through to a drop when the
     * inventory is full — otherwise a wolf with 36 occupied slots would silently eat their own helmet.
     */
    private static void stow(ServerPlayer player, ItemStack stack) {
        if (!WerewolfConfig.dropUnsafeEquipment && player.getInventory().add(stack)) {
            return;
        }
        player.drop(stack, false);
    }
}
