package at.koopro.wizardsandbeasts.client.gui.character;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;

/**
 * Texture Identifier constants for the Character Sheet GUI.
 *
 * TODO: All paths below require actual PNG art assets to be produced.
 *       Placeholder dimensions are noted per constant.
 */
public final class CharacterSheetTextures {

    /**
     * Main background panel — 320×240 nine-patch parchment.
     * TODO: create assets/wizards_and_beasts/textures/gui/character_sheet/background.png
     */
    public static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/gui/character_sheet/background.png");

    /**
     * 16×16 icon shown on the inventory tab button.
     * TODO: create assets/wizards_and_beasts/textures/gui/character_sheet/icon_tab.png
     */
    public static final Identifier ICON_TAB = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/gui/character_sheet/icon_tab.png");

    /** 24×24 nine-slice (3px corners) sub-panel for tabs/effect chips — inactive state. */
    public static final Identifier PANEL = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/gui/character_sheet/panel.png");

    /** 24×24 nine-slice (3px corners) sub-panel — active/hover state. */
    public static final Identifier PANEL_SEL = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/gui/character_sheet/panel_sel.png");

    public static final int PANEL_SIZE = 24;
    public static final int PANEL_BORDER = 3;

    private CharacterSheetTextures() {}
}
