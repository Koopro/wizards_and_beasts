package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.wand.WandAdminService;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminWandState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminEnumSelector;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminSlider;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminText;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import at.koopro.wizardsandbeasts.network.admin.AdminWandPayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminWandPayloads.PartInfo;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Wands → Generator: pick a wood, core, length, flexibility and appearance, and see the wand. The model is the
 * production item renderer drawing a stack made by the bench's own assembly ({@link AdminItemPreview#wand}); the
 * verdict and the numbers are the server's — it checks the pairing against its recipes and rules and resolves the
 * stats with the same resolver a cast uses. "Give me this wand" asks the server to make it; it re-checks everything.
 */
@NullMarked
final class WandGeneratorPanel implements AdminPanel {

    private static final String KEY = "admin.wizards_and_beasts.wand.";
    private static final int LABEL_H = 10;
    private static final int FIELD_H = 16;
    private static final int LINE = 10;
    private static final int DEBOUNCE_TICKS = 4;

    static String wood = "";
    static String core = "";
    static float length = 12.0f;
    static String flexibility = WandFlexibility.SUPPLE.name();
    static String preset = "";
    static boolean dirty = true;

    private @Nullable AdminPanelHost host;
    private int x;
    private int y;
    private int w;
    private int h;
    private int formW;
    private int quiet;
    private boolean built;
    private final List<Label> labels = new ArrayList<>();

    private record Label(Component text, int x, int y) {}

    @Override
    public AdminCategory section() {
        return AdminCategory.WANDS;
    }

    static WandAdminService.Request request() {
        return new WandAdminService.Request(wood, core, length, flexibility, preset);
    }

    @Override
    public void init(AdminPanelHost host, int x, int y, int w, int h) {
        this.host = host;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.formW = Mth.clamp(w * 38 / 100, 120, 180);
        labels.clear();
        WandAdminService.Catalog catalog = ClientAdminWandState.catalog();
        if (ClientAdminWandState.stale()) {
            AdminClientRequests.wandCatalog();
        }
        built = catalog != null;
        if (catalog == null) {
            return;
        }
        List<String> woods = catalog.woods().stream().map(PartInfo::id).toList();
        List<String> cores = catalog.cores().stream().map(PartInfo::id).toList();
        if (!woods.contains(wood) && !woods.isEmpty()) {
            wood = woods.getFirst();
            dirty = true;
        }
        if (!cores.contains(core) && !cores.isEmpty()) {
            core = cores.getFirst();
            dirty = true;
        }
        int fy = y;
        fy = selector(host, fy, "field_wood", woods, wood, v -> wood = v, v -> name(catalog.woods(), v));
        fy = selector(host, fy, "field_core", cores, core, v -> core = v, v -> name(catalog.cores(), v));
        labels.add(new Label(Component.translatable(KEY + "field_length"), x, fy));
        host.addPanelWidget(new AdminSlider(x, fy + LABEL_H, formW, FIELD_H, WandAdminService.MIN_LENGTH,
                WandAdminService.MAX_LENGTH, 0.25, false, Float.toString(length), v -> {
                    try {
                        length = Float.parseFloat(v);
                        changed();
                    } catch (NumberFormatException ignored) {
                        // The slider only offers numbers.
                    }
                }));
        fy += LABEL_H + FIELD_H + 6;
        List<String> flexes = Arrays.stream(WandFlexibility.values()).map(Enum::name).toList();
        fy = selector(host, fy, "field_flexibility", flexes, flexibility, v -> flexibility = v, AdminItemPreview::flexibilityName);
        List<String> looks = new ArrayList<>();
        looks.add("");
        List<String> lookNames = new ArrayList<>();
        lookNames.add(Component.translatable(KEY + "look_default").getString());
        for (String entry : catalog.presets()) {
            String[] parts = entry.split("\\|", 2);
            looks.add(parts[0]);
            lookNames.add(parts.length > 1 ? parts[1] : parts[0]);
        }
        if (!looks.contains(preset)) {
            preset = "";
        }
        fy = selector(host, fy, "field_look", looks, preset, v -> preset = v,
                v -> Component.literal(lookNames.get(Math.max(0, looks.indexOf(v)))));
        host.addPanelWidget(new AdminButton(x, fy + 4, formW, FIELD_H, Component.translatable(KEY + "give"),
                AdminButton.Tone.PRIMARY, () -> AdminClientRequests.giveTestWand(request())));
    }

    private interface Setter {
        void set(String value);
    }

    private int selector(AdminPanelHost host, int fy, String label, List<String> options, String current, Setter setter,
                         java.util.function.Function<String, Component> labeller) {
        labels.add(new Label(Component.translatable(KEY + label), x, fy));
        host.addPanelWidget(new AdminEnumSelector(x, fy + LABEL_H, formW, FIELD_H, options, current, value -> {
            setter.set(value);
            changed();
        }, labeller));
        return fy + LABEL_H + FIELD_H + 6;
    }

    private static Component name(List<PartInfo> parts, String id) {
        for (PartInfo part : parts) {
            if (part.id().equals(id)) {
                return WandPartsPanel.name(part);
            }
        }
        return Component.literal(id);
    }

    private void changed() {
        dirty = true;
        quiet = 0;
    }

    @Override
    public void tick() {
        // Ask once the selection has settled, not for every notch of a slider drag.
        if (dirty && ++quiet >= DEBOUNCE_TICKS && !wood.isEmpty() && !core.isEmpty()) {
            dirty = false;
            AdminClientRequests.wandPreview(request());
        }
    }

    @Override
    public void onServerState() {
        if (host == null) {
            return;
        }
        if (ClientAdminWandState.stale()) {
            AdminClientRequests.wandCatalog();
            dirty = true;
        }
        if (!built && ClientAdminWandState.catalog() != null) {
            host.requestRebuild();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (host == null) {
            return;
        }
        Font font = host.font();
        if (!built) {
            g.drawString(font, Component.translatable("admin.wizards_and_beasts.status.loading"), x + 2, y + 2, AdminTheme.INK_3, false);
            return;
        }
        for (Label label : labels) {
            g.drawString(font, label.text(), label.x(), label.y(), AdminTheme.RUBRIC, false);
        }
        int px = x + formW + AdminTheme.PAD * 2;
        int pw = w - formW - AdminTheme.PAD * 2;
        g.fill(px - AdminTheme.PAD - 1, y, px - AdminTheme.PAD, y + h, AdminTheme.PAPER_RULE);

        WandAdminService.Request request = request();
        ItemStack stack = AdminItemPreview.wand(wood, core, length, flexibility, preset);
        AdminItemPreview.draw(g, stack, px, y, 5.0f);
        int ty = y;
        int textX = px + 86;
        for (Component line : AdminItemPreview.tooltip(stack)) {
            g.drawString(font, AdminText.clip(font, line.getString(), pw - 86), textX, ty, AdminTheme.INK, false);
            ty += LINE;
        }
        int fy = Math.max(y + 84, ty + 6);
        AdminWandPayloads.Preview preview = ClientAdminWandState.previewFor(request);
        if (preview == null) {
            g.drawString(font, Component.translatable(KEY + "checking"), px, fy, AdminTheme.INK_3, false);
            fy += LINE + 2;
        } else {
            WandAdminService.Catalog catalog = ClientAdminWandState.catalog();
            String make = catalog == null ? wood + " + " + core
                    : name(catalog.woods(), wood).getString() + " + " + name(catalog.cores(), core).getString();
            Component verdict = Component.translatable(preview.messageKey(), make);
            for (FormattedCharSequence line : font.split(verdict, pw)) {
                g.drawString(font, line, px, fy, preview.valid() ? AdminTheme.GOOD : AdminTheme.BAD, false);
                fy += LINE;
            }
            fy += 2;
            for (AdminSpellFact fact : preview.facts()) {
                Component text = Component.translatable(fact.labelKey()).append(": ").append(fact.value());
                for (FormattedCharSequence line : font.split(text, pw)) {
                    g.drawString(font, line, px, fy, AdminTheme.INK_2, false);
                    fy += LINE;
                }
            }
        }
        for (FormattedCharSequence line : font.split(Component.translatable(KEY + "generator_note"), pw)) {
            g.drawString(font, line, px, fy + 4, AdminTheme.INK_3, false);
            fy += LINE;
        }

        AdminWandPayloads.ActionReply action = ClientAdminWandState.lastAction();
        if (action != null && System.currentTimeMillis() - ClientAdminWandState.lastActionAt() < 5000) {
            Component message = Component.translatable(action.messageKey(), action.detail());
            int my = y + h - LINE * 2;
            for (FormattedCharSequence line : font.split(message, formW)) {
                g.drawString(font, line, x, my, action.success() ? AdminTheme.GOOD : AdminTheme.BAD, false);
                my += LINE;
            }
        }
    }
}
