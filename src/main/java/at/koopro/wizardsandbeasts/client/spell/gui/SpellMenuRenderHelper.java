package at.koopro.wizardsandbeasts.client.spell.gui;

import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui.Rect;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import at.koopro.wizardsandbeasts.client.hud.WandHudSprites;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellDataState;
import at.koopro.wizardsandbeasts.client.spell.ui.SpellPowerTooltip;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.SpellRequirement;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Painting for {@link SpellMenuScreen}: list rows, bound-spell rows and the details card, each into a
 * rectangle the screen took from {@link SpellMenuLayout}. Nothing here decides where anything goes.
 */
public final class SpellMenuRenderHelper {

    private static final UiSurface S = UiSurface.PAGE;

    /** Alpha a placeholder spell's icon is drawn at -- present, legible, obviously not available. */
    private static final float ALPHA_COMING_SOON = 0.35f;
    private static final int ICON = 12;
    /** Proficiency pips at the end of a row: three 2px squares, so the count reads without colour. */
    private static final int PIP = 2;
    private static final int PIPS_W = 3 * PIP + 2;
    /** Width of the direction word on a bound-spell row ("Right" is the widest in English). */
    private static final int DIRECTION_W = 28;

    private SpellMenuRenderHelper() {}

    /** A spell category as a player reads it. */
    static Component categoryName(SpellCategory category) {
        return Component.translatable("spell_category.wizards_and_beasts." + category.name().toLowerCase(Locale.ROOT));
    }

    /**
     * A category's hue made legible in the parchment well. The hues were picked for a dark ground --
     * utility's green is 1.4 : 1 on paper; clamped, the hue keeps its meaning and gains the depth to be seen.
     */
    static int hue(SpellCategory category) {
        return UiContrast.readableOn(category.getColor(), S.well(), UiContrast.AA_TEXT);
    }

    static int proficiencyPips(String spellId) {
        return switch (Proficiency.fromCastCount(ClientSpellDataState.get().getSuccessfulHits(spellId))) {
            case MASTERED -> 3;
            case PROFICIENT -> 2;
            default -> 1;
        };
    }

    /** The category heading inside the list: its name in its hue over a hairline of the same hue. */
    static void renderHeader(GuiGraphics g, Font font, SpellCategory category, Rect r) {
        int hue = hue(category);
        g.drawString(font, categoryName(category), r.x() + 2, r.y() + 5, hue, S.shadow());
        g.fill(r.x(), r.bottom() - 2, r.right(), r.bottom() - 1, (0x70 << 24) | (hue & 0xFFFFFF));
    }

    /**
     * One spell row. Right to left: proficiency pips (or a "Soon" chip), the chevron of the socket it is
     * bound to, its recharge time; the name gets what is left and ends in an ellipsis rather than
     * running under them. Returns whether the name had to be shortened, so the screen can tooltip it.
     *
     * @param boundSlot the first socket holding this spell, or -1
     */
    static boolean renderRow(GuiGraphics g, Font font, Spell spell, Rect r,
                             boolean hovered, boolean selected, boolean dragSource, int boundSlot) {
        if (selected || dragSource) {
            g.fill(r.x(), r.y(), r.right(), r.bottom(), S.selectWash());
            g.fill(r.x(), r.y(), r.x() + 2, r.bottom(), WizardsPalette.GILT_DARK);
        } else if (hovered) {
            g.fill(r.x(), r.y(), r.right(), r.bottom(), S.hoverWash());
        }

        boolean comingSoon = !spell.isImplemented();
        g.blitSprite(RenderPipelines.GUI_TEXTURED, WandHudSprites.spellIcon(spell.getId()),
                r.x() + 4, r.y() + (r.h() - ICON) / 2, ICON, ICON, comingSoon ? ALPHA_COMING_SOON : 1.0f);

        int textY = r.y() + (r.h() - font.lineHeight) / 2 + 1;
        int right = r.right() - 3;
        if (comingSoon) {
            Component soon = Component.translatable("gui.wizards_and_beasts.spell.coming_soon_short");
            right -= Ui.chipWidth(font, soon);
            Ui.chip(g, font, S, right, r.y() + (r.h() - Ui.CHIP_H) / 2, soon, Ui.Tone.MUTED);
        } else {
            right -= PIPS_W;
            pips(g, right, r.y() + (r.h() - PIP) / 2, proficiencyPips(spell.getId()));
            if (boundSlot >= 0) {
                right -= Ui.ARROW + 3;
                Ui.arrow(g, boundSlot, right, r.y() + (r.h() - Ui.ARROW) / 2, S.ink2());
            }
            String cooldown = cooldownSeconds(spell);
            if (cooldown != null) {
                right -= font.width(cooldown) + 3;
                g.drawString(font, cooldown, right, textY, S.warn(), S.shadow());
            }
        }

        int nameX = r.x() + 4 + ICON + 4;
        int colour = comingSoon ? S.ink3() : S.ink();
        return UiText.drawFit(g, font, S, Component.literal(GuiText.resolve(spell.getDisplayName())),
                nameX, textY, right - 4 - nameX, colour);
    }

    /** Three pips in a row, the first {@code filled} lit gilt. */
    static void pips(GuiGraphics g, int x, int y, int filled) {
        for (int i = 0; i < 3; i++) {
            int px = x + i * (PIP + 1);
            g.fill(px, y, px + PIP, y + PIP, i < filled ? WizardsPalette.GILT_DARK : WizardsPalette.PAGE_DEEP);
        }
    }

    /**
     * A socket's row under the wand: chevron, direction word, the bound spell's name or "Empty". It is a
     * second, larger target for the same socket (the layout hit-tests it as one), and the place a
     * socket's spell can be read in full.
     */
    static void renderBinding(GuiGraphics g, Font font, int slot, Rect r, @Nullable Spell spell,
                              boolean hovered, boolean dropTarget) {
        if (dropTarget) {
            g.fill(r.x(), r.y(), r.right(), r.bottom(), S.selectWash());
        } else if (hovered) {
            g.fill(r.x(), r.y(), r.right(), r.bottom(), S.hoverWash());
        }
        Ui.arrow(g, slot, r.x() + 2, r.y() + (r.h() - Ui.ARROW) / 2, S.ink2());
        int textY = r.y() + (r.h() - font.lineHeight) / 2 + 1;
        int x = r.x() + 2 + Ui.ARROW + 4;
        g.drawString(font, Component.translatable("gui.wizards_and_beasts.spell_menu.slot." + SpellMenuScreen.SLOT_KEYS[slot]),
                x, textY, S.ink3(), S.shadow());
        x += DIRECTION_W;
        Component name = spell == null
                ? Component.translatable("gui.wizards_and_beasts.spell_menu.slot_empty")
                : Component.literal(GuiText.resolve(spell.getDisplayName()));
        UiText.drawFit(g, font, S, name, x, textY, r.right() - 2 - x,
                spell == null ? S.ink3() : hue(spell.getCategory()));
    }

    /** Remaining recharge as "3.2s", or null when the spell is ready. */
    static @Nullable String cooldownSeconds(Spell spell) {
        Minecraft mc = Minecraft.getInstance();
        PlayerSpellData data = ClientSpellDataState.get();
        if (mc.level == null || !data.isOnCooldown(spell.getId(), mc.level.getGameTime())) {
            return null;
        }
        float sec = Math.max(0f, (data.getCooldownExpiry(spell.getId()) - mc.level.getGameTime()) / 20f);
        return String.format(Locale.ROOT, "%.1fs", sec);
    }

    /**
     * The details card's contents for {@code spell}, from {@code top} down, inside {@code area}'s width.
     * Only what the spell has: no damage row for a spell that deals none, no description where the
     * language file has none. Returns the y below the last thing drawn, so the screen can scroll it.
     *
     * @param bound the sockets this spell sits in, in loadout order
     */
    static int renderDetail(GuiGraphics g, Font font, Spell spell, Rect area, int top, List<Integer> bound) {
        PlayerSpellData data = ClientSpellDataState.get();
        int x = area.x();
        int w = area.w();
        int y = top;

        g.blitSprite(RenderPipelines.GUI_TEXTURED, WandHudSprites.spellIcon(spell.getId()), x, y, 16, 16,
                spell.isImplemented() ? 1.0f : ALPHA_COMING_SOON);
        UiText.drawFit(g, font, S, Component.literal(GuiText.resolve(spell.getDisplayName())),
                x + 20, y + 4, w - 20, hue(spell.getCategory()));
        y += 20;

        // Status chips: what the spell is, where it is, whether it can be cast now. Each says it in
        // words; the tone only repeats it.
        List<Component> chips = new ArrayList<>();
        List<Ui.Tone> tones = new ArrayList<>();
        chips.add(categoryName(spell.getCategory()));
        tones.add(Ui.Tone.NEUTRAL);
        if (!spell.isImplemented()) {
            chips.add(Component.translatable("gui.wizards_and_beasts.spell.coming_soon_short"));
            tones.add(Ui.Tone.MUTED);
        } else if (bound.isEmpty()) {
            chips.add(Component.translatable("gui.wizards_and_beasts.spell_menu.unbound"));
            tones.add(Ui.Tone.MUTED);
        } else {
            Component where = Component.empty();
            for (int i = 0; i < bound.size(); i++) {
                if (i > 0) {
                    where = where.copy().append(", ");
                }
                where = where.copy().append(Component.translatable(
                        "gui.wizards_and_beasts.spell_menu.slot." + SpellMenuScreen.SLOT_KEYS[bound.get(i)]));
            }
            chips.add(Component.translatable("gui.wizards_and_beasts.spell_menu.bound", where));
            tones.add(Ui.Tone.GOOD);
        }
        String recharge = cooldownSeconds(spell);
        if (recharge != null) {
            chips.add(Component.translatable("gui.wizards_and_beasts.spell_menu.recharging_chip", recharge));
            tones.add(Ui.Tone.WARN);
        }
        int cx = x;
        for (int i = 0; i < chips.size(); i++) {
            int cw = Ui.chipWidth(font, chips.get(i));
            if (cx > x && cx + cw > x + w) {
                cx = x;
                y += Ui.CHIP_H + 2;
            }
            cx += Ui.chip(g, font, S, cx, y, chips.get(i), tones.get(i)) + 3;
        }
        y += Ui.CHIP_H + WizardsMetrics.SPACE_S;
        Ui.hairline(g, S, x, y, w);
        y += WizardsMetrics.SPACE_S + 1;

        String descKey = "spell.wizards_and_beasts." + spell.getId() + ".description";
        if (I18n.exists(descKey)) {
            Component desc = Component.translatable(descKey);
            y = UiText.drawWrapped(g, font, S, UiText.Role.LABEL, desc, x, y, w, Integer.MAX_VALUE)
                    + WizardsMetrics.SPACE_S;
        }

        y = Ui.statRow(g, font, S, x, y, w, Component.translatable("gui.wizards_and_beasts.spell_menu.stat.cooldown"),
                Component.translatable("gui.wizards_and_beasts.spell_menu.value.seconds",
                        String.format(Locale.ROOT, "%.1f", spell.getBaseCooldownTicks() / 20.0f)));
        if (spell.getBaseDamage() > 0) {
            y = Ui.statRow(g, font, S, x, y, w, Component.translatable("gui.wizards_and_beasts.spell_menu.stat.damage"),
                    Component.literal(String.format(Locale.ROOT, "%.1f", spell.getBaseDamage())));
        }

        int casts = data.getSuccessfulHits(spell.getId());
        Proficiency prof = Proficiency.fromCastCount(casts);
        Component profName = Component.translatable("gui.wizards_and_beasts.spell_menu.prof."
                + prof.name().toLowerCase(Locale.ROOT));
        float fraction;
        Component profValue;
        if (prof == Proficiency.MASTERED) {
            fraction = 1.0f;
            profValue = Component.translatable("gui.wizards_and_beasts.spell_menu.prof_casts", profName, casts);
        } else {
            int from = prof == Proficiency.NOVICE ? 0 : Proficiency.PROFICIENT.getCastsRequired();
            int to = prof == Proficiency.NOVICE ? Proficiency.PROFICIENT.getCastsRequired()
                    : Proficiency.MASTERED.getCastsRequired();
            fraction = (casts - from) / (float) Math.max(1, to - from);
            profValue = Component.translatable("gui.wizards_and_beasts.spell_menu.prof_progress", profName, casts, to);
        }
        y = Ui.labelledBar(g, font, S, x, y + 2, w,
                Component.translatable("gui.wizards_and_beasts.spell_menu.stat.proficiency"), profValue,
                fraction, WizardsPalette.GILT);

        SpellRequirement req = spell.getRequirement();
        if (req != null && req != SpellRequirement.NONE) {
            boolean met = req.isMet(data);
            UiText.draw(g, font, S, UiText.Role.LABEL,
                    Component.translatable("gui.wizards_and_beasts.spell_menu.stat.requires"), x, y);
            y += WizardsMetrics.LINE_BODY;
            // The glyph says met or not; the colour only repeats it.
            Component line = Component.literal(met ? "✔ " : "✕ ").append(req.describe());
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(line, w);
            for (var l : lines) {
                g.drawString(font, l, x, y, met ? S.good() : S.bad(), S.shadow());
                y += WizardsMetrics.LINE_BODY;
            }
            y += WizardsMetrics.SPACE_XS;
        }

        // What the spell will actually hit for, from the class the server casts with -- the damage row
        // above is the number in the JSON; this is the one the player gets.
        for (Component line : SpellPowerTooltip.lines(spell)) {
            y = UiText.drawWrapped(g, font, S, UiText.Role.CAPTION, line, x, y, w, 4);
        }
        return y;
    }
}
