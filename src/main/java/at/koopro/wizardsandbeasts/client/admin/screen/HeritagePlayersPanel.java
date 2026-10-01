package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.heritage.HeritageAdminService;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminHeritageState;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminConfirmDialog;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminEnumSelector;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminSectionHeader;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminText;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.rules.HeritageRules;
import at.koopro.wizardsandbeasts.network.admin.AdminHeritagePayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminSessionInfo;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Heritages → Players: the online players on the left; the selected one's heritage, stats and derived values on
 * the right, with the two player tools — assign a heritage, send back to onboarding.
 *
 * <p>Everything shown is what the server read off the player when asked ({@code AdminHeritagePayloads}); the
 * panel holds no player data of its own and edits nothing locally. Both tools ask for confirmation in a dialog
 * and send a confirmed request, which the server checks for the players capability and refuses unconfirmed. There
 * is no "every player" tool, on purpose.
 */
@NullMarked
final class HeritagePlayersPanel implements AdminPanel {

    private static final int ROW_H = 13;
    private static final int LIST_HEADER_H = 20;
    private static final int SCROLLBAR_W = 4;
    private static final int SUB_H = 14;
    private static final int LINE = 10;
    private static final long ACTION_SHOWN_MS = 10_000L;
    private static final String KEY = "admin.wizards_and_beasts.heritage_players.";

    private static @Nullable UUID selected;
    private static String assignHeritage = Heritage.values()[0].getId();
    private static String assignVariant = "";

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
    private int docHeight;
    private int actionsTop;
    private final List<Placed> placed = new ArrayList<>();
    private HeritageAdminService.@Nullable Inspection shown;

    private record Placed(AbstractWidget widget, int docX, int docY) {}

    @Override
    public AdminCategory section() {
        return AdminCategory.HERITAGES;
    }

    /** Pre-selects a player for the next visit; the inspection is requested when the panel opens. */
    static void select(UUID player) {
        selected = player;
    }

    static boolean permitted() {
        AdminSessionInfo info = ClientAdminState.info();
        return info != null && info.capabilities().contains(AdminCapability.PLAYERS);
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
        placed.clear();
        if (!permitted()) {
            return;
        }
        host.addPanelWidget(new AdminButton(x, y, listW - SCROLLBAR_W, 16,
                Component.translatable(KEY + "refresh"), AdminButton.Tone.QUIET, () -> {
                    AdminClientRequests.heritagePlayers();
                    if (selected != null) {
                        AdminClientRequests.inspectPlayer(selected);
                    }
                }));
        AdminClientRequests.heritagePlayers();
        if (selected != null && !ClientAdminHeritageState.inspecting(selected)) {
            AdminClientRequests.inspectPlayer(selected);
        }
        buildDetail(host, host.font());
    }

    private void buildDetail(AdminPanelHost host, Font font) {
        placed.clear();
        HeritageAdminService.Inspection inspection = ClientAdminHeritageState.inspection();
        shown = inspection != null && inspection.id().equals(selected) ? inspection : null;
        if (shown == null) {
            docHeight = 0;
            return;
        }
        HeritageAdminService.Inspection target = shown;
        int rowW = detailW - SCROLLBAR_W - 4;
        int doc = 16;
        doc += SUB_H + target.identity().size() * LINE + 4;
        doc += SUB_H + target.stats().size() * LINE + 4;
        doc += SUB_H + LINE + target.derived().size() * LINE + 6;

        actionsTop = doc;
        List<String> heritages = Arrays.stream(Heritage.values()).map(Heritage::getId).toList();
        if (!heritages.contains(assignHeritage)) {
            assignHeritage = heritages.get(0);
        }
        Heritage chosen = Heritage.byId(assignHeritage);
        List<String> variants = chosen == null ? List.of() : chosen.getSubtypes().stream().map(HeritageVariant::getId).toList();
        if (!variants.contains(assignVariant)) {
            assignVariant = variants.isEmpty() ? "" : variants.get(0);
        }
        int half = Math.max(60, (rowW - 4) / 2);
        place(host, new AdminEnumSelector(0, 0, half, 16, heritages, assignHeritage, value -> {
            assignHeritage = value;
            host.requestRebuild();
        }, HeritagePlayersPanel::heritageLabel), 0, actionsTop + SUB_H);
        if (!variants.isEmpty()) {
            place(host, new AdminEnumSelector(0, 0, half, 16, variants, assignVariant, value -> assignVariant = value,
                    HeritagePlayersPanel::variantLabel), half + 4, actionsTop + SUB_H);
        }
        AdminButton assign = new AdminButton(0, 0, 96, 16, Component.translatable(KEY + "assign"),
                AdminButton.Tone.PRIMARY, () -> confirmAssign(host, target));
        assign.setTooltip(Tooltip.create(Component.translatable(KEY + "assign.tooltip")));
        assign.active = !variants.isEmpty();
        AdminButton reset = new AdminButton(0, 0, 120, 16, Component.translatable(KEY + "reset"),
                AdminButton.Tone.NEUTRAL, () -> confirmReset(host, target));
        reset.setTooltip(Tooltip.create(Component.translatable(KEY + "reset.tooltip")));
        reset.active = !target.heritageId().isEmpty();
        place(host, assign, 0, actionsTop + SUB_H + 20);
        place(host, reset, 100, actionsTop + SUB_H + 20);
        docHeight = actionsTop + SUB_H + 40 + LINE + 4;
        layoutDetail();
    }

    private static Component heritageLabel(String id) {
        Heritage heritage = Heritage.byId(id);
        if (heritage == null) {
            return Component.literal(id);
        }
        Component name = Component.literal(heritage.getDisplayName());
        return HeritageRules.selectable(heritage) ? name
                : Component.translatable(KEY + "closed_suffix", name);
    }

    private static Component variantLabel(String id) {
        HeritageVariant variant = HeritageVariant.byId(id);
        return variant == null ? Component.literal(id) : Component.literal(variant.getDisplayName());
    }

    private void confirmAssign(AdminPanelHost host, HeritageAdminService.Inspection target) {
        Heritage heritage = Heritage.byId(assignHeritage);
        HeritageVariant variant = HeritageVariant.byId(assignVariant);
        if (heritage == null || variant == null) {
            return;
        }
        List<Component> body = new ArrayList<>();
        body.add(Component.translatable(KEY + "assign.body", target.name(),
                Component.literal(heritage.getDisplayName()), Component.literal(variant.getDisplayName())));
        body.add(Component.translatable(KEY + "assign.effects"));
        if (!HeritageRules.selectable(heritage)) {
            body.add(Component.translatable(KEY + "assign.closed"));
        }
        UUID id = target.id();
        host.openDialog(new AdminConfirmDialog(Component.translatable(KEY + "assign.title"), body,
                Component.translatable(KEY + "assign"), true, () -> {
                    host.closeDialog();
                    AdminClientRequests.assignHeritage(id, heritage.getId(), variant.getId());
                }, host::closeDialog));
    }

    private void confirmReset(AdminPanelHost host, HeritageAdminService.Inspection target) {
        UUID id = target.id();
        host.openDialog(new AdminConfirmDialog(Component.translatable(KEY + "reset.title"),
                List.of(Component.translatable(KEY + "reset.body", target.name()),
                        Component.translatable(KEY + "reset.effects")),
                Component.translatable(KEY + "reset"), true, () -> {
                    host.closeDialog();
                    AdminClientRequests.resetOnboarding(id);
                }, host::closeDialog));
    }

    private void place(AdminPanelHost host, AbstractWidget widget, int docX, int docY) {
        host.addPanelWidget(widget);
        placed.add(new Placed(widget, docX, docY));
    }

    private double maxDetailScroll() {
        return Math.max(0, docHeight - h);
    }

    private int listTop() {
        return y + LIST_HEADER_H;
    }

    private double maxListScroll() {
        return Math.max(0, ClientAdminHeritageState.players().size() * ROW_H - (y + h - listTop()));
    }

    private void layoutDetail() {
        detailScroll = Mth.clamp(detailScroll, 0, maxDetailScroll());
        for (Placed p : placed) {
            int widgetY = y + p.docY() - (int) detailScroll;
            p.widget().setPosition(detailX + p.docX(), widgetY);
            p.widget().visible = widgetY >= y && widgetY + p.widget().getHeight() <= y + h;
        }
    }

    // ── state and input ──

    @Override
    public void onServerState() {
        if (host != null && ClientAdminHeritageState.inspection() != shown) {
            host.requestRebuild();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!permitted() || mouseX < x || mouseX >= x + listW - SCROLLBAR_W || mouseY < listTop() || mouseY >= y + h) {
            return false;
        }
        int index = (int) ((mouseY - listTop() + listScroll) / ROW_H);
        List<HeritageAdminService.PlayerRow> players = ClientAdminHeritageState.players();
        if (index >= 0 && index < players.size() && host != null) {
            UUID id = players.get(index).id();
            if (!id.equals(selected)) {
                selected = id;
                detailScroll = 0;
                AdminClientRequests.inspectPlayer(id);
                host.requestRebuild();
            }
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
        if (!permitted()) {
            int lineY = y + 4;
            for (var line : font.split(Component.translatable(KEY + "no_capability"), w)) {
                g.drawString(font, line, x, lineY, AdminTheme.INK_3, false);
                lineY += LINE;
            }
            return;
        }
        renderList(g, font, mouseX, mouseY);
        g.fill(detailX - AdminTheme.PAD / 2 - 1, y, detailX - AdminTheme.PAD / 2, y + h, AdminTheme.PAPER_RULE);
        g.enableScissor(detailX, y, detailX + detailW, y + h);
        renderDetail(g, font);
        g.disableScissor();
    }

    private void renderList(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int top = listTop();
        int bottom = y + h;
        List<HeritageAdminService.PlayerRow> players = ClientAdminHeritageState.players();
        if (players.isEmpty()) {
            g.drawString(font, AdminText.emptyList(false, ClientAdminHeritageState.received(),
                    Component.translatable("admin.wizards_and_beasts.empty.players")), x + 2, top + 2, AdminTheme.INK_3, false);
            return;
        }
        int rowW = listW - SCROLLBAR_W - 2;
        g.enableScissor(x, top, x + listW, bottom);
        for (int i = 0; i < players.size(); i++) {
            int rowY = top + i * ROW_H - (int) listScroll;
            if (rowY + ROW_H < top || rowY > bottom) {
                continue;
            }
            HeritageAdminService.PlayerRow row = players.get(i);
            boolean isSelected = row.id().equals(selected);
            if (isSelected) {
                g.fill(x, rowY, x + rowW, rowY + ROW_H, AdminTheme.PAPER_SELECT);
                g.fill(x, rowY, x + 2, rowY + ROW_H, AdminTheme.GOLD);
            } else if (mouseX >= x && mouseX < x + rowW && mouseY >= rowY && mouseY < rowY + ROW_H) {
                g.fill(x, rowY, x + rowW, rowY + ROW_H, AdminTheme.ROW_HOVER);
            }
            Heritage heritage = Heritage.byId(row.heritageId());
            if (heritage != null) {
                HeritageBrowserPanel.drawCrest(g, font, x + 3, rowY + 1, heritage);
            }
            String name = AdminText.clip(font, row.name(), rowW - 20);
            g.drawString(font, name, x + 17, rowY + 3, heritage == null ? AdminTheme.INK_3 : AdminTheme.INK, false);
        }
        g.disableScissor();
    }

    private void renderDetail(GuiGraphics g, Font font) {
        int width = detailW - SCROLLBAR_W - 4;
        if (selected == null) {
            g.drawString(font, Component.translatable(KEY + "pick"), detailX, y + 4, AdminTheme.INK_3, false);
            return;
        }
        HeritageAdminService.Inspection target = shown;
        if (target == null) {
            g.drawString(font, Component.translatable("admin.wizards_and_beasts.status.loading"), detailX, y + 4, AdminTheme.INK_3, false);
            return;
        }
        int top = y - (int) detailScroll;
        g.drawString(font, AdminText.clip(font, target.name(), width), detailX, top + 3, AdminTheme.RUBRIC, false);
        int cursor = top + 16;
        cursor = facts(g, font, cursor, width, Component.translatable(KEY + "identity"), target.identity());
        cursor = facts(g, font, cursor, width, Component.translatable(KEY + "stats"), target.stats());
        AdminSectionHeader.renderSub(g, font, detailX, cursor, width, Component.translatable(KEY + "derived"));
        cursor += SUB_H;
        g.drawString(font, AdminText.clip(font, Component.translatable(KEY + "derived.note").getString(), width),
                detailX, cursor, AdminTheme.INK_3, false);
        cursor += LINE;
        for (AdminSpellFact fact : target.derived()) {
            line(g, font, cursor, width, fact);
            cursor += LINE;
        }

        AdminSectionHeader.renderSub(g, font, detailX, top + actionsTop, width, Component.translatable(KEY + "actions"));
        AdminHeritagePayloads.ActionReply action = ClientAdminHeritageState.lastAction();
        int statusY = top + actionsTop + SUB_H + 40;
        if (action != null && Util.getMillis() - ClientAdminHeritageState.lastActionAt() < ACTION_SHOWN_MS) {
            g.drawString(font, AdminText.clip(font, Component.translatable(action.messageKey(), action.detail()).getString(), width),
                    detailX, statusY, action.success() ? AdminTheme.GOOD : AdminTheme.BAD, false);
        } else {
            g.drawString(font, AdminText.clip(font, Component.translatable(KEY + "hint").getString(), width),
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

    private int facts(GuiGraphics g, Font font, int y, int width, Component title, List<AdminSpellFact> facts) {
        AdminSectionHeader.renderSub(g, font, detailX, y, width, title);
        int cursor = y + SUB_H;
        for (AdminSpellFact fact : facts) {
            line(g, font, cursor, width, fact);
            cursor += LINE;
        }
        return cursor + 4;
    }

    private void line(GuiGraphics g, Font font, int y, int width, AdminSpellFact fact) {
        int labelW = Math.min(110, width / 2);
        g.drawString(font, AdminText.clip(font, Component.translatable(fact.labelKey()).getString(), labelW - 4),
                detailX, y, AdminTheme.INK_2, false);
        Component value = fact.valueTranslatable() ? Component.translatable(fact.value()) : Component.literal(fact.value());
        g.drawString(font, AdminText.clip(font, value.getString(), width - labelW), detailX + labelW, y, AdminTheme.INK, false);
    }
}
