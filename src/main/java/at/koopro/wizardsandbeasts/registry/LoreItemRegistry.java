package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.item.lore.LoreTomeItem;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Studyable lore tomes. Each is read once for permanent KNOWLEDGE / History of Magic OWL credit
 * (see {@link LoreTomeItem}). The registry path doubles as the unique lore-source id, so adding a
 * new tome here automatically participates in the de-duplicated read tracking.
 */
public final class LoreItemRegistry {

    public static final DeferredItem<LoreTomeItem> A_HISTORY_OF_MAGIC =
            ModItems.ITEMS.registerItem("a_history_of_magic", props -> new LoreTomeItem(props.stacksTo(16)));

    public static final DeferredItem<LoreTomeItem> HOGWARTS_A_HISTORY =
            ModItems.ITEMS.registerItem("hogwarts_a_history", props -> new LoreTomeItem(props.stacksTo(16)));

    public static final DeferredItem<LoreTomeItem> RISE_AND_FALL_OF_THE_DARK_ARTS =
            ModItems.ITEMS.registerItem("rise_and_fall_of_the_dark_arts", props -> new LoreTomeItem(props.stacksTo(16)));

    private LoreItemRegistry() {}

    public static void init() {}
}
