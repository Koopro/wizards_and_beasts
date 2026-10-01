package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.wand.WandAdminService;
import at.koopro.wizardsandbeasts.admin.wand.WandSettingProvider;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminWandState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminText;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import at.koopro.wizardsandbeasts.network.admin.AdminWandPayloads.PartInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Wands → Woods and Wands → Cores: every wood (or core) in the server's datapack registry on the left; on the right
 * whether it is withdrawn, which partners the wandmaking recipes allow it and whether each pairing is withdrawn, its
 * canonical lore as its datapack file writes it, its numbers (read-only: they are datapack data), and — for a wood —
 * the production wand model and the tooltip a wand of it shows.
 *
 * <p>Compatibility is data: a pairing exists because a {@code wandmaking} recipe names it, and an administrator can
 * only withdraw what the data allows, never invent a pairing.
 */
@NullMarked
final class WandPartsPanel extends AdminBrowserPanel<PartInfo> {

    private static final String KEY = "admin.wizards_and_beasts.wand.";
    private static final int PREVIEW_H = 72;

    static @Nullable String selectedWood;
    static @Nullable String selectedCore;

    private final boolean woods;
    private int previewTop = -1;
    private List<Component> tooltip = List.of();

    WandPartsPanel(boolean woods) {
        this.woods = woods;
    }

    @Override
    public AdminCategory section() {
        return AdminCategory.WANDS;
    }

    @Override
    protected List<PartInfo> entries() {
        WandAdminService.Catalog catalog = ClientAdminWandState.catalog();
        return catalog == null ? List.of() : woods ? catalog.woods() : catalog.cores();
    }

    @Override
    protected String idOf(PartInfo entry) {
        return entry.id();
    }

    @Override
    protected Component nameOf(PartInfo entry) {
        return name(entry);
    }

    static Component name(PartInfo entry) {
        return entry.nameTranslatable() ? Component.translatable(entry.name()) : Component.literal(entry.name());
    }

    private @Nullable Identifier settingOf(PartInfo entry) {
        Identifier id = Identifier.tryParse(entry.id());
        return id == null ? null : woods ? WandSettingProvider.woodId(id) : WandSettingProvider.coreId(id);
    }

    @Override
    protected boolean enabledOf(PartInfo entry) {
        Identifier setting = settingOf(entry);
        AdminSettingDescriptor descriptor = setting == null ? null : ClientAdminState.get(setting);
        return descriptor == null || "true".equals(descriptor.value());
    }

    @Override
    protected int swatchOf(PartInfo entry) {
        return woods ? entry.tint() : 0;
    }

    @Override
    protected boolean hasSwatches() {
        return woods;
    }

    @Override
    protected String badgesOf(PartInfo entry) {
        return (enabledOf(entry) ? "" : "✕") + (withdrawnPairs(entry) > 0 ? "•" : "");
    }

    @Override
    protected @Nullable String selected() {
        return woods ? selectedWood : selectedCore;
    }

    @Override
    protected void setSelected(String id) {
        if (woods) {
            selectedWood = id;
        } else {
            selectedCore = id;
        }
    }

    @Override
    protected boolean stale() {
        return ClientAdminWandState.stale();
    }

    @Override
    protected void request() {
        AdminClientRequests.wandCatalog();
    }

    @Override
    protected Component pickPrompt() {
        return Component.translatable(KEY + (woods ? "pick_wood" : "pick_core"));
    }

    /** The partners a recipe pairs this part with, as ids. */
    private List<String> partners(PartInfo entry) {
        WandAdminService.Catalog catalog = ClientAdminWandState.catalog();
        List<String> out = new ArrayList<>();
        if (catalog == null) {
            return out;
        }
        for (String pair : catalog.pairs()) {
            String[] halves = pair.split("\\|", 2);
            if (woods ? halves[0].equals(entry.id()) : halves[1].equals(entry.id())) {
                out.add(woods ? halves[1] : halves[0]);
            }
        }
        return out;
    }

    private @Nullable Identifier pairSetting(PartInfo entry, String partner) {
        Identifier self = Identifier.tryParse(entry.id());
        Identifier other = Identifier.tryParse(partner);
        if (self == null || other == null) {
            return null;
        }
        return woods ? WandSettingProvider.pairId(self, other) : WandSettingProvider.pairId(other, self);
    }

    private long withdrawnPairs(PartInfo entry) {
        return partners(entry).stream().map(p -> pairSetting(entry, p)).filter(id -> id != null)
                .map(ClientAdminState::get).filter(d -> d != null && !"true".equals(d.value())).count();
    }

    @Override
    protected String subtitleOf(PartInfo entry) {
        String state = Component.translatable(KEY + (enabledOf(entry) ? "state_enabled" : "state_withdrawn")).getString();
        return state + " · " + Component.translatable(KEY + "pair_count", partners(entry).size()).getString();
    }

    private Component partnerName(String id) {
        PartInfo info = other(id);
        return info == null ? Component.literal(id) : name(info);
    }

    private @Nullable PartInfo other(String id) {
        WandAdminService.Catalog catalog = ClientAdminWandState.catalog();
        if (catalog == null) {
            return null;
        }
        for (PartInfo part : woods ? catalog.cores() : catalog.woods()) {
            if (part.id().equals(id)) {
                return part;
            }
        }
        return null;
    }

    @Override
    protected int buildPage(AdminPanelHost host, Font font, PartInfo entry, int doc, int rowW) {
        previewTop = -1;
        tooltip = List.of();
        doc = header(doc, Component.translatable(KEY + (woods ? "wood" : "core")));
        Identifier setting = settingOf(entry);
        if (setting != null) {
            doc = row(host, font, setting, doc, rowW);
        }

        List<String> partners = partners(entry);
        if (woods && !partners.isEmpty()) {
            doc = header(doc + 4, Component.translatable(KEY + "model"));
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "model_sample", partnerName(partners.getFirst())), AdminTheme.INK_3);
            previewTop = doc + 2;
            ItemStack sample = AdminItemPreview.wand(entry.id(), partners.getFirst(), 12.0f, "", "");
            tooltip = AdminItemPreview.tooltip(sample);
            doc = previewTop + Math.max(PREVIEW_H, tooltip.size() * LINE + 4);
            place(new AdminButton(0, 0, 110, AdminTheme.CONTROL_H, Component.translatable(KEY + "open_generator"),
                    AdminButton.Tone.QUIET, () -> {
                        WandGeneratorPanel.wood = entry.id();
                        WandGeneratorPanel.core = partners.getFirst();
                        WandGeneratorPanel.dirty = true;
                        WandsPanel.tab = WandsPanel.Tab.GENERATOR;
                        host.requestRebuild();
                    }), 0, doc);
            doc += AdminTheme.CONTROL_H + 4;
        }

        doc = header(doc + 4, Component.translatable(KEY + (woods ? "allowed_cores" : "allowed_woods")));
        doc = wrap(font, doc, rowW, Component.translatable(KEY + "pairs_hint"), AdminTheme.INK_3);
        for (String partner : partners) {
            PartInfo info = other(partner);
            doc = wrap(font, doc, rowW, info == null ? Component.literal(partner) : name(info), AdminTheme.RUBRIC);
            Identifier pair = pairSetting(entry, partner);
            if (pair != null) {
                doc = row(host, font, pair, doc, rowW);
            }
        }
        List<String> unpaired = new ArrayList<>();
        WandAdminService.Catalog catalog = ClientAdminWandState.catalog();
        if (catalog != null) {
            for (PartInfo part : woods ? catalog.cores() : catalog.woods()) {
                if (!partners.contains(part.id())) {
                    unpaired.add(name(part).getString());
                }
            }
        }
        if (!unpaired.isEmpty()) {
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "no_recipe_with", String.join(", ", unpaired)), AdminTheme.INK_3);
        }

        doc = header(doc + 4, Component.translatable(KEY + "lore"));
        doc = wrap(font, doc, rowW, entry.lore().isEmpty() ? Component.translatable(KEY + "no_lore")
                : Component.literal(entry.lore()).withStyle(ChatFormatting.ITALIC), AdminTheme.INK_2);
        doc = wrap(font, doc, rowW, Component.translatable(KEY + "lore_note"), AdminTheme.INK_3);

        doc = header(doc + 4, Component.translatable(KEY + "properties"));
        doc = facts(font, doc, rowW, entry.facts());
        doc = wrap(font, doc, rowW, Component.translatable(KEY + "properties_note"), AdminTheme.INK_3);
        return doc;
    }

    @Override
    protected void renderPage(GuiGraphics g, Font font, PartInfo entry, int top, int width, int mouseX, int mouseY) {
        if (previewTop < 0) {
            return;
        }
        List<String> partners = partners(entry);
        if (partners.isEmpty()) {
            return;
        }
        int py = top + previewTop;
        AdminItemPreview.draw(g, AdminItemPreview.wand(entry.id(), partners.getFirst(), 12.0f, "", ""), detailX, py, 4.0f);
        int ty = py;
        int textX = detailX + 70;
        for (Component line : tooltip) {
            g.drawString(font, AdminText.clip(font, line.getString(), width - 70), textX, ty, AdminTheme.INK, false);
            ty += LINE;
        }
    }

    @Override
    protected boolean loaded() {
        return ClientAdminWandState.catalog() != null;
    }

    @Override
    protected Component emptyMessage() {
        return Component.translatable(woods ? "admin.wizards_and_beasts.empty.woods" : "admin.wizards_and_beasts.empty.cores");
    }
}
