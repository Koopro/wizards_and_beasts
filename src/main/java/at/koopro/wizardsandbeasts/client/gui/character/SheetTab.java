package at.koopro.wizardsandbeasts.client.gui.character;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The Character Sheet's pages, in rail order: who the wizard is first, then what they are made of, then the
 * roads they are walking.
 *
 * <p>Public so a page can send the reader to another one — the Identity page's cards open the page that holds
 * the detail — without reaching into the screen.
 */
@NullMarked
public enum SheetTab {
    IDENTITY("identity", null),
    ATTRIBUTES("attributes", Module.PLAYER_STATS),
    MAGIC("spells", Module.PROFICIENCY),
    WAND("wand", null),
    SKILLS("skills", Module.SKILL_TREES),
    CREATURES("beasts", Module.CREATURES),
    EDUCATION("education", Module.OWLS),
    PROFESSION("career", Module.OWLS),
    REPUTATION("record", Module.MINISTRY),
    TRAVEL("travel", Module.BROOM_FLIGHT);

    private static final String KEY = "gui.wizards_and_beasts.character_sheet.tab.";

    /** Lang suffix. The older pages keep their original keys, so packs that renamed them keep working. */
    private final String id;
    /** The module whose data the page mostly shows, for the PREVIEW mark; null for pages fed by several. */
    private final @Nullable Module source;

    SheetTab(String id, @Nullable Module source) {
        this.id = id;
        this.source = source;
    }

    public Component label() {
        return Component.translatable(KEY + id);
    }

    /** One line on what the page holds, for the rail's hover card. */
    public Component description() {
        return Component.translatable(KEY + id + ".desc");
    }

    /**
     * Whether this page is showing data owned by a module that has not shipped as finished. Read live from
     * {@link ModuleManager}, so a server that promotes a module stops marking the page without a client change.
     */
    public boolean previewSourced() {
        return source != null && ModuleManager.isPreview(source);
    }
}
