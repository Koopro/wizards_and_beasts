package at.koopro.wizardsandbeasts.client.spell.gui;

import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellDataState;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellRequirement;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import at.koopro.wizardsandbeasts.client.gui.ScreenLayoutScaler;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsAndBeastsUiTokens;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Non-widget painting for {@link SpellMenuScreen}.
 */
public final class SpellMenuRenderHelper {

    private SpellMenuRenderHelper() {}

    public static void renderFrame(GuiGraphics graphics, Font font, int width, int height,
                                   int panelW, int panelH, int leftW, ScreenLayoutScaler layout) {
        int panelX = layout.panelX();
        int panelY = layout.panelY();
        panelW = layout.panelW();
        panelH = layout.panelH();
        leftW = layout.s(leftW);

        McStylePanel.drawTexturedPanel(graphics, panelX, panelY, panelW, panelH);
        graphics.drawCenteredString(font, "Spell Menu", panelX + panelW / 2,
                panelY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.HEADER_Y_OFFSET), WizardsAndBeastsUiTokens.SpellMenu.TITLE_COLOR);
        graphics.fill(panelX + leftW, panelY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.DIVIDER_Y_OFFSET),
                panelX + leftW + 1, panelY + panelH - layout.s(WizardsAndBeastsUiTokens.SpellMenu.DIVIDER_BOTTOM_OFFSET),
                WizardsAndBeastsUiTokens.SpellMenu.DIVIDER_COLOR);
    }

    public static void renderSpellList(GuiGraphics graphics, Font font, int width, int height,
                                         int panelW, int panelH, int leftW, int maxVisible,
                                         List<SpellMenuScreen.SpellEntry> spellEntries,
                                         @Nullable String selectedSpellId, int scrollOffset) {
        renderSpellList(graphics, font, width, height, panelW, panelH, leftW, maxVisible, spellEntries, selectedSpellId, scrollOffset, ScreenLayoutScaler.forScreen(width, height, panelW, panelH));
    }

    public static void renderSpellList(GuiGraphics graphics, Font font, int width, int height,
                                       int panelW, int panelH, int leftW, int maxVisible,
                                       List<SpellMenuScreen.SpellEntry> spellEntries,
                                       @Nullable String selectedSpellId, int scrollOffset, ScreenLayoutScaler layout) {
        int panelX = layout.panelX();
        int panelY = layout.panelY();
        panelW = layout.panelW();
        panelH = layout.panelH();
        leftW = layout.s(leftW);
        int listX = panelX + layout.s(WizardsAndBeastsUiTokens.SpellMenu.LEFT_PADDING);
        int searchY = panelY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.TOP_PADDING);
        int listY = searchY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SEARCH_HEIGHT + WizardsAndBeastsUiTokens.SpellMenu.SEARCH_TO_LIST_GAP);
        int startIdx = Math.max(0, scrollOffset);
        int endIdx = Math.min(spellEntries.size(), startIdx + maxVisible);

        if (spellEntries.isEmpty()) {
            graphics.drawString(font, "No spells found", listX, listY + WizardsAndBeastsUiTokens.SpellMenu.EMPTY_LIST_TEXT_Y_OFFSET,
                    WizardsAndBeastsUiTokens.SpellMenu.EMPTY_TEXT_COLOR, false);
        } else {
            for (int i = startIdx; i < endIdx; i++) {
                SpellMenuScreen.SpellEntry entry = spellEntries.get(i);
                int entryY = listY + (i - startIdx) * WizardsAndBeastsUiTokens.SpellMenu.LIST_ROW_SPACING;

                if (entry.spell() == null) {
                    String catName = entry.category().name().replace('_', ' ');
                    graphics.drawString(font, catName,
                            listX + WizardsAndBeastsUiTokens.SpellMenu.CATEGORY_X_OFFSET,
                            entryY + WizardsAndBeastsUiTokens.SpellMenu.ENTRY_TEXT_Y_OFFSET,
                            entry.category().getColor(), false);
                } else {
                    if (selectedSpellId != null && selectedSpellId.equals(entry.spell().getId())) {
                    graphics.fill(listX - layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECT_HIGHLIGHT_LEFT_OFFSET),
                                entryY - layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECT_HIGHLIGHT_TOP_OFFSET),
                                listX + leftW - layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECT_HIGHLIGHT_RIGHT_OFFSET),
                                entryY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.LIST_ROW_SPACING) - layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECT_HIGHLIGHT_BOTTOM_OFFSET),
                                WizardsAndBeastsUiTokens.SpellMenu.SELECT_HIGHLIGHT_COLOR);
                    }

                    PlayerSpellData data = ClientSpellDataState.get();
                    String sid = entry.spell().getId();
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level != null && data.isOnCooldown(sid, mc.level.getGameTime())) {
                        long exp = data.getCooldownExpiry(sid);
                        float sec = Math.max(0f, (exp - mc.level.getGameTime()) / 20f);
                        graphics.drawString(font, String.format("%.1fs", sec),
                                listX + leftW - layout.s(WizardsAndBeastsUiTokens.SpellMenu.COOLDOWN_X_OFFSET),
                                entryY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.ENTRY_TEXT_Y_OFFSET),
                                WizardsAndBeastsUiTokens.SpellMenu.COOLDOWN_COLOR, false);
                    }
                    int casts = data.getSuccessfulHits(entry.spell().getId());
                    Proficiency prof = Proficiency.fromCastCount(casts);
                    String indicator = switch (prof) {
                        case MASTERED -> "\u2605";
                        case PROFICIENT -> "\u25C9";
                        default -> "\u25CB";
                    };
                    int profColor = switch (prof) {
                        case MASTERED -> WizardsAndBeastsUiTokens.SpellMenu.PROF_MASTERED;
                        case PROFICIENT -> WizardsAndBeastsUiTokens.SpellMenu.PROF_PROFICIENT;
                        default -> WizardsAndBeastsUiTokens.SpellMenu.PROF_DEFAULT_DARK;
                    };
                    graphics.drawString(font, indicator,
                            listX + leftW - layout.s(WizardsAndBeastsUiTokens.SpellMenu.PROF_X_OFFSET),
                            entryY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.ENTRY_TEXT_Y_OFFSET),
                            profColor, false);
                }
            }
        }

        if (scrollOffset > 0) {
            graphics.drawCenteredString(font, "\u25B2", panelX + leftW / 2,
                    panelY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SCROLL_UP_INDICATOR_Y), WizardsAndBeastsUiTokens.SpellMenu.SCROLL_COLOR);
        }
        if (scrollOffset < spellEntries.size() - maxVisible) {
            graphics.drawCenteredString(font, "\u25BC", panelX + leftW / 2,
                    panelY + panelH - layout.s(WizardsAndBeastsUiTokens.SpellMenu.SCROLL_DOWN_INDICATOR_BOTTOM_OFFSET),
                    WizardsAndBeastsUiTokens.SpellMenu.SCROLL_COLOR);
        }
    }

    public static void renderSelectedSpellPanel(GuiGraphics graphics, Font font, int width, int height,
                                                int panelW, int panelH, int leftW,
                                                @Nullable String selectedSpellId) {
        renderSelectedSpellPanel(graphics, font, width, height, panelW, panelH, leftW, selectedSpellId, ScreenLayoutScaler.forScreen(width, height, panelW, panelH));
    }

    public static void renderSelectedSpellPanel(GuiGraphics graphics, Font font, int width, int height,
                                                int panelW, int panelH, int leftW,
                                                @Nullable String selectedSpellId, ScreenLayoutScaler layout) {
        int panelX = layout.panelX();
        int panelY = layout.panelY();
        panelW = layout.panelW();
        panelH = layout.panelH();
        leftW = layout.s(leftW);
        int infoX = panelX + leftW + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_INFO_X_OFFSET);

        if (selectedSpellId != null) {
            Spell sel = Spells.byId(selectedSpellId);
            if (sel != null) {
                PlayerSpellData data = ClientSpellDataState.get();
                int infoY = panelY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_INFO_BASE_Y);

                graphics.drawString(font, sel.getDisplayName(), infoX, infoY,
                        sel.getCategory().getColor(), false);

                String catName = sel.getCategory().name().replace('_', ' ');
                graphics.drawString(font, catName, infoX,
                        infoY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_CATEGORY_Y),
                        WizardsAndBeastsUiTokens.SpellMenu.CATEGORY_TEXT, false);

                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null && data.isOnCooldown(sel.getId(), mc.level.getGameTime())) {
                    float sec = Math.max(0f, (data.getCooldownExpiry(sel.getId()) - mc.level.getGameTime()) / 20f);
                    graphics.drawString(font, String.format("Recharging: %.1fs", sec),
                            infoX, infoY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_COOLDOWN_Y),
                            WizardsAndBeastsUiTokens.SpellMenu.COOLDOWN_COLOR, false);
                } else {
                    float cooldownSecs = sel.getBaseCooldownTicks() / 20.0f;
                    graphics.drawString(font, String.format("Cooldown: %.1fs", cooldownSecs),
                            infoX, infoY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_COOLDOWN_Y),
                            WizardsAndBeastsUiTokens.SpellMenu.EMPTY_TEXT_COLOR, false);
                }

                if (sel.getBaseDamage() > 0) {
                    graphics.drawString(font, String.format("Damage: %.1f", sel.getBaseDamage()),
                            infoX, infoY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_DAMAGE_Y),
                            WizardsAndBeastsUiTokens.SpellMenu.EMPTY_TEXT_COLOR, false);
                }

                int casts = data.getSuccessfulHits(sel.getId());
                Proficiency prof = Proficiency.fromCastCount(casts);
                int nextThreshold = switch (prof) {
                    case NOVICE -> Proficiency.PROFICIENT.getCastsRequired();
                    case PROFICIENT -> Proficiency.MASTERED.getCastsRequired();
                    case MASTERED -> casts;
                };
                String profName = switch (prof) {
                    case MASTERED -> "Mastered";
                    case PROFICIENT -> "Proficient";
                    default -> "Novice";
                };
                int profColor = switch (prof) {
                    case MASTERED -> WizardsAndBeastsUiTokens.SpellMenu.PROF_MASTERED;
                    case PROFICIENT -> WizardsAndBeastsUiTokens.SpellMenu.PROF_PROFICIENT;
                    default -> WizardsAndBeastsUiTokens.SpellMenu.PROF_NOVICE_TEXT;
                };
                String profText = prof == Proficiency.MASTERED
                        ? profName + " (" + casts + " casts)"
                        : profName + " (" + casts + "/" + nextThreshold + ")";
                int profY = sel.getBaseDamage() > 0
                        ? infoY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_PROF_WITH_DAMAGE_Y)
                        : infoY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_PROF_NO_DAMAGE_Y);
                graphics.drawString(font, profText, infoX, profY, profColor, false);

                SpellRequirement req = sel.getRequirement();
                if (req != null && req != SpellRequirement.NONE) {
                    graphics.drawString(font, "Req: " + req.getDescription(),
                            infoX, profY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_REQ_Y),
                            WizardsAndBeastsUiTokens.SpellMenu.REQUIREMENT_TEXT, false);
                }

                graphics.drawString(font, "Sync corrections: " + data.getSyncCorrections(),
                        infoX, profY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_REQ_Y + 10),
                        WizardsAndBeastsUiTokens.SpellMenu.EMPTY_TEXT_COLOR, false);

                graphics.drawCenteredString(font, "Click slot to assign",
                        panelX + leftW + layout.s(WizardsAndBeastsUiTokens.SpellMenu.ASSIGN_HINT_CENTER_X),
                        panelY + panelH - layout.s(WizardsAndBeastsUiTokens.SpellMenu.ASSIGN_HINT_BOTTOM_OFFSET),
                        WizardsAndBeastsUiTokens.SpellMenu.ASSIGN_HINT_COLOR);
            }
        } else {
            graphics.drawCenteredString(font, "Select a spell",
                    panelX + leftW + layout.s(WizardsAndBeastsUiTokens.SpellMenu.ASSIGN_HINT_CENTER_X),
                    panelY + panelH - layout.s(WizardsAndBeastsUiTokens.SpellMenu.ASSIGN_HINT_BOTTOM_OFFSET),
                    WizardsAndBeastsUiTokens.SpellMenu.CATEGORY_TEXT);
        }
    }
}
