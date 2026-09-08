package at.koopro.wizardsandbeasts.entity.dummy;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NullMarked;

/**
 * What a duelling dummy is doing besides absorbing spells, read from whatever is on its head.
 *
 * <p>The head slot rather than a synched flag or a GUI: the dummy already renders what it wears, so
 * the mode is legible from across the room without anyone having to click it. That is also why
 * there are only three — a mode you cannot see at a glance is a setting, and settings live in the
 * config screen.
 */
@NullMarked
public enum DummyMode {
    /** Plain target. */
    TRAINING,
    /** Carved pumpkin: keeps animals off and suppresses hostile spawns nearby. */
    SCARECROW,
    /** Player head: hostile mobs attack it instead of the wizard standing behind it. */
    DECOY;

    public static DummyMode of(ItemStack head) {
        if (head.is(Items.CARVED_PUMPKIN) || head.is(Items.JACK_O_LANTERN)) {
            return SCARECROW;
        }
        if (head.is(Items.PLAYER_HEAD)) {
            return DECOY;
        }
        return TRAINING;
    }
}
