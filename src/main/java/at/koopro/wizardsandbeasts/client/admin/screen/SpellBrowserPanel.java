package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.config.SettingKind;
import at.koopro.wizardsandbeasts.admin.spell.SpellTestService;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.client.admin.SpellPreviewClient;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminConfirmDialog;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminEnumSelector;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminSectionHeader;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminText;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTextField;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTooltip;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminValueRow;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.client.hud.WandHudSprites;
import at.koopro.wizardsandbeasts.client.spell.hud.SpellDiamondOverlay;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellPayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellSummary;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Magic → Spells: a searchable, filterable list of every registered spell on the left, and the selected spell's
 * page on the right — its editable values (ordinary setting rows), resets, a server-validated test cast, a local
 * preview, a HUD preview, and its read-only facts.
 *
 * <p>Everything here comes from the server: the list and the page are {@code AdminSpellPayloads} replies, the
 * editable rows are setting descriptors, and edits become drafts sent through the footer's Apply like any other
 * setting. The filter's categories are the categories the listed spells actually have, so a new category
 * appears without a code change.
 */
@NullMarked
final class SpellBrowserPanel implements AdminPanel {

    private static final int ROW_H = 13;
    private static final int LIST_HEADER_H = 36;
    private static final int SCROLLBAR_W = 4;
    private static final int SUB_H = 14;
    private static final int LINE = 10;
    private static final long ACTION_SHOWN_MS = 10_000L;
    private static final String FILTER_ALL = "ALL";
    private static final String FILTER_DARK = "DARK";
    private static final String FILTER_DISABLED = "DISABLED";
    private static final String FILTER_CHANGED = "CHANGED";
    private static final String TARGET_PLAYER = "PLAYER:";

    /** Remembered across visits, like the section. {@link DarkArtsPanel} sets it to open a spell. */
    static @Nullable String selected;
    private static String search = "";
    private static String filter = FILTER_ALL;
    private static String target = SpellTestService.TargetMode.LOOKED_AT.name();

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
    private List<AdminSpellSummary> visible = List.of();
    /** The filter options the selector was built with; the list arriving can add categories. */
    private List<String> optionsBuilt = List.of();

    /** The detail reply the page was built from; a different one means rebuild. */
    private AdminSpellPayloads.@Nullable DetailReply shown;
    private final List<AdminValueRow> rows = new ArrayList<>();
    private final List<Placed> placed = new ArrayList<>();
    private int docHeight;
    private int valuesTop;
    private int actionsTop;
    private int testTop;
    private int hudTop;
    private int factsTop;
    private List<Line> factLines = List.of();

    /** A widget positioned in the scrolling page at {@code (docX, docY)}. */
    private record Placed(AbstractWidget widget, int docX, int docY) {}

    private record Line(FormattedCharSequence text, int color) {}

    @Override
    public AdminCategory section() {
        return AdminCategory.MAGIC;
    }

    @Override
    public void init(AdminPanelHost host, int x, int y, int w, int h) {
        this.host = host;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.listW = Mth.clamp(w * 38 / 100, 120, 190);
        this.detailX = x + listW + AdminTheme.PAD;
        this.detailW = w - listW - AdminTheme.PAD;
        Font font = host.font();

        host.addPanelWidget(AdminTextField.search(font, x, y, listW, search, text -> {
            search = text;
            listScroll = 0;
            refilter();
        }));
        optionsBuilt = filterOptions();
        if (!optionsBuilt.contains(filter)) {
            filter = FILTER_ALL;
        }
        host.addPanelWidget(new AdminEnumSelector(x, y + 18, listW, 14, optionsBuilt, filter, value -> {
            filter = value;
            listScroll = 0;
            refilter();
        }, SpellBrowserPanel::filterLabel));

        if (ClientAdminState.spellListStale()) {
            AdminClientRequests.spellList();
        }
        refilter();
        AdminSpellPayloads.DetailReply detail = ClientAdminState.spellDetail();
        if (selected != null && (detail == null || !detail.summary().id().equals(selected))) {
            AdminClientRequests.spellDetail(selected);
        }
        buildDetail(host, font);
    }

    // ── list ──

    /** The filter's options: everything, each category the listed spells use, then the cross-cutting views. */
    private static List<String> filterOptions() {
        Set<String> options = new LinkedHashSet<>();
        options.add(FILTER_ALL);
        for (AdminSpellSummary row : ClientAdminState.spellList()) {
            options.add(row.category());
        }
        options.add(FILTER_DARK);
        options.add(FILTER_DISABLED);
        options.add(FILTER_CHANGED);
        return List.copyOf(options);
    }

    private static Component filterLabel(String option) {
        return Component.translatableWithFallback("admin.wizards_and_beasts.spell_filter." + option.toLowerCase(Locale.ROOT),
                option.charAt(0) + option.substring(1).toLowerCase(Locale.ROOT).replace('_', ' '));
    }

    private void refilter() {
        String needle = search.trim().toLowerCase(Locale.ROOT);
        List<AdminSpellSummary> out = new ArrayList<>();
        for (AdminSpellSummary row : ClientAdminState.spellList()) {
            boolean kept = switch (filter) {
                case FILTER_ALL -> true;
                case FILTER_DARK -> row.dark();
                case FILTER_DISABLED -> !row.castAllowed();
                case FILTER_CHANGED -> row.overridden();
                default -> row.category().equals(filter);
            };
            if (kept && (needle.isEmpty() || row.id().contains(needle)
                    || GuiText.resolve(row.displayName()).toLowerCase(Locale.ROOT).contains(needle))) {
                out.add(row);
            }
        }
        visible = out;
        listScroll = Mth.clamp(listScroll, 0, maxListScroll());
    }

    private int listTop() {
        return y + LIST_HEADER_H;
    }

    private double maxListScroll() {
        return Math.max(0, visible.size() * ROW_H - (y + h - listTop()));
    }

    private void select(String spellId) {
        if (host == null || spellId.equals(selected)) {
            return;
        }
        selected = spellId;
        detailScroll = 0;
        AdminClientRequests.spellDetail(spellId);
        host.requestRebuild();
    }

    // ── detail page ──

    private void buildDetail(AdminPanelHost host, Font font) {
        rows.clear();
        placed.clear();
        factLines = List.of();
        AdminSpellPayloads.DetailReply detail = ClientAdminState.spellDetail();
        if (selected == null || detail == null || !detail.summary().id().equals(selected)) {
            shown = null;
            docHeight = 0;
            return;
        }
        shown = detail;
        int rowW = detailW - SCROLLBAR_W - 4;
        int doc = 34;

        valuesTop = doc;
        doc += SUB_H;
        for (AdminSettingDescriptor setting : ClientAdminState.spellSettings()) {
            rows.add(AdminRowFactory.build(host, font, setting, rowW, id -> refreshRows()));
            doc += AdminTheme.ROW_H;
        }

        actionsTop = doc + 4;
        String spellId = detail.summary().id();
        String category = detail.summary().category();
        AdminButton resetSpell = new AdminButton(0, 0, 92, 16, Component.translatable("admin.wizards_and_beasts.button.reset_spell"),
                AdminButton.Tone.NEUTRAL, () -> host.openDialog(new AdminConfirmDialog(
                        Component.translatable("admin.wizards_and_beasts.confirm.reset_spell.title"),
                        List.of(Component.translatable("admin.wizards_and_beasts.confirm.reset_spell.body",
                                Component.translatable(detail.summary().displayName()))),
                        Component.translatable("admin.wizards_and_beasts.button.reset_spell"), true,
                        () -> {
                            host.closeDialog();
                            AdminClientRequests.resetSpell(spellId, true);
                        },
                        host::closeDialog)));
        resetSpell.active = detail.summary().overridden();
        AdminButton resetCategory = new AdminButton(0, 0, 110, 16, Component.translatable("admin.wizards_and_beasts.button.reset_category"),
                AdminButton.Tone.NEUTRAL, () -> host.openDialog(new AdminConfirmDialog(
                        Component.translatable("admin.wizards_and_beasts.confirm.reset_category.title"),
                        List.of(Component.translatable("admin.wizards_and_beasts.confirm.reset_category.body", filterLabel(category))),
                        Component.translatable("admin.wizards_and_beasts.button.reset_category"), true,
                        () -> {
                            host.closeDialog();
                            AdminClientRequests.resetSpellCategory(category, true);
                        },
                        host::closeDialog)));
        place(host, resetSpell, 0, actionsTop);
        place(host, resetCategory, 96, actionsTop);
        doc = actionsTop + 22;

        testTop = doc;
        List<String> targets = targetOptions();
        if (!targets.contains(target)) {
            target = targets.get(0);
        }
        AdminEnumSelector targetSelector = new AdminEnumSelector(0, 0, Math.min(130, rowW - 124), 16, targets, target,
                value -> target = value, SpellBrowserPanel::targetLabel);
        targetSelector.setTooltip(Tooltip.create(Component.translatable("admin.wizards_and_beasts.spell_test.target.tooltip")));
        AdminButton test = new AdminButton(0, 0, 56, 16, Component.translatable("admin.wizards_and_beasts.button.test"),
                AdminButton.Tone.PRIMARY, () -> test(spellId));
        test.setTooltip(Tooltip.create(Component.translatable("admin.wizards_and_beasts.button.test.tooltip")));
        test.active = detail.summary().implemented() && !detail.summary().castType().startsWith("BEAM");
        AdminButton preview = new AdminButton(0, 0, 60, 16, Component.translatable("admin.wizards_and_beasts.button.preview"),
                AdminButton.Tone.NEUTRAL, () -> preview(spellId));
        preview.setTooltip(Tooltip.create(Component.translatable("admin.wizards_and_beasts.button.preview.tooltip")));
        int selectorW = targetSelector.getWidth();
        place(host, targetSelector, 0, testTop + SUB_H);
        place(host, test, selectorW + 4, testTop + SUB_H);
        place(host, preview, selectorW + 64, testTop + SUB_H);
        doc = testTop + SUB_H + 18 + LINE + 6;

        hudTop = doc;
        doc = hudTop + SUB_H + 11 + 96 + 6;

        factsTop = doc;
        List<Line> lines = new ArrayList<>();
        for (AdminSpellFact fact : detail.facts()) {
            Component value = fact.valueTranslatable() ? Component.translatable(fact.value()) : Component.literal(fact.value());
            Component text = Component.translatable(fact.labelKey()).append(": ").append(value);
            for (FormattedCharSequence line : font.split(text, rowW)) {
                lines.add(new Line(line, AdminTheme.INK_2));
            }
        }
        factLines = lines;
        docHeight = factsTop + SUB_H + lines.size() * LINE + 4;
        layoutDetail();
    }

    private void place(AdminPanelHost host, AbstractWidget widget, int docX, int docY) {
        host.addPanelWidget(widget);
        placed.add(new Placed(widget, docX, docY));
    }

    private double maxDetailScroll() {
        return Math.max(0, docHeight - h);
    }

    private void layoutDetail() {
        detailScroll = Mth.clamp(detailScroll, 0, maxDetailScroll());
        int top = y;
        int bottom = y + h;
        int rowW = detailW - SCROLLBAR_W - 4;
        for (int i = 0; i < rows.size(); i++) {
            int rowY = top + valuesTop + SUB_H + i * AdminTheme.ROW_H - (int) detailScroll;
            AdminValueRow row = rows.get(i);
            row.place(detailX, rowY, rowW);
            row.setVisible(rowY >= top && rowY + AdminTheme.ROW_H <= bottom);
        }
        for (Placed p : placed) {
            int widgetY = top + p.docY() - (int) detailScroll;
            p.widget().setPosition(detailX + p.docX(), widgetY);
            p.widget().visible = widgetY >= top && widgetY + p.widget().getHeight() <= bottom;
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

    // ── test and preview ──

    private static List<String> targetOptions() {
        List<String> options = new ArrayList<>(List.of(
                SpellTestService.TargetMode.LOOKED_AT.name(),
                SpellTestService.TargetMode.NEAREST_DUMMY.name(),
                SpellTestService.TargetMode.SELF.name()));
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            for (Player other : mc.level.players()) {
                if (other != mc.player && other.distanceToSqr(mc.player) <= 32 * 32) {
                    options.add(TARGET_PLAYER + other.getUUID());
                }
            }
        }
        return options;
    }

    private static Component targetLabel(String option) {
        if (option.startsWith(TARGET_PLAYER)) {
            Minecraft mc = Minecraft.getInstance();
            try {
                UUID id = UUID.fromString(option.substring(TARGET_PLAYER.length()));
                Player other = mc.level == null ? null : mc.level.getPlayerByUUID(id);
                return Component.translatable("admin.wizards_and_beasts.spell_test.target.player",
                        other == null ? "?" : other.getName().getString());
            } catch (IllegalArgumentException malformed) {
                return Component.literal("?");
            }
        }
        return Component.translatable("admin.wizards_and_beasts.spell_test.target." + option.toLowerCase(Locale.ROOT));
    }

    private void test(String spellId) {
        if (host == null) {
            return;
        }
        SpellTestService.TargetMode mode;
        UUID player = null;
        if (target.startsWith(TARGET_PLAYER)) {
            mode = SpellTestService.TargetMode.PLAYER;
            try {
                player = UUID.fromString(target.substring(TARGET_PLAYER.length()));
            } catch (IllegalArgumentException malformed) {
                return;
            }
        } else {
            SpellTestService.TargetMode parsed = SpellTestService.TargetMode.byName(target);
            mode = parsed == null ? SpellTestService.TargetMode.LOOKED_AT : parsed;
        }
        AdminClientRequests.testSpell(spellId, mode, player);
        host.peek(50);
    }

    private void preview(String spellId) {
        Spell spell = Spells.byId(spellId);
        if (host == null || spell == null) {
            return;
        }
        SpellPreviewClient.play(spell);
        host.peek(40);
    }

    // ── state and input ──

    @Override
    public void onServerState() {
        if (host == null) {
            return;
        }
        if (ClientAdminState.spellListStale()) {
            // A spell value changed (here or by another admin): its row and its page header both summarise it.
            AdminClientRequests.spellList();
            if (selected != null) {
                AdminClientRequests.spellDetail(selected);
            }
        }
        if (!filterOptions().equals(optionsBuilt)) {
            host.requestRebuild();
            return;
        }
        refilter();
        AdminSpellPayloads.DetailReply detail = ClientAdminState.spellDetail();
        if (detail != shown && selected != null && detail != null && detail.summary().id().equals(selected)) {
            host.requestRebuild();
            return;
        }
        refreshRows();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX < x || mouseX >= x + listW - SCROLLBAR_W || mouseY < listTop() || mouseY >= y + h) {
            return false;
        }
        int index = (int) ((mouseY - listTop() + listScroll) / ROW_H);
        if (index >= 0 && index < visible.size()) {
            select(visible.get(index).id());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaY) {
        if (mouseY < y || mouseY >= y + h) {
            return false;
        }
        if (mouseX >= x && mouseX < x + listW && mouseY >= listTop()) {
            listScroll = Mth.clamp(listScroll - deltaY * ROW_H * 3, 0, maxListScroll());
            return true;
        }
        if (mouseX >= detailX && mouseX < detailX + detailW && shown != null) {
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
        renderDetail(g, font, mouseX, mouseY);
        g.disableScissor();
    }

    private void renderList(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int top = listTop();
        int bottom = y + h;
        if (visible.isEmpty()) {
            g.drawString(font, AdminText.emptyList(!ClientAdminState.spellList().isEmpty(), ClientAdminState.spellListReceived(),
                    Component.translatable("admin.wizards_and_beasts.empty.spells")), x + 2, top + 2, AdminTheme.INK_3, false);
            return;
        }
        g.enableScissor(x, top, x + listW, bottom);
        int rowW = listW - SCROLLBAR_W - 2;
        for (int i = 0; i < visible.size(); i++) {
            int rowY = top + i * ROW_H - (int) listScroll;
            if (rowY + ROW_H < top || rowY > bottom) {
                continue;
            }
            AdminSpellSummary row = visible.get(i);
            boolean isSelected = row.id().equals(selected);
            boolean hovered = mouseX >= x && mouseX < x + rowW && mouseY >= rowY && mouseY < rowY + ROW_H && mouseY >= top;
            if (isSelected) {
                g.fill(x, rowY, x + rowW, rowY + ROW_H, AdminTheme.PAPER_SELECT);
                g.fill(x, rowY, x + 2, rowY + ROW_H, AdminTheme.GOLD);
            } else if (hovered) {
                g.fill(x, rowY, x + rowW, rowY + ROW_H, AdminTheme.ROW_HOVER);
            }
            g.blitSprite(RenderPipelines.GUI_TEXTURED, WandHudSprites.spellIcon(row.id()), x + 3, rowY + 1, 11, 11);
            String badges = (row.unforgivable() ? "☠" : row.dark() ? "◆" : "") + (row.overridden() ? "•" : "");
            int badgeW = font.width(badges);
            int ink = !row.castAllowed() ? AdminTheme.INK_3 : row.implemented() ? AdminTheme.INK : AdminTheme.INK_2;
            String name = AdminText.clip(font, GuiText.resolve(row.displayName()), rowW - 18 - badgeW - 4);
            g.drawString(font, name, x + 17, rowY + 3, ink, false);
            if (!row.castAllowed()) {
                g.fill(x + 17, rowY + 7, x + 17 + font.width(name), rowY + 8, AdminTheme.BAD);
            }
            if (!badges.isEmpty()) {
                g.drawString(font, badges, x + rowW - badgeW - 2, rowY + 3,
                        row.unforgivable() ? AdminTheme.RUBRIC : AdminTheme.GOLD_DARK, false);
            }
        }
        g.disableScissor();
        double max = maxListScroll();
        if (max > 0) {
            int trackX = x + listW - SCROLLBAR_W;
            int trackH = bottom - top;
            g.fill(trackX, top, trackX + SCROLLBAR_W, bottom, AdminTheme.PAPER_SHADE);
            int thumbH = Math.max(12, trackH * trackH / (visible.size() * ROW_H));
            int thumbY = top + (int) ((trackH - thumbH) * (listScroll / max));
            g.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, AdminTheme.GOLD);
        }
    }

    private void renderDetail(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int top = y - (int) detailScroll;
        int width = detailW - SCROLLBAR_W - 4;
        if (selected == null) {
            g.drawString(font, Component.translatable("admin.wizards_and_beasts.spell.pick"), detailX, y + 4, AdminTheme.INK_3, false);
            return;
        }
        AdminSpellPayloads.DetailReply detail = shown;
        if (detail == null) {
            g.drawString(font, Component.translatable("admin.wizards_and_beasts.status.loading"), detailX, y + 4, AdminTheme.INK_3, false);
            return;
        }
        AdminSpellSummary summary = detail.summary();
        // Header: name, badges, one-line summary.
        g.fill(detailX, top + 1, detailX + 3, top + 12, summary.color() | 0xFF000000);
        g.drawString(font, AdminText.clip(font, GuiText.resolve(summary.displayName()), width - 8), detailX + 6, top + 2, AdminTheme.RUBRIC, false);
        List<String> badges = new ArrayList<>();
        badges.add(filterLabel(summary.category()).getString());
        badges.add(summary.castType().toLowerCase(Locale.ROOT).replace('_', ' '));
        if (summary.unforgivable()) {
            badges.add(Component.translatable("admin.wizards_and_beasts.badge.unforgivable").getString());
        } else if (summary.dark()) {
            badges.add(Component.translatable("admin.wizards_and_beasts.badge.dark").getString());
        }
        badges.add(Component.translatable(summary.castAllowed() ? "admin.wizards_and_beasts.badge.castable"
                : summary.enabled() ? "admin.wizards_and_beasts.badge.refused" : "admin.wizards_and_beasts.badge.disabled").getString());
        if (!summary.implemented()) {
            badges.add(Component.translatable("admin.wizards_and_beasts.badge.not_implemented").getString());
        }
        g.drawString(font, AdminText.clip(font, String.join(" · ", badges), width), detailX, top + 13,
                summary.castAllowed() ? AdminTheme.INK_2 : AdminTheme.BAD, false);
        g.drawString(font, AdminText.clip(font, summary.summary(), width), detailX, top + 23, AdminTheme.INK_3, false);

        AdminSectionHeader.renderSub(g, font, detailX, top + valuesTop, width, Component.translatable("admin.wizards_and_beasts.spell.values"));
        for (AdminValueRow row : rows) {
            row.render(g, font, mouseX, mouseY, host != null && host.edits().isEdited(row.setting().id()));
        }

        AdminSectionHeader.renderSub(g, font, detailX, top + testTop, width, Component.translatable("admin.wizards_and_beasts.spell.test"));
        AdminSpellPayloads.ActionReply action = ClientAdminState.lastAction();
        if (action != null && Util.getMillis() - ClientAdminState.lastActionAt() < ACTION_SHOWN_MS) {
            g.drawString(font, AdminText.clip(font, Component.translatable(action.messageKey(), action.detail()).getString(), width),
                    detailX, top + testTop + SUB_H + 20, action.success() ? AdminTheme.GOOD : AdminTheme.BAD, false);
        } else {
            g.drawString(font, AdminText.clip(font, Component.translatable("admin.wizards_and_beasts.spell_test.hint").getString(), width),
                    detailX, top + testTop + SUB_H + 20, AdminTheme.INK_3, false);
        }

        AdminSectionHeader.renderSub(g, font, detailX, top + hudTop, width, Component.translatable("admin.wizards_and_beasts.spell.hud"));
        // A slow sweep so the preview shows the cooldown overlay too, from the live HUD renderer.
        float sweep = (Util.getMillis() % 4000L) / 4000.0f;
        // The HUD is drawn over the world, not over paper; a dusk backdrop keeps its light caption readable.
        g.fill(detailX, top + hudTop + SUB_H, detailX + 104, top + hudTop + SUB_H + 11 + 96 + 4, AdminTheme.FRAME_RAISED);
        SpellDiamondOverlay.renderPreview(g, detailX + 4, top + hudTop + SUB_H + 11, summary.id(), 1.0f - sweep);
        int noteX = detailX + 4 + 96 + 8;
        int noteY = top + hudTop + SUB_H + 14;
        for (FormattedCharSequence line : font.split(Component.translatable("admin.wizards_and_beasts.spell.hud.note"), Math.max(40, width - 108))) {
            g.drawString(font, line, noteX, noteY, AdminTheme.INK_3, false);
            noteY += LINE;
        }

        AdminSectionHeader.renderSub(g, font, detailX, top + factsTop, width, Component.translatable("admin.wizards_and_beasts.spell.facts"));
        int lineY = top + factsTop + SUB_H;
        for (Line line : factLines) {
            g.drawString(font, line.text(), detailX, lineY, line.color(), false);
            lineY += LINE;
        }

        double max = maxDetailScroll();
        if (max > 0) {
            int trackX = detailX + detailW - SCROLLBAR_W;
            g.fill(trackX, y, trackX + SCROLLBAR_W, y + h, AdminTheme.PAPER_SHADE);
            int thumbH = Math.max(12, h * h / Math.max(1, docHeight));
            int thumbY = y + (int) ((h - thumbH) * (detailScroll / max));
            g.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, AdminTheme.GOLD);
        }
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
        if (mouseX >= x && mouseX < x + listW - SCROLLBAR_W && mouseY >= listTop() && mouseY < y + h) {
            int index = (int) ((mouseY - listTop() + listScroll) / ROW_H);
            if (index >= 0 && index < visible.size()) {
                AdminSpellSummary row = visible.get(index);
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(GuiText.resolve(row.displayName())));
                lines.add(Component.literal(row.summary()));
                lines.add(Component.translatable("admin.wizards_and_beasts.spell.requires", row.requirement()));
                g.setComponentTooltipForNextFrame(host.font(), lines, mouseX, mouseY);
            }
        }
    }

    @Override
    public @Nullable Component crumb() {
        for (at.koopro.wizardsandbeasts.network.admin.AdminSpellSummary spell : ClientAdminState.spellList()) {
            if (spell.id().equals(selected)) {
                return Component.literal(spell.displayName());
            }
        }
        return null;
    }
}
