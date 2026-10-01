package at.koopro.wizardsandbeasts.client.admin.preview;

import at.koopro.wizardsandbeasts.client.admin.widget.AdminSectionHeader;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminText;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.client.heritage.gui.HeritageDossierRenderer;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.rules.HeritageRules;
import at.koopro.wizardsandbeasts.registry.ModAttributes;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import at.koopro.wizardsandbeasts.stats.PowerBandTable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A heritage as the Control Center shows it: the onboarding dossier, its traits, what it does to the five stats,
 * and what it does to the derived values — all read from the same sources the game uses
 * ({@link HeritageDossierRenderer}, {@link PowerBandTable}, the lineage's attribute modifiers), never a copy.
 *
 * <p>A measured, stateless renderer ({@link #measure} then {@link #render}) rather than a widget, so any panel can
 * place it in a scrolling page; a creature or form preview can follow the same shape. The lineage may be null:
 * the heritage is then shown through its first lineage, which is what the onboarding screen opens on.
 */
@NullMarked
public final class HeritagePreview {

    private static final int DOSSIER_H = 112;
    private static final int SUB_H = 14;
    private static final int LINE = 10;
    private static final String KEY = "admin.wizards_and_beasts.heritage_preview.";

    private HeritagePreview() {}

    /** One label/value line. {@code derived} lines are marked as computed, not stored. */
    public record Line(Component label, Component value, boolean derived) {}

    public static int measure(Font font, int w, Heritage heritage, @Nullable HeritageVariant variant) {
        HeritageVariant shown = lineage(heritage, variant);
        return DOSSIER_H + 6
                + HeritageDossierRenderer.measureTraits(font, w, shown) + 4
                + SUB_H + stats(shown).size() * LINE + 6
                + SUB_H + LINE + derived(heritage, shown).size() * LINE + 2;
    }

    public static void render(GuiGraphics g, Font font, int x, int y, int w, Heritage heritage,
                              @Nullable HeritageVariant variant) {
        HeritageVariant shown = lineage(heritage, variant);
        HeritageDossierRenderer.drawDossier(g, font, x, y, w, DOSSIER_H, heritage, shown, !HeritageRules.selectable(heritage));
        int cursor = y + DOSSIER_H + 6;
        HeritageDossierRenderer.drawTraits(g, font, x, cursor, w, heritage, shown);
        cursor += HeritageDossierRenderer.measureTraits(font, w, shown) + 4;

        AdminSectionHeader.renderSub(g, font, x, cursor, w, Component.translatable(KEY + "stats"));
        cursor += SUB_H;
        cursor = lines(g, font, x, cursor, w, stats(shown));
        cursor += 6;

        AdminSectionHeader.renderSub(g, font, x, cursor, w, Component.translatable(KEY + "derived"));
        cursor += SUB_H;
        g.drawString(font, AdminText.clip(font, Component.translatable(KEY + "derived.note").getString(), w),
                x, cursor, AdminTheme.INK_3, false);
        cursor += LINE;
        lines(g, font, x, cursor, w, derived(heritage, shown));
    }

    private static @Nullable HeritageVariant lineage(Heritage heritage, @Nullable HeritageVariant variant) {
        if (variant != null && variant.getParentHeritage() == heritage) {
            return variant;
        }
        return heritage.getSubtypes().isEmpty() ? null : heritage.getSubtypes().get(0);
    }

    /** The five stats: POWER's band from the table the roll uses, the trained three, Knowledge as derived. */
    public static List<Line> stats(@Nullable HeritageVariant variant) {
        List<Line> out = new ArrayList<>();
        for (PlayerStat stat : PlayerStat.values()) {
            Component value;
            if (stat == PlayerStat.POWER) {
                value = variant == null ? Component.translatable(KEY + "none")
                        : Component.translatable(KEY + "power_band", PowerBandTable.getBandMin(variant),
                                PowerBandTable.getBandMax(variant), PowerBandTable.getGrowthCap(variant));
            } else if (stat.isDerived()) {
                value = Component.translatable(KEY + "knowledge");
            } else {
                value = Component.translatable(KEY + "trained");
            }
            out.add(new Line(stat.displayName(), value, stat.isDerived()));
        }
        return out;
    }

    /**
     * What this lineage adds to the derived values, exactly as {@code HeritageAPI.applyStats} adds it (one additive
     * modifier each on health, speed and armour). The mod's three own attributes carry no heritage modifier; saying
     * so is the honest answer.
     */
    public static List<Line> derived(Heritage heritage, @Nullable HeritageVariant variant) {
        double health = variant == null ? heritage.getBaseHealth() : variant.getTotalHealth();
        double speed = variant == null ? heritage.getBaseSpeed() : variant.getTotalSpeed();
        double armor = variant == null ? heritage.getBaseArmor() : variant.getTotalArmor();
        List<Line> out = new ArrayList<>();
        out.add(new Line(Component.translatable(Attributes.MAX_HEALTH.value().getDescriptionId()), modifier(health), true));
        out.add(new Line(Component.translatable(Attributes.ARMOR.value().getDescriptionId()), modifier(armor), true));
        out.add(new Line(Component.translatable(Attributes.MOVEMENT_SPEED.value().getDescriptionId()), modifier(speed), true));
        Component untouched = Component.translatable(KEY + "unaffected");
        out.add(new Line(Component.translatable(ModAttributes.WAND_AFFINITY.get().getDescriptionId()), untouched, true));
        out.add(new Line(Component.translatable(ModAttributes.DARK_CORRUPTION.get().getDescriptionId()), untouched, true));
        out.add(new Line(Component.translatable(ModAttributes.BEAST_RESISTANCE.get().getDescriptionId()), untouched, true));
        return out;
    }

    private static Component modifier(double amount) {
        if (amount == 0.0) {
            return Component.translatable(KEY + "no_modifier");
        }
        String text = String.format(Locale.ROOT, "%+.3f", amount).replaceAll("0+$", "").replaceAll("\\.$", "");
        return Component.translatable(KEY + "modifier", text);
    }

    private static int lines(GuiGraphics g, Font font, int x, int y, int w, List<Line> lines) {
        int cursor = y;
        int labelW = Math.min(110, w / 2);
        for (Line line : lines) {
            String label = AdminText.clip(font, line.label().getString() + (line.derived() ? " ◇" : ""), labelW - 4);
            g.drawString(font, label, x, cursor, AdminTheme.INK_2, false);
            g.drawString(font, AdminText.clip(font, line.value().getString(), w - labelW), x + labelW, cursor,
                    AdminTheme.INK, false);
            cursor += LINE;
        }
        return cursor;
    }
}
