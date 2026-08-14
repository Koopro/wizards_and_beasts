package at.koopro.wizardsandbeasts.client.spell.gui;

import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellDataState;
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
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;

import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Non-widget painting for {@link SpellMenuScreen}.
 */
public final class SpellMenuRenderHelper {

    private static final net.minecraft.resources.Identifier PANEL_TEX =
            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID, "textures/gui/spell_menu/panel.png");

    /** Average ink of {@code panel.png} — the ground every label on this screen is read against. */
    private static final int PANEL_INK = 0xFF1E1A32;

    private SpellMenuRenderHelper() {}

    /**
     * Leather plate, brass filigree, and a recessed well for the index.
     *
     * <p>This screen used to paint a navy panel with a pure-gold title, which matched nothing else in
     * the mod — {@code WizardsPalette} is leather and brass and says so in its own javadoc. The frame
     * is now built from it: {@code PLATE} for the face, {@code BRASS} for the rule, {@code WELL} for
     * the sunken list.
     */
    public static void renderFrame(GuiGraphics graphics, Font font, int width, int height,
                                   int panelW, int panelH, int leftW, GuiScaleHelper.Layout layout) {
        int x = layout.panelX();
        int y = layout.panelY();
        int w = layout.panelW();
        int h = layout.panelH();
        int lw = layout.s(leftW);

        graphics.fill(x, y, x + w, y + h, WizardsPalette.PLATE);
        McStylePanel.drawBorder(graphics, x, y, w, h, WizardsPalette.BRASS, WizardsPalette.INK);

        // Header band, seated on a brass rule.
        int headerH = layout.s(WizardsAndBeastsUiTokens.SpellMenu.DIVIDER_Y_OFFSET);
        graphics.fill(x + 1, y + 1, x + w - 1, y + headerH, WizardsPalette.RAIL);
        graphics.fill(x + 1, y + headerH, x + w - 1, y + headerH + 1, WizardsPalette.LINE);

        graphics.drawCenteredString(font,
                net.minecraft.network.chat.Component.translatable("gui.wizards_and_beasts.spell_menu.title"),
                x + w / 2, y + layout.s(WizardsAndBeastsUiTokens.SpellMenu.HEADER_Y_OFFSET),
                WizardsPalette.BRASS_HI);

        // The index sits in a recessed well; the plate to its right carries the sigil.
        int wellTop = y + headerH + layout.s(WizardsAndBeastsUiTokens.SpellMenu.TOP_PADDING);
        graphics.fill(x + layout.s(2), wellTop, x + lw, y + h - layout.s(2), WizardsPalette.WELL);
        graphics.fill(x + lw, y + headerH + 1, x + lw + 1, y + h - layout.s(2), WizardsPalette.LINE);
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
            // DARK_ARTS' 0x8B00FF is darker than the navy panel it sits on — that header row was
            // effectively invisible. Contrast-clamped, so the category hue survives but reads.
            graphics.drawString(font, catName, x + WizardsAndBeastsUiTokens.SpellMenu.CATEGORY_X_OFFSET,
                    y + WizardsAndBeastsUiTokens.SpellMenu.ENTRY_TEXT_Y_OFFSET,
                    UiContrast.readableOn(entry.category().getColor(), WizardsPalette.WELL), false);
            return;
        }

        Spell spell = entry.spell();
        // Rows are cards on the well, not text on a void: a seated row is what makes the index read
        // as a list of things you can pick up rather than a paragraph.
        graphics.fill(x - 1, y - 1, x + w, y + h,
                selected ? WizardsPalette.SELECT : hovered ? WizardsPalette.PLATE_2 : WizardsPalette.PLATE);
        if (selected) {
            graphics.fill(x - 1, y - 1, x + 1, y + h, WizardsPalette.BRASS);
        }

        Minecraft mc = Minecraft.getInstance();
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                at.koopro.wizardsandbeasts.client.ModTextures.resolveWandHudSpellIcon(
                        mc.getResourceManager(), spell.getId()),
                x + 1, y + (h - iconSize) / 2, 0f, 0f, iconSize, iconSize, 92, 92, 92, 92);

        graphics.drawString(font,
                at.koopro.wizardsandbeasts.client.gui.util.GuiText.resolve(spell.getDisplayName()),
                x + iconSize + 4, y + WizardsAndBeastsUiTokens.SpellMenu.ENTRY_TEXT_Y_OFFSET,
                UiContrast.readableOn(spell.getCategory().getColor(), WizardsPalette.PLATE), false);

        PlayerSpellData data = ClientSpellDataState.get();
        if (mc.level != null && data.isOnCooldown(spell.getId(), mc.level.getGameTime())) {
            float sec = Math.max(0f, (data.getCooldownExpiry(spell.getId()) - mc.level.getGameTime()) / 20f);
            String label = String.format("%.1fs", sec);
            graphics.drawString(font, label,
                    x + w - WizardsAndBeastsUiTokens.SpellMenu.COOLDOWN_X_OFFSET,
                    y + WizardsAndBeastsUiTokens.SpellMenu.ENTRY_TEXT_Y_OFFSET,
                    WizardsAndBeastsUiTokens.SpellMenu.COOLDOWN_COLOR, false);
        }

        Proficiency prof = Proficiency.fromCastCount(data.getSuccessfulHits(spell.getId()));
        String indicator = switch (prof) {
            case MASTERED -> "★";
            case PROFICIENT -> "◉";
            default -> "○";
        };
        int profColor = switch (prof) {
            case MASTERED -> WizardsAndBeastsUiTokens.SpellMenu.PROF_MASTERED;
            case PROFICIENT -> WizardsAndBeastsUiTokens.SpellMenu.PROF_PROFICIENT;
            default -> WizardsAndBeastsUiTokens.SpellMenu.PROF_DEFAULT_DARK;
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
        int infoX = panelX + leftW + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_INFO_X_OFFSET);

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
                UiContrast.readableOn(sel.getCategory().getColor(), WizardsPalette.PLATE), false);

        graphics.drawString(font, sel.getCategory().name().replace('_', ' '), infoX,
                infoY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_CATEGORY_Y),
                WizardsPalette.TEXT_DIM, false);

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && data.isOnCooldown(sel.getId(), mc.level.getGameTime())) {
            float sec = Math.max(0f, (data.getCooldownExpiry(sel.getId()) - mc.level.getGameTime()) / 20f);
            graphics.drawString(font,
                    Component.translatable("gui.wizards_and_beasts.spell_menu.recharging",
                            String.format("%.1f", sec)),
                    infoX, infoY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_COOLDOWN_Y),
                    WizardsAndBeastsUiTokens.SpellMenu.COOLDOWN_COLOR, false);
        } else {
            graphics.drawString(font,
                    Component.translatable("gui.wizards_and_beasts.spell_menu.cooldown",
                            String.format("%.1f", sel.getBaseCooldownTicks() / 20.0f)),
                    infoX, infoY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_COOLDOWN_Y),
                    WizardsPalette.TEXT_DIM, false);
        }

        if (sel.getBaseDamage() > 0) {
            graphics.drawString(font,
                    Component.translatable("gui.wizards_and_beasts.spell_menu.damage",
                            String.format("%.1f", sel.getBaseDamage())),
                    infoX, infoY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_DAMAGE_Y),
                    WizardsPalette.TEXT_DIM, false);
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
            case MASTERED -> WizardsAndBeastsUiTokens.SpellMenu.PROF_MASTERED;
            case PROFICIENT -> WizardsAndBeastsUiTokens.SpellMenu.PROF_PROFICIENT;
            default -> WizardsAndBeastsUiTokens.SpellMenu.PROF_NOVICE_TEXT;
        };
        Component profText = prof == Proficiency.MASTERED
                ? Component.translatable("gui.wizards_and_beasts.spell_menu.prof_casts", profName, casts)
                : Component.translatable("gui.wizards_and_beasts.spell_menu.prof_progress",
                        profName, casts, nextThreshold);
        int profY = sel.getBaseDamage() > 0
                ? infoY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_PROF_WITH_DAMAGE_Y)
                : infoY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_PROF_NO_DAMAGE_Y);
        graphics.drawString(font, profText, infoX, profY, profColor, false);

        SpellRequirement req = sel.getRequirement();
        if (req != null && req != SpellRequirement.NONE) {
            graphics.drawString(font, req.describe(), infoX,
                    profY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_REQ_Y),
                    WizardsPalette.TEXT_DIM, false);
        }
    }
}
