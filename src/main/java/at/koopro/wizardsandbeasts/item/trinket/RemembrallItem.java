package at.koopro.wizardsandbeasts.item.trinket;

import at.koopro.wizardsandbeasts.item.AnimatedItem;
import net.minecraft.world.item.Item;

/**
 * A Remembrall. In canon it turns red when its holder has forgotten something (<i>Philosopher's Stone</i> ch. 9);
 * the mod has no notion of a forgotten thing, so it is honestly decorative. It used to flash its enchantment glint
 * every 400 ms whatever was going on, which told the player something the object never meant
 * (documentation/CANON_AUDIT.md C-10).
 */
public class RemembrallItem extends Item implements AnimatedItem {
    public RemembrallItem(Properties properties) {
        super(properties);
    }
}
