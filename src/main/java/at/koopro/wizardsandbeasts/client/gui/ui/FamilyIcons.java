package at.koopro.wizardsandbeasts.client.gui.ui;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/**
 * The icon families drawn by {@code tools/ui_icons.py} (UI renaissance P4, 2026-11-18): 16x16 cells in the same
 * engraved one-ink style as the stat glyphs, one strip per family, indexed by the enum's ordinal.
 *
 * <p>Drawn 1:1 (pixel art). An ordinal past the strip draws nothing, so a new enum constant costs a decoration, not a
 * magenta square; the label beside the icon still names it.
 */
@NullMarked
public final class FamilyIcons {

    public static final int SIZE = 16;

    private static final Identifier SPELL_CATEGORIES = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/gui/icons/spell_categories.png");
    private static final int SPELL_CATEGORY_CELLS = 4;
    private static final Identifier HERITAGES = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/gui/icons/heritages.png");
    private static final int HERITAGE_CELLS = 8;
    /** Novice, Proficient, Mastered (Proficiency order), then the skill point. */
    private static final Identifier PROGRESSION = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/gui/icons/progression.png");
    private static final int PROGRESSION_CELLS = 4;
    private static final int SKILL_POINT_CELL = 3;

    /** Curios slot order: head, face, body, back, hands, belt, necklace, ring, charm. */
    private static final Identifier EQUIPMENT = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/gui/icons/equipment_slots.png");
    public static final int EQUIPMENT_CELLS = 9;

    private FamilyIcons() {}

    /** An equipment slot's icon by its cell (see {@link #EQUIPMENT_CELLS}); faded for an empty-slot hint. */
    public static void equipmentSlot(GuiGraphics g, int cell, int x, int y, float alpha) {
        cell(g, EQUIPMENT, EQUIPMENT_CELLS, cell, x, y, UiMotion.alpha(alpha));
    }

    public static void spellCategory(GuiGraphics g, SpellCategory category, int x, int y) {
        cell(g, SPELL_CATEGORIES, SPELL_CATEGORY_CELLS, category.ordinal(), x, y);
    }

    public static void heritage(GuiGraphics g, Heritage heritage, int x, int y) {
        cell(g, HERITAGES, HERITAGE_CELLS, heritage.ordinal(), x, y);
    }

    /** A spell's proficiency tier: sprout, bloom, crown. {@code alpha} below 1 quiets it (the common tier). */
    public static void proficiency(GuiGraphics g, Proficiency tier, int x, int y, float alpha) {
        cell(g, PROGRESSION, PROGRESSION_CELLS, tier.ordinal(), x, y, UiMotion.alpha(alpha));
    }

    public static void skillPoint(GuiGraphics g, int x, int y) {
        cell(g, PROGRESSION, PROGRESSION_CELLS, SKILL_POINT_CELL, x, y, -1);
    }

    private static void cell(GuiGraphics g, Identifier strip, int cells, int index, int x, int y) {
        cell(g, strip, cells, index, x, y, -1);
    }

    private static void cell(GuiGraphics g, Identifier strip, int cells, int index, int x, int y, int color) {
        if (index < 0 || index >= cells || !UiIcons.exists(strip)) {
            return;
        }
        g.blit(RenderPipelines.GUI_TEXTURED, strip, x, y, (float) (index * SIZE), 0f, SIZE, SIZE, SIZE, SIZE,
                SIZE * cells, SIZE, color);
    }
}
