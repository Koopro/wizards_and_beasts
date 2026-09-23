package at.koopro.wizardsandbeasts.client.spell.gui;

import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellDataState;
import at.koopro.wizardsandbeasts.client.spell.ui.SpellPowerTooltip;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellRequirement;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsAndBeastsUiTokens;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;

import org.jspecify.annotations.Nullable;

/**
 * Non-widget painting for {@link SpellMenuScreen}.
 */
public final class SpellMenuRenderHelper {

    /** Alpha a placeholder spell's icon is drawn at — present, legible, obviously not available. */
    private static final float ALPHA_COMING_SOON = 0.35f;

    /**
     * How far the sheet reaches past the layout rectangle on every side.
     *
     * <p>Every offset on this screen is measured from the layout's origin and sits 0-4px inside it,
     * which is right on top of the parchment frame's double rule (4-6px in). Growing the sheet by the
     * standard 12px clearance moves the frame out of the content's way without moving a single
     * control; {@code Layout.panel} already leaves 12px of screen either side for it.
     */
    public static final int SHEET_OUTSET = WizardsMetrics.SPACE_L;

    /**
     * The ground the index headers and the info card's labels are read against, for
     * {@link UiContrast}: an inset's stock is the page darkened, so the page's shade tone is the
     * conservative stand-in.
     */
    private static final int WELL_GROUND = WizardsPalette.PAGE_SHADE;

    /**
     * Left edge of the info card, from the index column's edge. Clear of the index well, which runs
     * {@code s(4)} past that edge to hold its scrollbar — two insets must not share an edge.
     */
    public static final int INFO_CARD_X = 8;
    /** The info text, clear of the card's engraved edge. */
    private static final int INFO_TEXT_X = INFO_CARD_X + 6;

    private SpellMenuRenderHelper() {}

    /**
     * The sheet, the title, and a recessed well for the index.
     *
     * <p>This screen was a navy panel with a pure-gold title, then leather and brass; it is now the
     * parchment every other screen is — the shared themed sheet, an inset well for the list, and the
     * title in ink on the page between the search box and the Skills button.
     */
    public static void renderFrame(GuiGraphics graphics, Font font, int width, int height,
                                   int panelW, int panelH, int leftW, GuiScaleHelper.Layout layout) {
        int x = layout.panelX();
        int y = layout.panelY();
        int w = layout.panelW();
        int h = layout.panelH();
        int lw = layout.s(leftW);

        McStylePanel.drawThemedPanel(graphics, x - SHEET_OUTSET, y - SHEET_OUTSET,
                w + 2 * SHEET_OUTSET, h + 2 * SHEET_OUTSET);

        // Centred in the gap between the search box (which spans the index column) and the Skills
        // button, not on the sheet: at w / 2 the search box, drawn later as a widget, covered half of it.
        String title = net.minecraft.network.chat.Component
                .translatable("gui.wizards_and_beasts.spell_menu.title").getString();
        int titleCx = x + (lw + w - layout.s(62)) / 2;
        graphics.drawString(font, title, titleCx - font.width(title) / 2,
                y + layout.s(WizardsAndBeastsUiTokens.SpellMenu.HEADER_Y_OFFSET),
                WizardsPalette.PAGE_INK, false);

        // The index sits in a recessed well that holds its search field too, the full height of the
        // layout: the field is a well of its own, and two wells stacked edge to edge read as a seam.
        // Its right edge clears the list's scrollbar; the plate to its right carries the sigil.
        McStylePanel.drawThemedInset(graphics, x, y, lw + layout.s(4), h);
    }

    /**
     * Paints one list row at a rectangle the caller owns.
     *
     * <p>Per-row rather than per-list because {@link SpellMenuScreen} computes the geometry and uses
     * the same numbers to hit-test. The previous whole-list painter derived its own row positions and
     * used the unscaled {@code LIST_ROW_SPACING} while the screen placed its buttons with the scaled
     * one, so labels and click targets drifted apart at every GUI scale but 100%.
     */
    public static void renderRow(GuiGraphics graphics, Font font, SpellMenuScreen.SpellEntry entry,
                                 int x, int y, int w, int h,
                                 boolean hovered, boolean selected, int iconSize) {
        if (entry.spell() == null) {
            String catName = entry.category().name().replace('_', ' ');
            // Category hues were chosen for a dark ground — UTILITY's 0x44FF44 is 1.4 : 1 on paper.
            // Contrast-clamped, so the hue survives but reads.
            graphics.drawString(font, catName, x + WizardsAndBeastsUiTokens.SpellMenu.CATEGORY_X_OFFSET,
                    y + WizardsAndBeastsUiTokens.SpellMenu.ENTRY_TEXT_Y_OFFSET,
                    UiContrast.readableOn(entry.category().getColor(), WELL_GROUND), false);
            return;
        }

        Spell spell = entry.spell();
        // Rows are cards on the well, not text on a void: a seated row is what makes the index read
        // as a list of things you can pick up rather than a paragraph. The card is the page laid on
        // the darker well; hover lifts it, selection washes it with gilt and marks its edge.
        int card = selected ? WizardsPalette.PAGE_SELECT : hovered ? WizardsPalette.PAGE_LIGHT : WizardsPalette.PAGE;
        graphics.fill(x - 1, y - 1, x + w, y + h, card);
        if (selected) {
            graphics.fill(x - 1, y - 1, x + 1, y + h, WizardsPalette.GILT_DARK);
        }

        Minecraft mc = Minecraft.getInstance();
        // A spell whose behaviour is not written yet is drawn, not hidden: the corpus is registered
        // precisely so the roster is visible. What it must not do is look pickable — faded icon,
        // dimmed name, and a "coming soon" tag where the proficiency mark would go.
        boolean comingSoon = !spell.isImplemented();

        graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                at.koopro.wizardsandbeasts.client.hud.WandHudSprites.spellIcon(spell.getId()),
                x + 1, y + (h - iconSize) / 2, iconSize, iconSize,
                comingSoon ? ALPHA_COMING_SOON : 1.0f);

        graphics.drawString(font,
                at.koopro.wizardsandbeasts.client.gui.util.GuiText.resolve(spell.getDisplayName()),
                x + iconSize + 4, y + WizardsAndBeastsUiTokens.SpellMenu.ENTRY_TEXT_Y_OFFSET,
                comingSoon ? WizardsPalette.PAGE_INK_3
                        : UiContrast.readableOn(spell.getCategory().getColor(), card),
                false);

        if (comingSoon) {
            String tag = net.minecraft.network.chat.Component
                    .translatable("gui.wizards_and_beasts.spell.coming_soon").getString();
            graphics.drawString(font, tag,
                    x + w - font.width(tag) - 2,
                    y + WizardsAndBeastsUiTokens.SpellMenu.ENTRY_TEXT_Y_OFFSET,
                    WizardsPalette.PAGE_INK_3, false);
            return;
        }

        PlayerSpellData data = ClientSpellDataState.get();
        if (mc.level != null && data.isOnCooldown(spell.getId(), mc.level.getGameTime())) {
            float sec = Math.max(0f, (data.getCooldownExpiry(spell.getId()) - mc.level.getGameTime()) / 20f);
            String label = String.format("%.1fs", sec);
            graphics.drawString(font, label,
                    x + w - WizardsAndBeastsUiTokens.SpellMenu.COOLDOWN_X_OFFSET,
                    y + WizardsAndBeastsUiTokens.SpellMenu.ENTRY_TEXT_Y_OFFSET,
                    UiContrast.readableOn(WizardsAndBeastsUiTokens.SpellMenu.COOLDOWN_COLOR, card), false);
        }

        Proficiency prof = Proficiency.fromCastCount(data.getSuccessfulHits(spell.getId()));
        String indicator = switch (prof) {
            case MASTERED -> "★";
            case PROFICIENT -> "◉";
            default -> "○";
        };
        int profColor = switch (prof) {
            case MASTERED -> UiContrast.readableOn(WizardsAndBeastsUiTokens.SpellMenu.PROF_MASTERED, card);
            case PROFICIENT -> UiContrast.readableOn(WizardsAndBeastsUiTokens.SpellMenu.PROF_PROFICIENT, card);
            default -> WizardsPalette.PAGE_INK_3;
        };
        graphics.drawString(font, indicator,
                x + w - WizardsAndBeastsUiTokens.SpellMenu.PROF_X_OFFSET,
                y + WizardsAndBeastsUiTokens.SpellMenu.ENTRY_TEXT_Y_OFFSET, profColor, false);
    }

    /**
     * The detail panel for whichever spell is selected.
     *
     * <p>The assignment hint that used to live at the bottom of this method moved onto the screen,
     * which varies it with what you are holding. A "Sync corrections: N" counter was also removed —
     * that is a debug readout, and this is a screen players open to pick a spell.
     */
    public static void renderSelectedSpellPanel(GuiGraphics graphics, Font font, int width, int height,
                                                int panelW, int panelH, int leftW,
                                                @Nullable String selectedSpellId, GuiScaleHelper.Layout layout) {
        int panelX = layout.panelX();
        int panelY = layout.panelY();
        panelH = layout.panelH();
        leftW = layout.s(leftW);
        int infoX = panelX + leftW + layout.s(INFO_TEXT_X);

        if (selectedSpellId == null) {
            return;
        }
        Spell sel = Spells.byId(selectedSpellId);
        if (sel == null) {
            return;
        }

        PlayerSpellData data = ClientSpellDataState.get();
        int infoY = panelY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_INFO_BASE_Y);

        graphics.drawString(font, GuiText.resolve(sel.getDisplayName()), infoX, infoY,
                UiContrast.readableOn(sel.getCategory().getColor(), WELL_GROUND), false);

        graphics.drawString(font, sel.getCategory().name().replace('_', ' '), infoX,
                infoY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_CATEGORY_Y),
                WizardsPalette.PAGE_INK_2, false);

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && data.isOnCooldown(sel.getId(), mc.level.getGameTime())) {
            float sec = Math.max(0f, (data.getCooldownExpiry(sel.getId()) - mc.level.getGameTime()) / 20f);
            graphics.drawString(font,
                    Component.translatable("gui.wizards_and_beasts.spell_menu.recharging",
                            String.format("%.1f", sec)),
                    infoX, infoY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_COOLDOWN_Y),
                    UiContrast.readableOn(WizardsAndBeastsUiTokens.SpellMenu.COOLDOWN_COLOR, WELL_GROUND), false);
        } else {
            graphics.drawString(font,
                    Component.translatable("gui.wizards_and_beasts.spell_menu.cooldown",
                            String.format("%.1f", sel.getBaseCooldownTicks() / 20.0f)),
                    infoX, infoY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_COOLDOWN_Y),
                    WizardsPalette.PAGE_INK_2, false);
        }

        if (sel.getBaseDamage() > 0) {
            graphics.drawString(font,
                    Component.translatable("gui.wizards_and_beasts.spell_menu.damage",
                            String.format("%.1f", sel.getBaseDamage())),
                    infoX, infoY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_DAMAGE_Y),
                    WizardsPalette.PAGE_INK_2, false);
        }

        int casts = data.getSuccessfulHits(sel.getId());
        Proficiency prof = Proficiency.fromCastCount(casts);
        int nextThreshold = switch (prof) {
            case NOVICE -> Proficiency.PROFICIENT.getCastsRequired();
            case PROFICIENT -> Proficiency.MASTERED.getCastsRequired();
            case MASTERED -> casts;
        };
        Component profName = Component.translatable(switch (prof) {
            case MASTERED -> "gui.wizards_and_beasts.spell_menu.prof.mastered";
            case PROFICIENT -> "gui.wizards_and_beasts.spell_menu.prof.proficient";
            default -> "gui.wizards_and_beasts.spell_menu.prof.novice";
        });
        int profColor = switch (prof) {
            case MASTERED -> UiContrast.readableOn(WizardsAndBeastsUiTokens.SpellMenu.PROF_MASTERED, WELL_GROUND);
            case PROFICIENT -> UiContrast.readableOn(WizardsAndBeastsUiTokens.SpellMenu.PROF_PROFICIENT, WELL_GROUND);
            default -> WizardsPalette.PAGE_INK_2;
        };
        Component profText = prof == Proficiency.MASTERED
                ? Component.translatable("gui.wizards_and_beasts.spell_menu.prof_casts", profName, casts)
                : Component.translatable("gui.wizards_and_beasts.spell_menu.prof_progress",
                        profName, casts, nextThreshold);
        int profY = sel.getBaseDamage() > 0
                ? infoY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_PROF_WITH_DAMAGE_Y)
                : infoY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_PROF_NO_DAMAGE_Y);
        graphics.drawString(font, profText, infoX, profY, profColor, false);

        int nextY = profY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_REQ_Y);

        SpellRequirement req = sel.getRequirement();
        if (req != null && req != SpellRequirement.NONE) {
            graphics.drawString(font, req.describe(), infoX, nextY, WizardsPalette.PAGE_INK_2, false);
            nextY += layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_REQ_Y);
        }

        // What the spell will actually hit for, from the same class the server casts with. The base
        // damage line above it is the number in the JSON; this is the number the player gets, and
        // until now the screen only ever showed the first of the two.
        for (Component line : SpellPowerTooltip.lines(sel)) {
            graphics.drawString(font, line, infoX, nextY, WizardsPalette.PAGE_INK_2, false);
            nextY += font.lineHeight + 1;
        }
    }
}
