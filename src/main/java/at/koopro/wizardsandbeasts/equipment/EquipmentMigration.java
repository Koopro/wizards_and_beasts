package at.koopro.wizardsandbeasts.equipment;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

/**
 * Moves pieces that used to be armour into the Curios slots they belong in now.
 *
 * <p>Before the Curios layer, cloaks sat in {@link EquipmentSlot#CHEST} and the robe body, wizard hat
 * and masks were real armour. A save from then still has them in armour slots. On every login each
 * armour slot is checked; a stack that has a wearable definition (and so is no longer armour) is moved
 * to its Curios slot with every component intact — cloak charges, hood state, enchantments, name.
 *
 * <p>Rules, in order:
 * <ol>
 *   <li>Only stacks with a wearable definition move. Real armour has none, so a netherite chestplate
 *       is never touched — and if a cloak and a chestplate were somehow both worn, only the cloak
 *       moves.</li>
 *   <li>Nothing already worn is overwritten: a full target slot means the piece goes to the main
 *       inventory instead.</li>
 *   <li>If the inventory is full too, the piece stays where it is. It is inert in an armour slot but
 *       it is not lost, and the next login tries again.</li>
 * </ol>
 * Idempotent: once nothing defined sits in an armour slot, the pass is four reads.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
@NullMarked
public final class EquipmentMigration {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** What happened to one stack. */
    public enum Outcome { NOTHING, EQUIPPED, STOWED, LEFT_IN_PLACE }

    private EquipmentMigration() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            migrate(player);
        }
    }

    /** Runs the pass; returns how many stacks were moved out of armour slots. */
    public static int migrate(ServerPlayer player) {
        if (!EquipmentAPI.canWear(player)) {
            return 0;
        }
        int moved = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            Outcome outcome = migrateSlot(player, slot);
            if (outcome == Outcome.EQUIPPED || outcome == Outcome.STOWED) {
                moved++;
            }
        }
        if (moved > 0) {
            player.containerMenu.broadcastChanges();
        }
        return moved;
    }

    static Outcome migrateSlot(ServerPlayer player, EquipmentSlot slot) {
        ItemStack inSlot = player.getItemBySlot(slot);
        if (inSlot.isEmpty() || WearableDefinitions.get(inSlot) == null) {
            return Outcome.NOTHING;
        }
        String id = BuiltInRegistries.ITEM.getKey(inSlot.getItem()).toString();
        // Worn pieces are single items; working on a copy of one keeps a corrupt multi-count stack from
        // being equipped and stowed at the same time.
        if (inSlot.getCount() == 1 && EquipmentAPI.tryEquip(player, inSlot.copy())) {
            player.setItemSlot(slot, ItemStack.EMPTY);
            LOGGER.info("[Equipment] migrated {} from {} to its Curios slot for {}", id, slot.getName(),
                    player.getName().getString());
            return Outcome.EQUIPPED;
        }
        ItemStack moving = inSlot.copy();
        player.getInventory().add(moving);
        // `add` shrinks what it placed; whatever is left goes back in the slot, so nothing is lost.
        player.setItemSlot(slot, moving.isEmpty() ? ItemStack.EMPTY : moving);
        if (moving.isEmpty()) {
            LOGGER.info("[Equipment] {} could not be worn in its Curios slot (occupied); stowed for {}",
                    id, player.getName().getString());
            return Outcome.STOWED;
        }
        LOGGER.warn("[Equipment] {} left in {} for {}: Curios slot and inventory both full", id,
                slot.getName(), player.getName().getString());
        return Outcome.LEFT_IN_PLACE;
    }
}
