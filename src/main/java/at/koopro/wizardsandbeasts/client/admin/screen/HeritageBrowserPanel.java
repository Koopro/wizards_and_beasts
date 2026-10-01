package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.heritage.HeritageRuleSettings;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.client.admin.preview.HeritagePreview;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminEnumSelector;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminSectionHeader;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminText;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTooltip;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminValueRow;
import at.koopro.wizardsandbeasts.client.heritage.gui.HeritageSelectionScreen;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageTransformService;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.rules.HeritageRules;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Heritages → Heritages: every heritage the game registers on the left, the selected one's page on the right —
 * its rules (ordinary setting rows), an onboarding preview, its lineages and a {@link HeritagePreview}.
 *
 * <p>Nothing here names a heritage. The list is {@link Heritage#values()}, the rule rows are whichever
 * {@code heritage/<id>/<property>} settings the server registered for it, and the preview reads the dossier,
 * traits, power band and modifiers the onboarding and the game read. A heritage added to the enum appears here
 * with no screen change.
 */
@NullMarked
final class HeritageBrowserPanel implements AdminPanel {

    private static final int ROW_H = 15;
    private static final int SCROLLBAR_W = 4;
    private static final int SUB_H = 14;
    private static final int LINE = 10;
    private static final int HEADER_H = 36;
    private static final String KEY = "admin.wizards_and_beasts.heritage.";

    /** Remembered across visits, like the section. */
    static @Nullable Heritage selected;
    /** The lineage the preview shows; reset when the heritage changes. */
    private static @Nullable HeritageVariant previewLineage;

    private @Nullable AdminPanelHost host;
    private int x;
    private int y;
    private int w;
    private int h;
    private int listW;
    private int detailX;
    private int detailW;
    private double listScroll;
    private double detailScroll;

    private final List<AdminValueRow> rows = new ArrayList<>();
    private final List<Placed> placed = new ArrayList<>();
    private List<FormattedCharSequence> description = List.of();
    private int rulesTop;
    private int previewTop;
    private int lineagesTop;
    private int dossierTop;
    private int docHeight;

    private record Placed(AbstractWidget widget, int docX, int docY) {}

    @Override
    public AdminCategory section() {
        return AdminCategory.HERITAGES;
    }

    @Override
    public void init(AdminPanelHost host, int x, int y, int w, int h) {
        this.host = host;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.listW = Mth.clamp(w * 30 / 100, 96, 150);
        this.detailX = x + listW + AdminTheme.PAD;
        this.detailW = w - listW - AdminTheme.PAD;
        if (selected == null) {
            selected = Heritage.values()[0];
        }
        buildDetail(host, host.font(), selected);
    }

    private void select(Heritage heritage) {
        if (host == null || heritage == selected) {
            return;
        }
        selected = heritage;
        previewLineage = null;
        detailScroll = 0;
        host.requestRebuild();
    }

    // ── detail ──

    private void buildDetail(AdminPanelHost host, Font font, Heritage heritage) {
        rows.clear();
        placed.clear();
        int rowW = detailW - SCROLLBAR_W - 4;
        description = font.split(Component.translatable(heritage.getDescriptionTranslationKey()), rowW);
        int doc = HEADER_H + description.size() * LINE + 4;

        rulesTop = doc;
        doc += SUB_H;
        for (HeritageRuleSettings.Property property : HeritageRuleSettings.applicable(heritage)) {
            AdminSettingDescriptor setting = ClientAdminState.get(HeritageRuleSettings.id(heritage, property));
            if (setting != null) {
                rows.add(AdminRowFactory.build(host, font, setting, rowW, id -> refreshRows()));
                doc += AdminTheme.ROW_H;
            }
        }
        if (rows.isEmpty()) {
            doc += LINE;
        }

        previewTop = doc + 4;
        List<HeritageVariant> lineages = heritage.getSubtypes();
        if (previewLineage == null || previewLineage.getParentHeritage() != heritage) {
            previewLineage = lineages.isEmpty() ? null : lineages.get(0);
        }
        if (!lineages.isEmpty()) {
            List<String> ids = lineages.stream().map(HeritageVariant::getId).toList();
            AdminEnumSelector lineage = new AdminEnumSelector(0, 0, Math.min(140, rowW - 130), 16, ids,
                    previewLineage == null ? ids.get(0) : previewLineage.getId(), value -> {
                        previewLineage = HeritageVariant.byId(value);
                        host.requestRebuild();
                    }, HeritageBrowserPanel::lineageLabel);
            lineage.setTooltip(Tooltip.create(Component.translatable(KEY + "lineage.tooltip")));
            place(host, lineage, 0, previewTop + SUB_H);
        }
        AdminButton onboarding = new AdminButton(0, 0, 122, 16, Component.translatable(KEY + "preview_onboarding"),
                AdminButton.Tone.NEUTRAL, HeritageBrowserPanel::openOnboardingPreview);
        onboarding.setTooltip(Tooltip.create(Component.translatable(KEY + "preview_onboarding.tooltip")));
        place(host, onboarding, Math.max(0, rowW - 122), previewTop + SUB_H);
        doc = previewTop + SUB_H + 20;

        lineagesTop = doc;
        doc += SUB_H + lineages.size() * LINE + 6;

        dossierTop = doc;
        int previewW = Math.min(rowW, 240);
        docHeight = dossierTop + HeritagePreview.measure(font, previewW, heritage, previewLineage) + 6;
        layoutDetail();
    }

    private static Component lineageLabel(String id) {
        HeritageVariant variant = HeritageVariant.byId(id);
        return variant == null ? Component.literal(id) : Component.literal(variant.getDisplayName());
    }

    /**
     * The onboarding screen exactly as a new player would meet it under the current rules, in preview mode:
     * nothing is sent, ESC comes back here. The administrator's own heritage is never touched.
     */
    private static void openOnboardingPreview() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen current = minecraft.screen;
        if (current != null) {
            minecraft.setScreen(HeritageSelectionScreen.preview(current));
        }
    }

    private void place(AdminPanelHost host, AbstractWidget widget, int docX, int docY) {
        host.addPanelWidget(widget);
        placed.add(new Placed(widget, docX, docY));
    }

    private double maxDetailScroll() {
        return Math.max(0, docHeight - h);
    }

    private double maxListScroll() {
        return Math.max(0, Heritage.values().length * ROW_H - h);
    }

    private void layoutDetail() {
        detailScroll = Mth.clamp(detailScroll, 0, maxDetailScroll());
        int rowW = detailW - SCROLLBAR_W - 4;
        for (int i = 0; i < rows.size(); i++) {
            int rowY = y + rulesTop + SUB_H + i * AdminTheme.ROW_H - (int) detailScroll;
            AdminValueRow row = rows.get(i);
            row.place(detailX, rowY, rowW);
            row.setVisible(rowY >= y && rowY + AdminTheme.ROW_H <= y + h);
        }
        for (Placed p : placed) {
            int widgetY = y + p.docY() - (int) detailScroll;
            p.widget().setPosition(detailX + p.docX(), widgetY);
            p.widget().visible = widgetY >= y && widgetY + p.widget().getHeight() <= y + h;
        }
    }

    private void refreshRows() {
        if (host == null) {
            return;
        }
        for (AdminValueRow row : rows) {
            AdminRowFactory.refresh(host, row);
        }
    }

    // ── state and input ──

    @Override
    public void onServerState() {
        refreshRows();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX < x || mouseX >= x + listW - SCROLLBAR_W || mouseY < y || mouseY >= y + h) {
            return false;
        }
        int index = (int) ((mouseY - y + listScroll) / ROW_H);
        Heritage[] all = Heritage.values();
        if (index >= 0 && index < all.length) {
            select(all[index]);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaY) {
        if (mouseY < y || mouseY >= y + h) {
            return false;
        }
        if (mouseX >= x && mouseX < x + listW) {
            listScroll = Mth.clamp(listScroll - deltaY * ROW_H * 2, 0, maxListScroll());
            return true;
        }
        if (mouseX >= detailX && mouseX < detailX + detailW) {
            detailScroll = Mth.clamp(detailScroll - deltaY * AdminTheme.ROW_H, 0, maxDetailScroll());
            layoutDetail();
            return true;
        }
        return false;
    }

    // ── drawing ──

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (host == null) {
            return;
        }
        Font font = host.font();
        renderList(g, font, mouseX, mouseY);
        g.fill(detailX - AdminTheme.PAD / 2 - 1, y, detailX - AdminTheme.PAD / 2, y + h, AdminTheme.PAPER_RULE);
        g.enableScissor(detailX, y, detailX + detailW, y + h);
        if (selected != null) {
            renderDetail(g, font, mouseX, mouseY, selected);
        }
        g.disableScissor();
    }

    /** The heritage's mark: a crest in its own colour with its initial — the mod ships no heritage icons. */
    static void drawCrest(GuiGraphics g, Font font, int cx, int cy, Heritage heritage) {
        int color = heritage.getColor() | 0xFF000000;
        g.fill(cx, cy, cx + 11, cy + 9, AdminTheme.FRAME);
        g.fill(cx + 1, cy + 1, cx + 10, cy + 9, color);
        g.fill(cx + 2, cy + 9, cx + 9, cy + 11, color);
        g.fill(cx + 4, cy + 11, cx + 7, cy + 12, color);
        String initial = heritage.getId().substring(0, 1).toUpperCase(Locale.ROOT);
        boolean light = ((color >> 16 & 0xFF) + (color >> 8 & 0xFF) + (color & 0xFF)) > 420;
        g.drawString(font, initial, cx + 6 - font.width(initial) / 2, cy + 1, light ? AdminTheme.INK : 0xFFFFFFFF, false);
    }

    private void renderList(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int rowW = listW - SCROLLBAR_W - 2;
        g.enableScissor(x, y, x + listW, y + h);
        Heritage[] all = Heritage.values();
        for (int i = 0; i < all.length; i++) {
            Heritage heritage = all[i];
            int rowY = y + i * ROW_H - (int) listScroll;
            if (rowY + ROW_H < y || rowY > y + h) {
                continue;
            }
            boolean isSelected = heritage == selected;
            boolean hovered = mouseX >= x && mouseX < x + rowW && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (isSelected) {
                g.fill(x, rowY, x + rowW, rowY + ROW_H, AdminTheme.PAPER_SELECT);
                g.fill(x, rowY, x + 2, rowY + ROW_H, AdminTheme.GOLD);
            } else if (hovered) {
                g.fill(x, rowY, x + rowW, rowY + ROW_H, AdminTheme.ROW_HOVER);
            }
            drawCrest(g, font, x + 4, rowY + 2, heritage);
            boolean open = HeritageRules.selectable(heritage);
            String name = AdminText.clip(font, Component.literal(heritage.getDisplayName()).getString(), rowW - 30);
            g.drawString(font, name, x + 19, rowY + 4, open ? AdminTheme.INK : AdminTheme.INK_3, false);
            if (!open) {
                g.drawString(font, "✕", x + rowW - 8, rowY + 4, AdminTheme.BAD, false);
            }
        }
        g.disableScissor();
    }

    private void renderDetail(GuiGraphics g, Font font, int mouseX, int mouseY, Heritage heritage) {
        int top = y - (int) detailScroll;
        int width = detailW - SCROLLBAR_W - 4;

        drawCrest(g, font, detailX, top + 1, heritage);
        g.drawString(font, AdminText.clip(font, Component.literal(heritage.getDisplayName()).getString(), width - 16),
                detailX + 16, top + 3, AdminTheme.RUBRIC, false);
        g.drawString(font, AdminText.clip(font, String.join(" · ", badges(heritage)), width), detailX, top + 16,
                HeritageRules.selectable(heritage) ? AdminTheme.INK_2 : AdminTheme.BAD, false);
        int lineY = top + HEADER_H - 10;
        for (FormattedCharSequence line : description) {
            g.drawString(font, line, detailX, lineY, AdminTheme.INK_3, false);
            lineY += LINE;
        }

        AdminSectionHeader.renderSub(g, font, detailX, top + rulesTop, width, Component.translatable(KEY + "rules"));
        if (rows.isEmpty()) {
            g.drawString(font, Component.translatable("admin.wizards_and_beasts.status.loading"), detailX,
                    top + rulesTop + SUB_H, AdminTheme.INK_3, false);
        }
        for (AdminValueRow row : rows) {
            row.render(g, font, mouseX, mouseY, host != null && host.edits().isEdited(row.setting().id()));
        }

        AdminSectionHeader.renderSub(g, font, detailX, top + previewTop, width, Component.translatable(KEY + "preview"));

        AdminSectionHeader.renderSub(g, font, detailX, top + lineagesTop, width, Component.translatable(KEY + "lineages"));
        int ly = top + lineagesTop + SUB_H;
        for (HeritageVariant variant : heritage.getSubtypes()) {
            String tags = variant.hasTag(HeritageTransformService.TAG_TRANSFORMATION)
                    ? "  — " + Component.translatable(KEY + "badge.transforms").getString() : "";
            g.drawString(font, AdminText.clip(font, Component.literal(variant.getDisplayName()).getString() + tags, width),
                    detailX + 6, ly, variant == previewLineage ? AdminTheme.INK : AdminTheme.INK_2, false);
            ly += LINE;
        }

        HeritagePreview.render(g, font, detailX, top + dossierTop, Math.min(width, 240), heritage, previewLineage);

        double max = maxDetailScroll();
        if (max > 0) {
            int trackX = detailX + detailW - SCROLLBAR_W;
            g.fill(trackX, y, trackX + SCROLLBAR_W, y + h, AdminTheme.PAPER_SHADE);
            int thumbH = Math.max(12, h * h / Math.max(1, docHeight));
            int thumbY = y + (int) ((h - thumbH) * (detailScroll / max));
            g.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, AdminTheme.GOLD);
        }
    }

    /** The header's status line: onboarding status first, then what the heritage is. */
    static List<String> badges(Heritage heritage) {
        List<String> out = new ArrayList<>();
        String status = HeritageRules.selectable(heritage) ? "selectable"
                : HeritageRules.rule(heritage).selectable().isPresent() ? "closed" : "coming_soon";
        out.add(Component.translatable(KEY + "badge." + status).getString());
        out.add(heritage.getMagicSource().getDisplayName());
        out.add(heritage.getSizeCategory().getDisplayName());
        if (!heritage.canUseWand()) {
            out.add(Component.translatable(KEY + "badge.no_wand").getString());
        }
        if (HeritageTransformService.SERVED.contains(heritage)) {
            out.add(Component.translatable(HeritageRules.transformationAllowed(heritage)
                    ? KEY + "badge.transforms" : KEY + "badge.transform_closed").getString());
        }
        return out;
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
        if (host == null) {
            return;
        }
        for (AdminValueRow row : rows) {
            if (row.labelHovered(mouseX, mouseY) && mouseY >= y && mouseY < y + h) {
                g.setTooltipForNextFrame(host.font(), AdminTooltip.forSetting(host.font(), row.setting()), mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public @Nullable Component crumb() {
        return selected == null ? null : Component.literal(selected.getDisplayName());
    }
}
