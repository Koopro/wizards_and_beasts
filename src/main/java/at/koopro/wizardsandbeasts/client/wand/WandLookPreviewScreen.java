package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.customization.WandSlot;
import at.koopro.wizardsandbeasts.wand.recipe.WandmakingRecipe;
import at.koopro.wizardsandbeasts.wand.registry.WandDatapackRegistries;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.wand.visual.WandBondLook;
import at.koopro.wizardsandbeasts.wand.visual.WandLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * Developer preview of the wand look system: cycle a wand's wood, core, length, flexibility, bond and
 * make, and see it drawn by the real renderer at slot size and enlarged, beside the {@link WandLook} it
 * resolved to.
 *
 * <p>Nothing here is a second rendering path. The wand on screen is a stack built by the wandmaker's
 * bench's own routine ({@link WandmakingRecipe#createWand}) and drawn with {@code GuiGraphics.renderItem},
 * so it goes through {@link WandRenderer} exactly as a wand in an inventory does; the bond is set by
 * writing a master and a bond score onto that stack, the way allegiance does. Client-only, never sent
 * anywhere. Opened from the Control Center's Visuals → Debug panel.
 */
@NullMarked
public final class WandLookPreviewScreen extends Screen {

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 250;
    private static final int PAD = 10;
    private static final int LEFT_W = 150;
    private static final int TOP = 26;
    private static final int ROW = 20;
    private static final int ARROW_W = 14;

    private static final int COLOR_TITLE = 0xFF5A3A10;
    private static final int COLOR_LABEL = 0xFF555555;
    private static final int COLOR_VALUE = 0xFF202020;
    private static final int COLOR_PANEL = 0xFFC6C6C6;
    /** Mid grey: a black viewport swallowed blackthorn and walnut, a pale one swallowed holly. */
    private static final int COLOR_VIEWPORT_BG = 0xFF6E6E74;
    private static final int COLOR_DIVIDER = 0xFF555555;

    /** The recipes' range and a little either side, so the clamps are visible too. */
    private static final float[] LENGTHS = {7.0f, 8.5f, 9.5f, 10.5f, 11.5f, 12.25f, 13.0f, 14.0f, 15.0f, 16.5f, 18.0f};
    /** A make offset this small changes the seed without changing the length a player could read. */
    private static final float MAKE_STEP = 1.0e-4f;
    private static final int RARE_SEARCH = 4000;
    /** The bond score each preview bond state is written with: the middle of its band. */
    private static final float[] BOND_SCORES = {1.0f, 1.0f, 1.0f, 0.2f, 0.5f, 0.8f, 1.0f};
    private static final UUID SOMEONE_ELSE = UUID.fromString("00000000-0000-0000-0000-0000000e1de5");

    private final List<Identifier> woods = new ArrayList<>();
    private final List<Identifier> cores = new ArrayList<>();
    private int wood;
    private int core;
    private int length = 5;
    private int flexibility = WandFlexibility.PLIANT.ordinal();
    private int bond = WandBondLook.MASTERED.ordinal();
    private int make;
    private String status = "";

    private GuiScaleHelper.Layout layout;
    private ItemStack stack = ItemStack.EMPTY;
    private @Nullable WandLook look;

    public WandLookPreviewScreen() {
        super(Component.literal("Wand Look Preview"));
    }

    @Override
    protected void init() {
        super.init();
        layout = GuiScaleHelper.Layout.panel(width, height, PANEL_W, PANEL_H);
        if (woods.isEmpty()) {
            HolderLookup.Provider registries = registries();
            woods.addAll(keys(registries, WandDatapackRegistries.WAND_WOOD_REGISTRY));
            cores.addAll(keys(registries, WandDatapackRegistries.WAND_CORE_REGISTRY));
            core = Math.max(0, cores.indexOf(id("phoenix_feather")));
        }
        rebuild();
    }

    private static HolderLookup.@Nullable Provider registries() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : mc.level.registryAccess();
    }

    private static <T> List<Identifier> keys(HolderLookup.@Nullable Provider registries,
                                             ResourceKey<Registry<T>> registry) {
        if (registries == null) {
            return List.of();
        }
        return registries.lookup(registry)
                .map(lookup -> lookup.listElementIds().map(ResourceKey::identifier).sorted().toList())
                .orElse(List.of());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    // ── controls ─────────────────────────────────────────────────────────────

    private void rebuild() {
        clearWidgets();
        int x = layout.panelX() + layout.s(PAD);
        int y = layout.panelY() + layout.s(TOP);
        int w = layout.s(LEFT_W);
        cycler(x, y, w, step -> wood = wrap(wood + step, woods.size()));
        cycler(x, y += layout.s(ROW), w, step -> core = wrap(core + step, cores.size()));
        cycler(x, y += layout.s(ROW), w, step -> length = wrap(length + step, LENGTHS.length));
        cycler(x, y += layout.s(ROW), w, step -> flexibility = wrap(flexibility + step, WandFlexibility.values().length));
        cycler(x, y += layout.s(ROW), w, step -> bond = wrap(bond + step, WandBondLook.values().length));
        cycler(x, y += layout.s(ROW), w, step -> make = Math.max(0, make + step));

        y += layout.s(ROW + 4);
        int half = (w - layout.s(4)) / 2;
        addRenderableWidget(Button.builder(Component.literal("Next rare"), b -> findRare())
                .bounds(x, y, half, layout.s(16)).build());
        addRenderableWidget(Button.builder(Component.literal("Elder Wand"), b -> elderWand())
                .bounds(x + half + layout.s(4), y, half, layout.s(16)).build());
        y += layout.s(ROW);
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(x, y, w, layout.s(16)).build());
        refresh();
    }

    /** ◀ and ▶ either side of a row; the row's label and value are drawn between them. */
    private void cycler(int x, int y, int w, IntConsumer step) {
        int h = layout.s(16);
        int aw = layout.s(ARROW_W);
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> { step.accept(-1); refresh(); })
                .bounds(x, y, aw, h).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> { step.accept(1); refresh(); })
                .bounds(x + w - aw, y, aw, h).build());
    }

    private static int wrap(int value, int size) {
        return size == 0 ? 0 : Math.floorMod(value, size);
    }

    private void findRare() {
        int start = make;
        for (int i = 1; i <= RARE_SEARCH; i++) {
            make = start + i;
            refresh();
            if (look != null && look.rareIndex() >= 0) {
                status = "rare construction " + look.rareIndex() + " at make #" + make;
                return;
            }
        }
        make = start;
        refresh();
        status = woods.isEmpty() ? "" : "no rare construction for " + woods.get(wood).getPath();
    }

    private void elderWand() {
        wood = Math.max(0, woods.indexOf(id("elder")));
        core = Math.max(0, cores.indexOf(id("thestral_tail_hair")));
        refresh();
    }

    /** Rebuilds the preview stack from the controls and resolves its look the way the renderer does. */
    private void refresh() {
        status = "";
        if (woods.isEmpty() || cores.isEmpty()) {
            stack = ItemStack.EMPTY;
            look = null;
            return;
        }
        float inches = LENGTHS[length] + make * MAKE_STEP;
        stack = new WandmakingRecipe(woods.get(wood), cores.get(core), 0f, inches, inches, 1f)
                .createWand(WandFlexibility.values()[flexibility], inches);
        WandBondLook state = WandBondLook.values()[bond];
        Optional<UUID> master = switch (state) {
            case DORMANT -> Optional.empty();
            case AWAITING, UNFAMILIAR -> Optional.of(SOMEONE_ELSE);
            default -> Optional.ofNullable(Minecraft.getInstance().player).map(p -> p.getUUID());
        };
        stack.set(WandComponents.WAND_MASTER.get(), master);
        stack.set(WandComponents.WAND_ALLEGIANCE_SCORE.get(), BOND_SCORES[bond]);
        look = WandLookCache.look(registries(), stack);
    }

    // ── drawing ──────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderMenuBackground(g);
        int px = layout.panelX();
        int py = layout.panelY();
        // Procedural, not drawTexturedPanel: that stretches the demo sheet, whose art covers only its top
        // two thirds, so a tall panel drew short of its own content.
        McStylePanel.drawPanel(g, px, py, layout.panelW(), layout.panelH(), COLOR_PANEL, 0xFFFFFFFF, 0xFF555555);
        g.drawString(font, title, px + (layout.panelW() - font.width(title)) / 2, py + layout.s(8), COLOR_TITLE, false);
        super.render(g, mouseX, mouseY, partialTick);

        int x = px + layout.s(PAD);
        int y = py + layout.s(TOP);
        int w = layout.s(LEFT_W);
        if (woods.isEmpty() || cores.isEmpty()) {
            g.drawString(font, "Join a world: wand woods and cores are synced registries.", x, y, COLOR_LABEL, false);
            return;
        }
        rowLabel(g, x, y, w, "wood", woods.get(wood).getPath());
        rowLabel(g, x, y += layout.s(ROW), w, "core", cores.get(core).getPath());
        rowLabel(g, x, y += layout.s(ROW), w, "length", String.format(Locale.ROOT, "%.2f\"", LENGTHS[length]));
        rowLabel(g, x, y += layout.s(ROW), w, "flex", WandFlexibility.values()[flexibility].getSerializedName());
        rowLabel(g, x, y += layout.s(ROW), w, "bond", WandBondLook.values()[bond].name().toLowerCase(Locale.ROOT));
        rowLabel(g, x, y += layout.s(ROW), w, "make", "#" + make);

        // ── viewport: slot size, the player's own slot, and enlarged ──
        int vx = x + w + layout.s(PAD);
        int vy = py + layout.s(TOP);
        int vw = px + layout.panelW() - layout.s(2 * PAD) - vx;
        int vh = layout.s(120);
        g.fill(vx, vy, vx + vw, vy + vh, COLOR_VIEWPORT_BG);
        McStylePanel.drawBorder(g, vx, vy, vw, vh, COLOR_DIVIDER, COLOR_DIVIDER);
        boolean nobodyHolds = WandBondLook.values()[bond] == WandBondLook.DORMANT
                || WandBondLook.values()[bond] == WandBondLook.AWAITING;
        drawWand(g, vx + layout.s(6), vy + layout.s(6), 1.0f, nobodyHolds);
        drawWand(g, vx + layout.s(6), vy + layout.s(28), 2.0f, nobodyHolds);
        float big = Math.min(vw - layout.s(48), vh - layout.s(8)) / 16.0f;
        drawWand(g, vx + layout.s(44), vy + layout.s(4), big, nobodyHolds);

        if (look != null) {
            drawLook(g, vx, vy + vh + layout.s(6), vw, look);
        }
        if (!status.isEmpty()) {
            g.drawString(font, status, x, py + layout.panelH() - layout.s(14), COLOR_LABEL, false);
        }
    }

    private void rowLabel(GuiGraphics g, int x, int y, int w, String label, String value) {
        int inner = x + layout.s(ARROW_W + 4);
        g.drawString(font, label, inner, y + layout.s(4), COLOR_LABEL, false);
        String clipped = font.plainSubstrByWidth(value, w - layout.s(2 * ARROW_W + 46));
        g.drawString(font, clipped, inner + layout.s(36), y + layout.s(4), COLOR_VALUE, false);
    }

    /**
     * Draws the stack as a slot icon, {@code scale} times slot size. A wand nobody holds is drawn with no
     * owner (as in an item frame); otherwise the local player owns it, as in their inventory.
     */
    private void drawWand(GuiGraphics g, int x, int y, float scale, boolean nobodyHolds) {
        if (stack.isEmpty()) {
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        if (nobodyHolds) {
            g.renderFakeItem(stack, 0, 0);
        } else {
            g.renderItem(stack, 0, 0);
        }
        g.pose().popMatrix();
    }

    private void drawLook(GuiGraphics g, int x, int y, int w, WandLook look) {
        List<String> lines = new ArrayList<>();
        lines.add("grain " + (look.grain() == null ? "plain" : look.grain().getPath())
                + "   tint " + hex(look.tint()) + (look.elder() ? "   ELDER WAND" : ""));
        lines.add("handle " + module(look, WandSlot.HANDLE) + "  shaft " + module(look, WandSlot.SHAFT)
                + "  tip " + module(look, WandSlot.TIP));
        lines.add("ornament " + module(look, WandSlot.ORNAMENT)
                + "   rare " + (look.rareIndex() < 0 ? "-" : "#" + look.rareIndex()));
        lines.add(String.format(Locale.ROOT, "shaft x%.2f  drawn %.1fu  girth x%.2f",
                look.lengthScale(), look.drawnLength(), look.girth()));
        lines.add(String.format(Locale.ROOT, "bow %+.2f°  twist %+.1f°", look.bowDegrees(), look.twistDegrees()));
        WandBondLook state = WandBondLook.values()[bond];
        lines.add(String.format(Locale.ROOT, "light %s %s x%.2f   bond x%.2f sat %.2f%s",
                hex(look.glint()), look.pattern().getSerializedName(), look.glintStrength(),
                state.strength(), state.saturation(), state.reachesPommel() ? "  +pommel" : ""));
        int line = y;
        for (String text : lines) {
            g.drawString(font, font.plainSubstrByWidth(text, w - layout.s(30)), x, line, COLOR_VALUE, false);
            line += layout.s(10);
        }
        // The light as it is right now, rhythm and all -- a slot icon holds one still moment of it.
        int swatch = look.glintArgb(state, Util.getMillis() / 1000.0f);
        int sx = x + w - layout.s(24);
        g.fill(sx, y, sx + layout.s(24), y + layout.s(24), swatch);
        McStylePanel.drawBorder(g, sx, y, layout.s(24), layout.s(24), COLOR_DIVIDER, COLOR_DIVIDER);
    }

    private static String module(WandLook look, WandSlot slot) {
        return look.configuration().getModule(slot).map(Identifier::getPath).orElse("-");
    }

    private static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
