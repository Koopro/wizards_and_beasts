package at.koopro.neo.client.gui;

import at.koopro.neo.data.PlayerSpellData;
import at.koopro.neo.network.ClientSpellDataHolder;
import at.koopro.neo.spell.Proficiency;
import at.koopro.neo.spell.Spell;
import at.koopro.neo.spell.SpellRequirement;
import at.koopro.neo.spell.Spells;
import at.koopro.neo.util.GuiUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Non-widget painting for {@link SpellMenuScreen}.
 */
public final class SpellMenuRenderHelper {

    private SpellMenuRenderHelper() {}

    public static void renderFrame(GuiGraphics graphics, Font font, int width, int height,
                                   int panelW, int panelH, int leftW) {
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC1A1A2E);
        GuiUtils.drawBorder(graphics, panelX, panelY, panelW, panelH, 0xFF444466);
        graphics.drawCenteredString(font, "Spell Menu", panelX + panelW / 2, panelY + 6, 0xFFFFD700);
        graphics.fill(panelX + leftW, panelY + 20, panelX + leftW + 1, panelY + panelH - 5, 0xFF444466);
    }

    public static void renderSpellList(GuiGraphics graphics, Font font, int width, int height,
                                         int panelW, int panelH, int leftW, int maxVisible,
                                         List<SpellMenuScreen.SpellEntry> spellEntries,
                                         @Nullable String selectedSpellId, int scrollOffset) {
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;
        int listX = panelX + 4;
        int searchY = panelY + 4;
        int listY = searchY + 18;
        int startIdx = Math.max(0, scrollOffset);
        int endIdx = Math.min(spellEntries.size(), startIdx + maxVisible);

        if (spellEntries.isEmpty()) {
            graphics.drawString(font, "No spells found", listX, listY + 4, 0xFFAAAAAA, false);
        } else {
            for (int i = startIdx; i < endIdx; i++) {
                SpellMenuScreen.SpellEntry entry = spellEntries.get(i);
                int entryY = listY + (i - startIdx) * 18;

                if (entry.spell() == null) {
                    String catName = entry.category().name().replace('_', ' ');
                    graphics.drawString(font, catName, listX + 2, entryY + 4,
                            entry.category().getColor(), false);
                } else {
                    if (selectedSpellId != null && selectedSpellId.equals(entry.spell().getId())) {
                        graphics.fill(listX - 1, entryY - 1,
                                listX + leftW - 7, entryY + 17,
                                0x55FFFFFF);
                    }

                    PlayerSpellData data = ClientSpellDataHolder.get();
                    int casts = data.getCastCount(entry.spell().getId());
                    Proficiency prof = Proficiency.fromCastCount(casts);
                    String indicator = switch (prof) {
                        case MASTERED -> "\u2605";
                        case PROFICIENT -> "\u25C9";
                        default -> "\u25CB";
                    };
                    int profColor = switch (prof) {
                        case MASTERED -> 0xFFFFD700;
                        case PROFICIENT -> 0xFFFFFF44;
                        default -> 0xFF666666;
                    };
                    graphics.drawString(font, indicator, listX + leftW - 16, entryY + 4, profColor, false);
                }
            }
        }

        if (scrollOffset > 0) {
            graphics.drawCenteredString(font, "\u25B2", panelX + leftW / 2, panelY + 18, 0xFF888888);
        }
        if (scrollOffset < spellEntries.size() - maxVisible) {
            graphics.drawCenteredString(font, "\u25BC", panelX + leftW / 2, panelY + panelH - 12, 0xFF888888);
        }
    }

    public static void renderSelectedSpellPanel(GuiGraphics graphics, Font font, int width, int height,
                                                int panelW, int panelH, int leftW,
                                                @Nullable String selectedSpellId) {
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;
        int infoX = panelX + leftW + 10;

        if (selectedSpellId != null) {
            Spell sel = Spells.byId(selectedSpellId);
            if (sel != null) {
                PlayerSpellData data = ClientSpellDataHolder.get();
                int infoY = panelY + 140;

                graphics.drawString(font, sel.getDisplayName(), infoX, infoY,
                        sel.getCategory().getColor(), false);

                String catName = sel.getCategory().name().replace('_', ' ');
                graphics.drawString(font, catName, infoX, infoY + 12, 0xFF888888, false);

                float cooldownSecs = sel.getBaseCooldownTicks() / 20.0f;
                graphics.drawString(font, String.format("Cooldown: %.1fs", cooldownSecs),
                        infoX, infoY + 24, 0xFFAAAAAA, false);

                if (sel.getBaseDamage() > 0) {
                    graphics.drawString(font, String.format("Damage: %.1f", sel.getBaseDamage()),
                            infoX, infoY + 36, 0xFFAAAAAA, false);
                }

                int casts = data.getCastCount(sel.getId());
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
                    case MASTERED -> 0xFFFFD700;
                    case PROFICIENT -> 0xFFFFFF44;
                    default -> 0xFFAAAAAA;
                };
                String profText = prof == Proficiency.MASTERED
                        ? profName + " (" + casts + " casts)"
                        : profName + " (" + casts + "/" + nextThreshold + ")";
                int profY = sel.getBaseDamage() > 0 ? infoY + 48 : infoY + 36;
                graphics.drawString(font, profText, infoX, profY, profColor, false);

                SpellRequirement req = sel.getRequirement();
                if (req != null && req != SpellRequirement.NONE) {
                    graphics.drawString(font, "Req: " + req.getDescription(),
                            infoX, profY + 14, 0xFF777777, false);
                }

                graphics.drawCenteredString(font, "Click slot to assign",
                        panelX + leftW + 75, panelY + panelH - 16, 0xFF88FF88);
            }
        } else {
            graphics.drawCenteredString(font, "Select a spell",
                    panelX + leftW + 75, panelY + panelH - 16, 0xFF888888);
        }
    }
}
