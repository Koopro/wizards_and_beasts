package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.config.SettingKind;
import at.koopro.wizardsandbeasts.admin.creature.CreatureAdminService;
import at.koopro.wizardsandbeasts.admin.creature.CreatureRuleSettings;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminCreatureState;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.client.admin.viewer.EntityViewerScreen;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminCheckbox;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminEnumSelector;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminSectionHeader;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminText;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTextField;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTooltip;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminValueRow;
import at.koopro.wizardsandbeasts.network.admin.AdminCreaturePayloads.CreatureSummary;
import at.koopro.wizardsandbeasts.network.admin.AdminCreaturePayloads.SpawnEntry;
import at.koopro.wizardsandbeasts.network.admin.AdminCreaturePayloads.VariantInfo;
import at.koopro.wizardsandbeasts.network.admin.AdminSessionInfo;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Creatures → Creatures: the roster on the left (search, category and trait filters), the selected creature's page
 * on the right — its natural-spawn rule, the Entity Viewer and a server-placed test spawn, its base attributes, its
 * natural spawns as the server loaded them, behaviour, abilities, and its variants with their rules.
 *
 * <p>Everything shown is a server reply ({@code AdminCreaturePayloads}) or a setting descriptor; nothing names a
 * creature. The category filter lists the categories the roster actually has.
 */
@NullMarked
final class CreatureBrowserPanel implements AdminPanel {

    private static final int ROW_H = 13;
    private static final int LIST_HEADER_H = 36;
    private static final int SCROLLBAR_W = 4;
    private static final int SUB_H = 14;
    private static final int LINE = 10;
    private static final long ACTION_SHOWN_MS = 10_000L;
    private static final String KEY = "admin.wizards_and_beasts.creature.";
    private static final String ALL = "ALL";
    private static final List<String> FLAGS = List.of(ALL, "MAGICAL", "NON_MAGICAL", "HOSTILE", "PASSIVE",
            "FLYING", "TAMEABLE", "BREEDABLE", "SPAWNS", "VARIANTS");
    private static final String ROLL = "";

    static @Nullable String selected;
    /** Set when something outside the list picks a creature, so the list scrolls it into view once. */
    static boolean reveal;
    private static String search = "";
    private static String category = ALL;
    private static String flag = ALL;
    private static String spawnVariant = ROLL;
    private static boolean spawnNoAi;

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
    private List<CreatureSummary> visible = List.of();
    private List<String> categoriesBuilt = List.of();

    private CreatureAdminService.@Nullable Detail shown;
    private final List<PlacedRow> rows = new ArrayList<>();
    private final List<Placed> placed = new ArrayList<>();
    private final List<Text> texts = new ArrayList<>();
    private final List<Header> headers = new ArrayList<>();
    private int docHeight;
    private int actionsStatusY;

    private record Placed(AbstractWidget widget, int docX, int docY) {}

    private record PlacedRow(AdminValueRow row, int docY) {}

    /** A line of the page at {@code docY}. */
    private record Text(FormattedCharSequence text, int docX, int docY, int color) {}

    private record Header(Component title, int docY) {}

    @Override
    public AdminCategory section() {
        return AdminCategory.CREATURES;
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
        categoriesBuilt = categories();
        if (!categoriesBuilt.contains(category)) {
            category = ALL;
        }
        int half = (listW - 4) / 2;
        host.addPanelWidget(new AdminEnumSelector(x, y + 18, half, 14, categoriesBuilt, category, value -> {
            category = value;
            listScroll = 0;
            refilter();
        }, CreatureBrowserPanel::categoryLabel));
        host.addPanelWidget(new AdminEnumSelector(x + half + 4, y + 18, half, 14, FLAGS, flag, value -> {
            flag = value;
            listScroll = 0;
            refilter();
        }, CreatureBrowserPanel::flagLabel));

        if (ClientAdminCreatureState.listStale()) {
            AdminClientRequests.creatureList();
            if (selected != null) {
                AdminClientRequests.creatureDetail(selected);
            }
        }
        refilter();
        revealSelected();
        CreatureAdminService.Detail detail = ClientAdminCreatureState.detail();
        if (selected != null && (detail == null || !detail.summary().id().equals(selected))) {
            AdminClientRequests.creatureDetail(selected);
        }
        buildDetail(host, font);
    }

    // ── list ──

    private static List<String> categories() {
        Set<String> out = new LinkedHashSet<>();
        out.add(ALL);
        ClientAdminCreatureState.creatures().stream().map(CreatureSummary::category).filter(c -> !c.isEmpty())
                .sorted().forEach(out::add);
        return List.copyOf(out);
    }

    private static Component categoryLabel(String option) {
        return option.equals(ALL) ? Component.translatable(KEY + "filter.all_categories")
                : Component.literal(title(option));
    }

    private static Component flagLabel(String option) {
        return Component.translatable(KEY + "filter." + option.toLowerCase(Locale.ROOT));
    }

    private static String title(String name) {
        String lower = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private void refilter() {
        String needle = search.trim().toLowerCase(Locale.ROOT);
        List<CreatureSummary> out = new ArrayList<>();
        for (CreatureSummary row : ClientAdminCreatureState.creatures()) {
            if (!category.equals(ALL) && !row.category().equals(category)) {
                continue;
            }
            if (!matchesFlag(row)) {
                continue;
            }
            if (needle.isEmpty() || row.id().contains(needle)
                    || Component.translatable(row.nameKey()).getString().toLowerCase(Locale.ROOT).contains(needle)) {
                out.add(row);
            }
        }
        visible = out;
        listScroll = Mth.clamp(listScroll, 0, maxListScroll());
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

    private static boolean matchesFlag(CreatureSummary row) {
        return switch (flag) {
            case "MAGICAL" -> row.magical();
            case "NON_MAGICAL" -> !row.magical();
            case "HOSTILE" -> row.temperament().equals("HOSTILE");
            case "PASSIVE" -> row.temperament().equals("PASSIVE");
            case "FLYING" -> row.flying();
            case "TAMEABLE" -> row.tameable();
            case "BREEDABLE" -> row.breedable();
            case "SPAWNS" -> row.spawnEntries() > 0;
            case "VARIANTS" -> row.variants() > 0;
            default -> true;
        };
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
        spawnVariant = ROLL;
        detailScroll = 0;
        AdminClientRequests.creatureDetail(id);
        host.requestRebuild();
    }

    // ── detail ──

    private static boolean canSpawn() {
        AdminSessionInfo info = ClientAdminState.info();
        return info != null && info.capabilities().contains(AdminCapability.WORLD);
    }

    private void buildDetail(AdminPanelHost host, Font font) {
        rows.clear();
        placed.clear();
        texts.clear();
        headers.clear();
        CreatureAdminService.Detail detail = ClientAdminCreatureState.detail();
        if (selected == null || detail == null || !detail.summary().id().equals(selected)) {
            shown = null;
            docHeight = 0;
            return;
        }
        shown = detail;
        String id = detail.summary().id();
        int rowW = detailW - SCROLLBAR_W - 4;
        int doc = 26;

        // Rule: natural spawning.
        doc = header(font, doc, rowW, Component.translatable(KEY + "rules"));
        doc = row(host, font, CreatureRuleSettings.naturalSpawnId(id), doc, rowW);

        // Viewer and test spawn.
        doc = header(font, doc + 4, rowW, Component.translatable(KEY + "test"));
        AdminButton viewer = new AdminButton(0, 0, 90, 16, Component.translatable(KEY + "open_viewer"),
                AdminButton.Tone.PRIMARY, () -> openViewer(id, spawnVariant));
        viewer.setTooltip(Tooltip.create(Component.translatable(KEY + "open_viewer.tooltip")));
        place(host, viewer, 0, doc);
        List<String> variantOptions = new ArrayList<>();
        variantOptions.add(ROLL);
        detail.variants().forEach(v -> variantOptions.add(v.id()));
        if (!variantOptions.contains(spawnVariant)) {
            spawnVariant = ROLL;
        }
        if (variantOptions.size() > 1) {
            place(host, new AdminEnumSelector(0, 0, 100, 16, variantOptions, spawnVariant, value -> spawnVariant = value,
                    CreatureBrowserPanel::variantLabel), 94, doc);
        }
        doc += 20;
        AdminButton spawn = new AdminButton(0, 0, 90, 16, Component.translatable(KEY + "spawn"),
                AdminButton.Tone.NEUTRAL, () -> {
                    AdminClientRequests.spawnTestCreature(id, spawnVariant, spawnNoAi);
                    host.peek(40);
                });
        spawn.setTooltip(Tooltip.create(Component.translatable(KEY + "spawn.tooltip")));
        spawn.active = canSpawn();
        place(host, spawn, 0, doc);
        AdminCheckbox noAi = new AdminCheckbox(0, 0, Component.translatable(KEY + "no_ai"), spawnNoAi,
                checked -> spawnNoAi = checked);
        place(host, noAi, 94, doc + 2);
        AdminButton cleanup = new AdminButton(0, 0, 90, 16, Component.translatable(KEY + "cleanup"),
                AdminButton.Tone.QUIET, AdminClientRequests::cleanupTestCreatures);
        cleanup.setTooltip(Tooltip.create(Component.translatable(KEY + "cleanup.tooltip")));
        cleanup.active = canSpawn();
        place(host, cleanup, Math.max(0, rowW - 90), doc);
        doc += 20;
        actionsStatusY = doc;
        doc += LINE + 4;

        // Attributes.
        doc = header(font, doc, rowW, Component.translatable(KEY + "attributes"));
        doc = facts(font, doc, rowW, detail.attributes());

        // Spawning.
        doc = header(font, doc + 4, rowW, Component.translatable(KEY + "spawning"));
        if (detail.spawns().isEmpty()) {
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "no_natural_spawns"), AdminTheme.INK_3);
        }
        for (SpawnEntry entry : detail.spawns()) {
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "spawn_entry", entry.biomes(), entry.weight(),
                    entry.minCount(), entry.maxCount()), AdminTheme.INK);
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "spawn_source", entry.source()), AdminTheme.INK_3);
        }
        if (!detail.conditions().isEmpty()) {
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "conditions"), AdminTheme.INK_2);
            for (String condition : detail.conditions()) {
                doc = wrap(font, doc, rowW - 6, Component.literal("• ").append(
                        Component.translatable("admin.wizards_and_beasts.spawn_condition." + condition)), AdminTheme.INK);
            }
        }
        doc = wrap(font, doc, rowW, Component.translatable(KEY + "spawn_note"), AdminTheme.INK_3);

        // Behaviour and abilities.
        doc = header(font, doc + 4, rowW, Component.translatable(KEY + "behaviour"));
        doc = facts(font, doc, rowW, detail.behaviour());
        if (!detail.abilities().isEmpty()) {
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "abilities",
                    String.join(", ", detail.abilities())), AdminTheme.INK);
        }

        // Variants.
        doc = header(font, doc + 4, rowW, Component.translatable(KEY + "variants"));
        if (detail.variants().isEmpty()) {
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "no_variants"), AdminTheme.INK_3);
        }
        int total = detail.variants().stream().filter(VariantInfo::enabled).mapToInt(VariantInfo::weight).sum();
        for (VariantInfo variant : detail.variants()) {
            String chance = variant.enabled() && total > 0
                    ? String.format(Locale.ROOT, "%.1f%%", 100.0 * variant.weight() / total) : "—";
            texts.add(new Text(Component.literal(title(variant.id())).getVisualOrderText(), 0, doc + 3, AdminTheme.RUBRIC));
            AdminButton preview = new AdminButton(0, 0, 60, 14, Component.translatable(KEY + "preview"),
                    AdminButton.Tone.QUIET, () -> openViewer(id, variant.id()));
            place(host, preview, Math.max(0, rowW - 60), doc);
            doc += 16;
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "variant_line", chance, variant.authoredWeight(),
                    variant.texture().isEmpty() ? Component.translatable(KEY + "base_texture").getString() : variant.texture()),
                    AdminTheme.INK_3);
            doc += 2;
            doc = row(host, font, CreatureRuleSettings.variantId(id, variant.id(), CreatureRuleSettings.ENABLED), doc, rowW);
            doc = row(host, font, CreatureRuleSettings.variantId(id, variant.id(), CreatureRuleSettings.WEIGHT), doc, rowW);
            doc += 4;
        }
        docHeight = doc + 6;
        layoutDetail();
    }

    private static Component variantLabel(String option) {
        return option.equals(ROLL) ? Component.translatable(KEY + "variant_roll") : Component.literal(title(option));
    }

    private static void openViewer(String creatureId, String variant) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            minecraft.setScreen(new EntityViewerScreen(minecraft.screen, creatureId, variant));
        }
    }

    private int header(Font font, int doc, int rowW, Component title) {
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

    private void place(AdminPanelHost host, AbstractWidget widget, int docX, int docY) {
        host.addPanelWidget(widget);
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

    // ── state and input ──

    @Override
    public void onServerState() {
        if (host == null) {
            return;
        }
        if (ClientAdminCreatureState.listStale()) {
            AdminClientRequests.creatureList();
            if (selected != null) {
                AdminClientRequests.creatureDetail(selected);
            }
        }
        if (!categories().equals(categoriesBuilt)) {
            host.requestRebuild();
            return;
        }
        refilter();
        revealSelected();
        CreatureAdminService.Detail detail = ClientAdminCreatureState.detail();
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

    static void drawIcon(GuiGraphics g, int x, int y, int size, String icon, String id) {
        if (!icon.isEmpty()) {
            Identifier texture = Identifier.tryParse(icon);
            if (texture != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, size, size, size, size);
                return;
            }
        }
        // No bestiary icon: a plain mark in the ink colour, never a missing-texture checkerboard.
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, AdminTheme.PAPER_SHADE);
        g.fill(x + 1, y + 1, x + size - 1, y + 2, AdminTheme.INK_3);
    }

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
            g.drawString(font, AdminText.emptyList(!ClientAdminCreatureState.creatures().isEmpty(), ClientAdminCreatureState.received(),
                    Component.translatable("admin.wizards_and_beasts.empty.creatures")), x + 2, top + 2, AdminTheme.INK_3, false);
            return;
        }
        g.enableScissor(x, top, x + listW, bottom);
        int rowW = listW - SCROLLBAR_W - 2;
        for (int i = 0; i < visible.size(); i++) {
            int rowY = top + i * ROW_H - (int) listScroll;
            if (rowY + ROW_H < top || rowY > bottom) {
                continue;
            }
            CreatureSummary row = visible.get(i);
            boolean isSelected = row.id().equals(selected);
            if (isSelected) {
                g.fill(x, rowY, x + rowW, rowY + ROW_H, AdminTheme.PAPER_SELECT);
                g.fill(x, rowY, x + 2, rowY + ROW_H, AdminTheme.GOLD);
            } else if (mouseX >= x && mouseX < x + rowW && mouseY >= rowY && mouseY < rowY + ROW_H) {
                g.fill(x, rowY, x + rowW, rowY + ROW_H, AdminTheme.ROW_HOVER);
            }
            drawIcon(g, x + 3, rowY + 1, 11, row.icon(), row.id());
            // ✕: its natural spawning is switched off; ◇n: it has n variants.
            String badges = (row.naturalSpawnRule() ? "" : "✕") + (row.variants() > 0 ? "◇" + row.variants() : "");
            int badgeW = font.width(badges);
            String name = AdminText.clip(font, Component.translatable(row.nameKey()).getString(), rowW - 18 - badgeW - 4);
            g.drawString(font, name, x + 17, rowY + 3, row.naturalSpawnRule() ? AdminTheme.INK : AdminTheme.INK_3, false);
            if (!badges.isEmpty()) {
                g.drawString(font, badges, x + rowW - badgeW - 2, rowY + 3,
                        row.naturalSpawnRule() ? AdminTheme.GOLD_DARK : AdminTheme.BAD, false);
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
        CreatureAdminService.Detail detail = shown;
        if (detail == null) {
            g.drawString(font, Component.translatable("admin.wizards_and_beasts.status.loading"), detailX, y + 4, AdminTheme.INK_3, false);
            return;
        }
        int top = y - (int) detailScroll;
        CreatureSummary summary = detail.summary();
        drawIcon(g, detailX, top + 1, 16, summary.icon(), summary.id());
        g.drawString(font, AdminText.clip(font, Component.translatable(summary.nameKey()).getString(), width - 20),
                detailX + 20, top + 2, AdminTheme.RUBRIC, false);
        g.drawString(font, AdminText.clip(font, String.join(" · ", badges(summary)), width - 20), detailX + 20, top + 13,
                AdminTheme.INK_2, false);

        for (Header header : headers) {
            AdminSectionHeader.renderSub(g, font, detailX, top + header.docY(), width, header.title());
        }
        for (Text text : texts) {
            int ty = top + text.docY();
            if (ty + LINE >= y && ty <= y + h) {
                g.drawString(font, text.text(), detailX + text.docX(), ty, text.color(), false);
            }
        }
        for (PlacedRow placedRow : rows) {
            placedRow.row().render(g, font, mouseX, mouseY, host != null && host.edits().isEdited(placedRow.row().setting().id()));
        }

        var action = ClientAdminCreatureState.lastAction();
        int statusY = top + actionsStatusY;
        if (action != null && Util.getMillis() - ClientAdminCreatureState.lastActionAt() < ACTION_SHOWN_MS) {
            g.drawString(font, AdminText.clip(font, Component.translatable(action.messageKey(), action.detail()).getString(), width),
                    detailX, statusY, action.success() ? AdminTheme.GOOD : AdminTheme.BAD, false);
        } else {
            g.drawString(font, AdminText.clip(font, Component.translatable(canSpawn() ? KEY + "spawn_hint" : KEY + "spawn_needs_world").getString(), width),
                    detailX, statusY, AdminTheme.INK_3, false);
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

    static List<String> badges(CreatureSummary summary) {
        List<String> out = new ArrayList<>();
        if (!summary.category().isEmpty()) {
            out.add(title(summary.category()));
        }
        out.add(Component.translatable(KEY + (summary.magical() ? "badge.magical" : "badge.non_magical")).getString());
        if (!summary.temperament().isEmpty()) {
            out.add(title(summary.temperament()));
        }
        if (summary.flying()) {
            out.add(Component.translatable(KEY + "badge.flying").getString());
        }
        if (summary.tameable()) {
            out.add(Component.translatable(KEY + (summary.breedable() ? "badge.breedable" : "badge.tameable")).getString());
        }
        out.add(Component.translatable(summary.spawnEntries() == 0 ? KEY + "badge.no_spawns"
                : summary.naturalSpawnRule() ? KEY + "badge.spawns" : KEY + "badge.spawns_off").getString());
        if (summary.bespoke()) {
            out.add(Component.translatable(KEY + "badge.bespoke").getString());
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
                CreatureSummary row = visible.get(index);
                g.setComponentTooltipForNextFrame(host.font(), List.of(Component.translatable(row.nameKey()),
                        Component.literal(String.join(" · ", badges(row)))), mouseX, mouseY);
            }
        }
    }

    @Override
    public @Nullable Component crumb() {
        for (at.koopro.wizardsandbeasts.network.admin.AdminCreaturePayloads.CreatureSummary creature
                : ClientAdminCreatureState.creatures()) {
            if (creature.id().equals(selected)) {
                return Component.translatable(creature.nameKey());
            }
        }
        return null;
    }
}
