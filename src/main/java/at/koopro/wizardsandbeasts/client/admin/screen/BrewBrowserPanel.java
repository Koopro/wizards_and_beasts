package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.brew.BrewAdminService;
import at.koopro.wizardsandbeasts.admin.brew.BrewSettingIds;
import at.koopro.wizardsandbeasts.admin.config.SettingKind;
import at.koopro.wizardsandbeasts.brew.tuning.BrewEffectText;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminBrewState;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminEnumSelector;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminSectionHeader;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminText;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTextField;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTooltip;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminValueRow;
import at.koopro.wizardsandbeasts.network.admin.AdminBrewPayloads.BrewSummary;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Brewing → Brews: every brew the server has on the left (search; effect, equipment, difficulty and enabled
 * filters), the selected brew's page on the right — whether it is enabled, its drink-time effects in an
 * {@link EffectListEditor}, a client-side preview, its recipe's editable values, and the components it is built from.
 *
 * <p>The list and page are server replies; every editable value is an ordinary setting descriptor, edited as a draft
 * and sent through Apply. The preview is drawn from the draft on this client alone — nothing is applied to anyone.
 */
@NullMarked
final class BrewBrowserPanel implements AdminPanel {

    private static final int ROW_H = 13;
    private static final int LIST_HEADER_H = 54;
    private static final int SCROLLBAR_W = 4;
    private static final int SUB_H = 14;
    private static final int LINE = 10;
    private static final String KEY = "admin.wizards_and_beasts.brew.";
    private static final String ALL = "ALL";

    static @Nullable String selected;
    static boolean reveal;
    private static String search = "";
    private static String effectFilter = ALL;
    private static String kindFilter = ALL;
    private static String difficultyFilter = ALL;
    private static String enabledFilter = ALL;

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
    private List<BrewSummary> visible = List.of();
    private List<String> effectsBuilt = List.of();

    private BrewAdminService.@Nullable Detail shown;
    private @Nullable EffectListEditor editor;
    private final List<PlacedRow> rows = new ArrayList<>();
    private final List<Placed> placed = new ArrayList<>();
    private final List<Text> texts = new ArrayList<>();
    private final List<Header> headers = new ArrayList<>();
    private final List<ItemMark> items = new ArrayList<>();
    private int previewTop = -1;
    private int previewHeight;
    private int docHeight;

    private record Placed(AbstractWidget widget, int docX, int docY) {}

    private record PlacedRow(AdminValueRow row, int docY) {}

    private record Text(FormattedCharSequence text, int docX, int docY, int color) {}

    private record Header(Component title, int docY) {}

    private record ItemMark(ItemStack stack, int docX, int docY) {}

    @Override
    public AdminCategory section() {
        return AdminCategory.BREWING;
    }

    @Override
    public void init(AdminPanelHost host, int x, int y, int w, int h) {
        this.host = host;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.listW = Mth.clamp(w * 34 / 100, 110, 170);
        this.detailX = x + listW + AdminTheme.PAD;
        this.detailW = w - listW - AdminTheme.PAD;
        Font font = host.font();

        host.addPanelWidget(AdminTextField.search(font, x, y, listW, search, text -> {
            search = text;
            listScroll = 0;
            refilter();
        }));
        effectsBuilt = effectOptions();
        if (!effectsBuilt.contains(effectFilter)) {
            effectFilter = ALL;
        }
        int half = (listW - 4) / 2;
        host.addPanelWidget(new AdminEnumSelector(x, y + 18, half, 14, effectsBuilt, effectFilter, value -> {
            effectFilter = value;
            listScroll = 0;
            refilter();
        }, BrewBrowserPanel::effectFilterLabel));
        host.addPanelWidget(new AdminEnumSelector(x + half + 4, y + 18, half, 14,
                List.of(ALL, "PEWTER", "BRASS", "COPPER", "SILVER", "NO_RECIPE"), kindFilter, value -> {
                    kindFilter = value;
                    listScroll = 0;
                    refilter();
                }, value -> Component.translatable(KEY + "filter." + value.toLowerCase(Locale.ROOT))));
        host.addPanelWidget(new AdminEnumSelector(x, y + 36, half, 14,
                List.of(ALL, "BASIC", "STANDARD", "ADVANCED", "MASTER", "UNBREWABLE"), difficultyFilter, value -> {
                    difficultyFilter = value;
                    listScroll = 0;
                    refilter();
                }, value -> value.equals(ALL) ? Component.translatable(KEY + "filter.any_difficulty")
                        : Component.translatable(KEY + "difficulty." + value.toLowerCase(Locale.ROOT))));
        host.addPanelWidget(new AdminEnumSelector(x + half + 4, y + 36, half, 14, List.of(ALL, "ENABLED", "DISABLED"),
                enabledFilter, value -> {
                    enabledFilter = value;
                    listScroll = 0;
                    refilter();
                }, value -> Component.translatable(KEY + "filter.state_" + value.toLowerCase(Locale.ROOT))));

        if (ClientAdminBrewState.listStale()) {
            AdminClientRequests.brewList();
            if (selected != null) {
                AdminClientRequests.brewDetail(selected);
            }
        }
        refilter();
        revealSelected();
        BrewAdminService.Detail detail = ClientAdminBrewState.detail();
        if (selected != null && (detail == null || !detail.summary().id().equals(selected))) {
            AdminClientRequests.brewDetail(selected);
        }
        buildDetail(host, font);
    }

    // ── list ──

    private static List<String> effectOptions() {
        Set<String> out = new LinkedHashSet<>();
        out.add(ALL);
        ClientAdminBrewState.brews().stream().flatMap(b -> b.effects().stream()).sorted().forEach(out::add);
        return List.copyOf(out);
    }

    private static Component effectFilterLabel(String option) {
        if (option.equals(ALL)) {
            return Component.translatable(KEY + "filter.any_effect");
        }
        Identifier id = Identifier.tryParse(option);
        MobEffect effect = id == null ? null : BuiltInRegistries.MOB_EFFECT.getValue(id);
        return effect == null ? Component.literal(option) : effect.getDisplayName();
    }

    private void refilter() {
        String needle = search.trim().toLowerCase(Locale.ROOT);
        List<BrewSummary> out = new ArrayList<>();
        for (BrewSummary row : ClientAdminBrewState.brews()) {
            boolean kept = (effectFilter.equals(ALL) || row.effects().contains(effectFilter))
                    && matchesKind(row)
                    && (difficultyFilter.equals(ALL) || row.difficulty().equals(difficultyFilter))
                    && (enabledFilter.equals(ALL) || row.enabled() == enabledFilter.equals("ENABLED"));
            if (kept && (needle.isEmpty() || row.id().contains(needle)
                    || Component.translatable(row.nameKey()).getString().toLowerCase(Locale.ROOT).contains(needle))) {
                out.add(row);
            }
        }
        visible = out;
        listScroll = Mth.clamp(listScroll, 0, maxListScroll());
    }

    private static boolean matchesKind(BrewSummary row) {
        return switch (kindFilter) {
            case "SILVER" -> row.silver();
            case "NO_RECIPE" -> row.recipeId().isEmpty();
            case ALL -> true;
            default -> row.tier().equals(kindFilter);
        };
    }

    private void revealSelected() {
        if (!reveal || selected == null) {
            return;
        }
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i).id().equals(selected)) {
                listScroll = Mth.clamp(i * ROW_H - (y + h - listTop()) / 2.0, 0, maxListScroll());
                reveal = false;
                return;
            }
        }
    }

    private int listTop() {
        return y + LIST_HEADER_H;
    }

    private double maxListScroll() {
        return Math.max(0, visible.size() * ROW_H - (y + h - listTop()));
    }

    private void select(String id) {
        if (host == null || id.equals(selected)) {
            return;
        }
        selected = id;
        detailScroll = 0;
        AdminClientRequests.brewDetail(id);
        host.requestRebuild();
    }

    // ── detail ──

    static ItemStack stackOf(String brewId) {
        ItemStack stack = new ItemStack(ConsumableItemRegistry.BREW.get());
        stack.set(ModDataComponents.BREW_ID.get(), brewId);
        return stack;
    }

    private void buildDetail(AdminPanelHost host, Font font) {
        rows.clear();
        placed.clear();
        texts.clear();
        headers.clear();
        items.clear();
        editor = null;
        previewTop = -1;
        BrewAdminService.Detail detail = ClientAdminBrewState.detail();
        if (selected == null || detail == null || !detail.summary().id().equals(selected)) {
            shown = null;
            docHeight = 0;
            return;
        }
        shown = detail;
        BrewSummary summary = detail.summary();
        int rowW = detailW - SCROLLBAR_W - 4;
        int doc = 26;

        doc = header(doc, Component.translatable(KEY + "brew"));
        doc = row(host, font, BrewSettingIds.brew(summary.id(), BrewSettingIds.ENABLED), doc, rowW);

        doc = header(doc + 4, Component.translatable(KEY + "effects"));
        AdminSettingDescriptor effects = ClientAdminState.get(BrewSettingIds.brew(summary.id(), BrewSettingIds.EFFECTS));
        if (effects != null) {
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "effects_hint"), AdminTheme.INK_3);
            EffectListEditor list = new EffectListEditor(host, effects);
            editor = list;
            doc += list.build(doc, rowW, this::place) + 2;
        } else {
            // Not one editable list: either the brew is built from bespoke components (Felix, Polyjuice) or its
            // effects are split across several lists. Say which, rather than implying it does nothing.
            doc = wrap(font, doc, rowW, Component.translatable(summary.effectText().isEmpty() ? KEY + "effects_components" : KEY + "effects_fixed",
                    summary.effectText()), AdminTheme.INK_3);
        }

        doc = header(doc + 4, Component.translatable(KEY + "preview"));
        previewTop = doc;
        previewHeight = previewHeight(font);
        doc += previewHeight;

        doc = header(doc + 4, Component.translatable(KEY + "recipe"));
        doc = facts(font, doc, rowW, detail.recipe());
        for (AdminSettingDescriptor setting : detail.settings()) {
            BrewSettingIds.Parsed parsed = BrewSettingIds.parse(setting.id());
            if (parsed == null || parsed.kind() != BrewSettingIds.Kind.RECIPE) {
                continue;
            }
            if (parsed.item() != null) {
                // The row's label is the shared "Ingredient count"; the item it counts is shown above it.
                Identifier itemId = Identifier.tryParse(parsed.item());
                Item item = itemId == null ? null : BuiltInRegistries.ITEM.getValue(itemId);
                if (item != null) {
                    items.add(new ItemMark(new ItemStack(item), 0, doc));
                    texts.add(new Text(Component.translatable(item.getDescriptionId()).getVisualOrderText(), 20, doc + 4, AdminTheme.RUBRIC));
                    doc += 18;
                }
            }
            doc = row(host, font, setting.id(), doc, rowW);
        }

        doc = header(doc + 4, Component.translatable(KEY + "components"));
        for (String component : detail.components()) {
            doc = wrap(font, doc, rowW, Component.literal("• " + component), AdminTheme.INK_2);
        }
        doc = wrap(font, doc, rowW, Component.translatable(KEY + "components_note"), AdminTheme.INK_3);
        docHeight = doc + 6;
        layoutDetail();
    }

    private int header(int doc, Component title) {
        headers.add(new Header(title, doc));
        return doc + SUB_H;
    }

    private int row(AdminPanelHost host, Font font, Identifier id, int doc, int rowW) {
        AdminSettingDescriptor setting = ClientAdminState.get(id);
        if (setting == null) {
            return doc;
        }
        rows.add(new PlacedRow(AdminRowFactory.build(host, font, setting, rowW, changed -> refreshRows()), doc));
        return doc + AdminTheme.ROW_H;
    }

    private int facts(Font font, int doc, int rowW, List<AdminSpellFact> facts) {
        for (AdminSpellFact fact : facts) {
            Component value = fact.valueTranslatable() ? Component.translatable(fact.value()) : Component.literal(fact.value());
            doc = wrap(font, doc, rowW, Component.translatable(fact.labelKey()).append(": ").append(value), AdminTheme.INK_2);
        }
        return doc;
    }

    private int wrap(Font font, int doc, int width, Component text, int color) {
        for (FormattedCharSequence line : font.split(text, width)) {
            texts.add(new Text(line, 0, doc, color));
            doc += LINE;
        }
        return doc;
    }

    private void place(AbstractWidget widget, int docX, int docY) {
        if (host != null) {
            host.addPanelWidget(widget);
        }
        placed.add(new Placed(widget, docX, docY));
    }

    private double maxDetailScroll() {
        return Math.max(0, docHeight - h);
    }

    private void layoutDetail() {
        detailScroll = Mth.clamp(detailScroll, 0, maxDetailScroll());
        int rowW = detailW - SCROLLBAR_W - 4;
        for (PlacedRow placedRow : rows) {
            int rowY = y + placedRow.docY() - (int) detailScroll;
            placedRow.row().place(detailX, rowY, rowW);
            placedRow.row().setVisible(rowY >= y && rowY + AdminTheme.ROW_H <= y + h);
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
        for (PlacedRow placedRow : rows) {
            AdminRowFactory.refresh(host, placedRow.row());
        }
    }

    // ── preview (client-only; nothing is applied to anyone) ──

    /** The rows being edited, as they would apply: enabled and valid lines only. */
    private List<BrewEffectText.Line> previewLines() {
        List<BrewEffectText.Line> out = new ArrayList<>();
        if (editor != null) {
            for (EffectListEditor.Row row : editor.rows()) {
                BrewEffectText.Line line = row.line();
                if (line != null && line.enabled()) {
                    out.add(line);
                }
            }
        }
        return out;
    }

    private int previewHeight(Font font) {
        int tooltip = tooltipLines().size() * LINE;
        return Math.max(20, tooltip) + 4 + Math.max(1, previewLines().size()) * 20 + LINE + 4;
    }

    private List<Component> tooltipLines() {
        Minecraft mc = Minecraft.getInstance();
        if (selected == null || mc.level == null) {
            return List.of();
        }
        return stackOf(selected).getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
    }

    private void renderPreview(GuiGraphics g, Font font, int top, int width) {
        if (selected == null || previewTop < 0) {
            return;
        }
        int py = top + previewTop;
        g.renderItem(stackOf(selected), detailX, py);
        int ty = py;
        for (Component line : tooltipLines()) {
            g.drawString(font, AdminText.clip(font, line.getString(), width - 22), detailX + 22, ty, AdminTheme.INK, false);
            ty += LINE;
        }
        int ey = Math.max(py + 20, ty) + 4;
        List<BrewEffectText.Line> lines = previewLines();
        if (lines.isEmpty()) {
            g.drawString(font, Component.translatable(editor == null ? KEY + "preview_components" : KEY + "preview_none"),
                    detailX, ey + 4, AdminTheme.INK_3, false);
            ey += 20;
        }
        for (BrewEffectText.Line line : lines) {
            Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.get(line.effect());
            if (holder.isEmpty()) {
                continue;
            }
            g.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(holder.get()), detailX, ey, 18, 18);
            MobEffectInstance instance = new MobEffectInstance(holder.get(), line.duration(), line.amplifier(), line.ambient(), true, true);
            Component name = holder.get().value().getDisplayName().copy()
                    .append(line.amplifier() > 0 ? Component.literal(" ").append(Component.translatable("potion.potency." + line.amplifier())) : Component.empty());
            g.drawString(font, AdminText.clip(font, name.getString(), width - 26), detailX + 22, ey + 1, AdminTheme.INK, false);
            g.drawString(font, MobEffectUtil.formatDuration(instance, 1.0f, 20.0f), detailX + 22, ey + 10, AdminTheme.INK_3, false);
            ey += 20;
        }
        g.drawString(font, AdminText.clip(font, Component.translatable(KEY + "preview_note").getString(), width), detailX, ey,
                AdminTheme.INK_3, false);
    }

    // ── state and input ──

    @Override
    public void onServerState() {
        if (host == null) {
            return;
        }
        if (ClientAdminBrewState.listStale()) {
            AdminClientRequests.brewList();
            if (selected != null) {
                AdminClientRequests.brewDetail(selected);
            }
        }
        if (!effectOptions().equals(effectsBuilt)) {
            host.requestRebuild();
            return;
        }
        refilter();
        revealSelected();
        BrewAdminService.Detail detail = ClientAdminBrewState.detail();
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
            g.drawString(font, AdminText.emptyList(!ClientAdminBrewState.brews().isEmpty(), ClientAdminBrewState.received(),
                    Component.translatable("admin.wizards_and_beasts.empty.brews")), x + 2, top + 2, AdminTheme.INK_3, false);
            return;
        }
        g.enableScissor(x, top, x + listW, bottom);
        int rowW = listW - SCROLLBAR_W - 2;
        for (int i = 0; i < visible.size(); i++) {
            int rowY = top + i * ROW_H - (int) listScroll;
            if (rowY + ROW_H < top || rowY > bottom) {
                continue;
            }
            BrewSummary row = visible.get(i);
            if (row.id().equals(selected)) {
                g.fill(x, rowY, x + rowW, rowY + ROW_H, AdminTheme.PAPER_SELECT);
                g.fill(x, rowY, x + 2, rowY + ROW_H, AdminTheme.GOLD);
            } else if (mouseX >= x && mouseX < x + rowW && mouseY >= rowY && mouseY < rowY + ROW_H) {
                g.fill(x, rowY, x + rowW, rowY + ROW_H, AdminTheme.ROW_HOVER);
            }
            // The brew's own colour, as the bottle shows it.
            g.fill(x + 4, rowY + 2, x + 13, rowY + 11, AdminTheme.INK_3);
            g.fill(x + 5, rowY + 3, x + 12, rowY + 10, row.color() | 0xFF000000);
            // ✕: disabled; •: an override is in force.
            String badges = (row.enabled() ? "" : "✕") + (row.overridden() ? "•" : "");
            int badgeW = font.width(badges);
            String name = AdminText.clip(font, Component.translatable(row.nameKey()).getString(), rowW - 18 - badgeW - 4);
            g.drawString(font, name, x + 17, rowY + 3, row.enabled() ? AdminTheme.INK : AdminTheme.INK_3, false);
            if (!badges.isEmpty()) {
                g.drawString(font, badges, x + rowW - badgeW - 2, rowY + 3, row.enabled() ? AdminTheme.GOLD_DARK : AdminTheme.BAD, false);
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
        int width = detailW - SCROLLBAR_W - 4;
        if (selected == null) {
            g.drawString(font, Component.translatable(KEY + "pick"), detailX, y + 4, AdminTheme.INK_3, false);
            return;
        }
        BrewAdminService.Detail detail = shown;
        if (detail == null) {
            g.drawString(font, Component.translatable("admin.wizards_and_beasts.status.loading"), detailX, y + 4, AdminTheme.INK_3, false);
            return;
        }
        int top = y - (int) detailScroll;
        BrewSummary summary = detail.summary();
        g.renderItem(stackOf(summary.id()), detailX, top);
        g.drawString(font, AdminText.clip(font, Component.translatable(summary.nameKey()).getString(), width - 20),
                detailX + 20, top + 2, AdminTheme.RUBRIC, false);
        g.drawString(font, AdminText.clip(font, String.join(" · ", badges(summary)), width - 20), detailX + 20, top + 13,
                summary.enabled() ? AdminTheme.INK_2 : AdminTheme.BAD, false);

        for (Header header : headers) {
            AdminSectionHeader.renderSub(g, font, detailX, top + header.docY(), width, header.title());
        }
        for (Text text : texts) {
            int ty = top + text.docY();
            if (ty + LINE >= y && ty <= y + h) {
                g.drawString(font, text.text(), detailX + text.docX(), ty, text.color(), false);
            }
        }
        for (ItemMark mark : items) {
            g.renderItem(mark.stack(), detailX + mark.docX(), top + mark.docY());
        }
        renderPreview(g, font, top, width);
        for (PlacedRow placedRow : rows) {
            placedRow.row().render(g, font, mouseX, mouseY, host != null && host.edits().isEdited(placedRow.row().setting().id()));
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

    static List<String> badges(BrewSummary summary) {
        List<String> out = new ArrayList<>();
        out.add(Component.translatable(KEY + "difficulty." + summary.difficulty().toLowerCase(Locale.ROOT)).getString());
        if (!summary.tier().isEmpty()) {
            out.add(Component.translatable(KEY + "filter." + summary.tier().toLowerCase(Locale.ROOT)).getString());
            out.add(String.format(Locale.ROOT, "%ds", summary.heatTicks() / 20));
            out.add(String.format(Locale.ROOT, "%.0f%%", summary.failureChance() * 100));
        }
        if (summary.silver()) {
            out.add(Component.translatable(KEY + "filter.silver").getString());
        }
        if (!summary.enabled()) {
            out.add(Component.translatable(KEY + "disabled").getString());
        }
        return out;
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
        if (host == null) {
            return;
        }
        for (PlacedRow placedRow : rows) {
            if (placedRow.row().labelHovered(mouseX, mouseY) && mouseY >= y && mouseY < y + h) {
                g.setTooltipForNextFrame(host.font(), AdminTooltip.forSetting(host.font(), placedRow.row().setting()), mouseX, mouseY);
                return;
            }
        }
        if (mouseX >= x && mouseX < x + listW - SCROLLBAR_W && mouseY >= listTop() && mouseY < y + h) {
            int index = (int) ((mouseY - listTop() + listScroll) / ROW_H);
            if (index >= 0 && index < visible.size()) {
                BrewSummary row = visible.get(index);
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable(row.nameKey()));
                lines.add(Component.literal(String.join(" · ", badges(row))));
                if (!row.effectText().isEmpty()) {
                    lines.add(Component.literal(row.effectText()));
                }
                g.setComponentTooltipForNextFrame(host.font(), lines, mouseX, mouseY);
            }
        }
    }

    @Override
    public @Nullable Component crumb() {
        for (at.koopro.wizardsandbeasts.network.admin.AdminBrewPayloads.BrewSummary brew : ClientAdminBrewState.brews()) {
            if (brew.id().equals(selected)) {
                return Component.translatable(brew.nameKey());
            }
        }
        return null;
    }
}
