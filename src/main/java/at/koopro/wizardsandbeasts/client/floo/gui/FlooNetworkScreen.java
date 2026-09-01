package at.koopro.wizardsandbeasts.client.floo.gui;

import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.floo.FlooDestinationDto;
import at.koopro.wizardsandbeasts.network.floo.FlooCallRequestC2SPayload;
import at.koopro.wizardsandbeasts.network.floo.FlooTravelRequestC2SPayload;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The two ways to name a destination, and the two things a lit grate is for.
 *
 * <h2>Why there are two tabs</h2>
 * <p>A scrolling list of every place you could go, with a Travel button under it, is a warp menu. It
 * is also the only way to reach a hearth whose name you cannot be expected to remember, so it stays —
 * but it is no longer the only door.
 *
 * <p>SPEAK is a text field and nothing else. The player types where they are going, the way a wizard
 * says it into the fire, and gets it slightly wrong at their own risk: a mumbled address still
 * connects, just not necessarily to the address they meant. That risk is the reason speaking is
 * interesting, and the reason picking from the list carries a flat misfire chance instead — with no
 * way to mispronounce a mouse click, the danger has to come from somewhere.
 *
 * <p>The tab is remembered for the session in {@link #lastSpeakMode}, because a player who prefers
 * one door prefers it every time.
 *
 * <h2>Head in the fire</h2>
 * <p>A call is not a journey and does not get the list. You have knelt at a grate and pushed your
 * face into it to talk to somebody; you do that because you know who you are calling. Forcing SPEAK
 * is the mechanical expression of that, and it is also why the frame goes dark — the screen should
 * not look like the one that moves you.
 *
 * <h2>What this screen is not allowed to decide</h2>
 * <p>Nothing. Every refusal is the server's: whether an address exists, whether it is sealed, whether
 * this player may see it, whether they can afford the powder. The greying of a sealed row and the
 * disabled Travel button are courtesies to save a round trip, and the server re-checks all of it. A
 * client that keeps this screen open across a seal change must not be able to travel by having been
 * told "enabled" a minute ago — and it cannot, because {@code FlooTravelHandler} looks again.
 */
public class FlooNetworkScreen extends Screen {

    private static final int PANEL_W = 236;
    private static final int PANEL_H = 216;
    private static final int ENTRY_H = 16;
    private static final int MAX_VISIBLE = 7;
    private static final int LIST_PADDING = 8;

    /** Matches {@code FlooAddress.MAX_LENGTH}; the server validates regardless. */
    private static final int ADDRESS_MAX_LENGTH = 48;

    // -- the palette -----------------------------------------------------------------------------
    // Dark stone and green fire, not a chest. The Floo screen is something you are looking at from
    // inside a fireplace, and vanilla's inventory beige says "container" louder than any text can
    // say "hearth".
    private static final int COL_STONE = 0xF00E1013;
    private static final int COL_STONE_HEAD = 0xF6070809;
    private static final int COL_INSET = 0xC0000000;
    private static final int COL_EMERALD = at.koopro.wizardsandbeasts.floo.FlooCues.EMERALD;
    private static final int COL_EMBER = 0xFF6ADF7F;
    private static final int COL_TITLE = 0xFFD4AF37;
    private static final int COL_ENTRY = 0xFFE8E8E8;
    private static final int COL_SELECTED = 0xFF7BE08F;
    private static final int COL_DISABLED = 0xFF6A6A6A;
    private static final int COL_HOVER = 0xFF8BC34A;
    private static final int COL_MUTED = 0xFF8A8A8A;

    private static boolean lastSpeakMode = false;

    private final List<FlooDestinationDto> destinations;
    private final String originAddress;
    private final boolean headInFire;

    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private boolean speakMode;

    private Button confirmButton;
    private Button listTab;
    private Button speakTab;
    private EditBox addressField;
    private GuiScaleHelper.Layout layout;

    public FlooNetworkScreen(@NonNull List<FlooDestinationDto> destinations,
                             @NonNull String originAddress, boolean headInFire) {
        super(Component.translatable(headInFire
                ? "floo.wizards_and_beasts.gui.title.call"
                : "floo.wizards_and_beasts.gui.title.network"));
        this.destinations = destinations;
        this.originAddress = originAddress;
        this.headInFire = headInFire;
        // A call is always spoken. Forced rather than defaulted, so no path through this screen can
        // send a call built from a list row.
        this.speakMode = headInFire || lastSpeakMode;
    }

    @Override
    protected void init() {
        super.init();
        layout = GuiScaleHelper.Layout.panel(width, height, PANEL_W, PANEL_H);
        int px = layout.panelX();
        int py = layout.panelY();
        int panelW = layout.panelW();
        int panelH = layout.panelH();

        if (!headInFire) {
            int tabW = (panelW - layout.s(24)) / 2;
            listTab = addRenderableWidget(
                    Button.builder(Component.translatable("floo.wizards_and_beasts.gui.tab.known"),
                                    b -> setSpeakMode(false))
                            .bounds(px + layout.s(8), py + layout.s(34), tabW, layout.s(16)).build());
            speakTab = addRenderableWidget(
                    Button.builder(Component.translatable("floo.wizards_and_beasts.gui.tab.speak"),
                                    b -> setSpeakMode(true))
                            .bounds(px + layout.s(12) + tabW, py + layout.s(34), tabW, layout.s(16)).build());
        }

        addressField = new EditBox(font, px + LIST_PADDING, py + layout.s(74),
                panelW - LIST_PADDING * 2, layout.s(18),
                Component.translatable("floo.wizards_and_beasts.gui.speak.hint"));
        addressField.setMaxLength(ADDRESS_MAX_LENGTH);
        addressField.setHint(Component.translatable("floo.wizards_and_beasts.gui.speak.hint"));
        addressField.setResponder(text -> refreshConfirm());
        addRenderableWidget(addressField);

        int btnW = layout.s(84);
        int btnY = py + panelH - layout.s(26);
        confirmButton = addRenderableWidget(
                Button.builder(Component.translatable(headInFire
                                        ? "floo.wizards_and_beasts.gui.button.call"
                                        : "floo.wizards_and_beasts.gui.button.travel"),
                                b -> onConfirm())
                        .bounds(px + panelW / 2 - btnW - layout.s(3), btnY, btnW, layout.s(18)).build());

        addRenderableWidget(
                Button.builder(Component.translatable("floo.wizards_and_beasts.gui.button.cancel"),
                                b -> onClose())
                        .bounds(px + panelW / 2 + layout.s(3), btnY, btnW, layout.s(18)).build());

        applyMode();
    }

    private void setSpeakMode(boolean speak) {
        if (speakMode == speak) {
            return;
        }
        speakMode = speak;
        lastSpeakMode = speak;
        playTick(1.4f);
        applyMode();
    }

    /** Show whichever half of the screen the current tab uses, and nothing of the other. */
    private void applyMode() {
        addressField.visible = speakMode;
        addressField.setEditable(speakMode);
        if (listTab != null) {
            // The active tab is the one you cannot press. Cheaper than a custom widget and it reads
            // correctly: the pressed-looking, unclickable tab is the one you are on.
            listTab.active = speakMode;
            speakTab.active = !speakMode;
        }
        if (speakMode) {
            setFocused(addressField);
            addressField.setFocused(true);
        } else {
            setFocused(null);
        }
        refreshConfirm();
    }

    /**
     * Whether there is anything to confirm right now.
     *
     * <p>Blank-vs-non-blank only in SPEAK. Whether the typed address exists, is sealed, or is one
     * this player may see are all server questions, and answering any of them here would turn the
     * button into an oracle that lights up for private addresses.
     */
    private void refreshConfirm() {
        if (confirmButton == null) {
            return;
        }
        confirmButton.active = speakMode
                ? addressField != null && !addressField.getValue().isBlank()
                : selectedIndex >= 0 && selectedIndex < destinations.size()
                        && destinations.get(selectedIndex).isEnabled();
    }

    // -- rendering -------------------------------------------------------------------------------

    @Override
    public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);
        int px = layout.panelX();
        int py = layout.panelY();
        int panelW = layout.panelW();
        int panelH = layout.panelH();

        drawHearthFrame(graphics, px, py, panelW, panelH);

        graphics.drawCenteredString(font, this.title, px + panelW / 2, py + layout.s(10), COL_TITLE);

        // Subtitle: the grate you are standing in. A network screen that never says where you are is
        // disorienting in exactly the place the fiction is already disorienting.
        if (!originAddress.isBlank()) {
            graphics.drawCenteredString(font,
                    Component.translatable("floo.wizards_and_beasts.gui.from", originAddress),
                    px + panelW / 2, py + layout.s(21), COL_MUTED);
        }

        int contentTop = py + layout.s(headInFire ? 36 : 54);

        if (speakMode) {
            if (headInFire) {
                drawWrapped(graphics, Component.translatable("floo.wizards_and_beasts.gui.head_in_fire"),
                        px + LIST_PADDING, contentTop, panelW - LIST_PADDING * 2, COL_EMBER);
            } else {
                graphics.drawCenteredString(font,
                        Component.translatable("floo.wizards_and_beasts.gui.speak.prompt"),
                        px + panelW / 2, contentTop, COL_ENTRY);
            }
        } else if (destinations.isEmpty()) {
            // An empty network has exactly one cause and one cure, so the empty state says both.
            // "No connected fireplaces found" alone reads as a fault; it is actually the starting
            // state of every world, and a player has no way to guess that a name tag is the answer.
            graphics.drawCenteredString(font, Component.translatable("floo.wizards_and_beasts.gui.empty"),
                    px + panelW / 2, contentTop + layout.s(20), COL_DISABLED);
            graphics.drawCenteredString(font, Component.translatable("floo.wizards_and_beasts.gui.empty_hint"),
                    px + panelW / 2, contentTop + layout.s(32), COL_DISABLED);
        } else {
            renderList(graphics, px, py, panelW, contentTop, mouseX, mouseY);
        }

        graphics.drawCenteredString(font, Component.translatable("floo.wizards_and_beasts.gui.footer"),
                px + panelW / 2, py + panelH - layout.s(38), COL_MUTED);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * Dark stone with green fire licking the inside of the frame.
     *
     * <p>Drawn from fills rather than a texture, on purpose: the border animates, and an animated
     * nine-slice would need a sprite sheet and an mcmeta for something that is four gradients and a
     * sine. The flicker is what stops the panel reading as a static dialog — the fire is supposed to
     * be alive while you are standing in it.
     */
    private void drawHearthFrame(GuiGraphics graphics, int px, int py, int w, int h) {
        graphics.fill(px, py, px + w, py + h, headInFire ? COL_STONE_HEAD : COL_STONE);

        // Two counter-phased sines, so opposite edges never pulse together and the frame never looks
        // like it is breathing as one object.
        float t = (Minecraft.getInstance().level == null ? 0f
                : (Minecraft.getInstance().level.getGameTime() % 200L) / 200.0f) * Mth.TWO_PI;
        float a = 0.62f + 0.38f * Mth.sin(t * 2.0f);
        float b = 0.62f + 0.38f * Mth.sin(t * 2.0f + Mth.PI);

        int thick = Math.max(2, layout.s(2));
        graphics.fill(px, py, px + w, py + thick, flame(a));
        graphics.fill(px, py + h - thick, px + w, py + h, flame(b));
        graphics.fill(px, py, px + thick, py + h, flame(b));
        graphics.fill(px + w - thick, py, px + w, py + h, flame(a));

        // Inner bevel: one dark line inside the fire, which is what makes the border read as a frame
        // around a recess rather than as a coloured rectangle.
        graphics.fill(px + thick, py + thick, px + w - thick, py + thick + 1, 0x60000000);

        // Header divider under the title block.
        int divY = py + layout.s(headInFire ? 30 : 30);
        graphics.fill(px + layout.s(6), divY, px + w - layout.s(6), divY + 1, 0x50FFFFFF);
    }

    /** Emerald at {@code intensity}, kept opaque so the stone never shows through the frame. */
    private static int flame(float intensity) {
        int r = (int) (0x21 * intensity);
        int g = (int) (0xB3 * intensity);
        int bl = (int) (0x42 * intensity);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private void drawWrapped(GuiGraphics graphics, Component text, int x, int y, int wrapWidth, int colour) {
        int lineY = y;
        for (var line : font.split(text, wrapWidth)) {
            graphics.drawString(font, line, x, lineY, colour, false);
            lineY += font.lineHeight + 1;
        }
    }

    private void renderList(@NonNull GuiGraphics graphics, int px, int py, int panelW,
                            int listY, int mouseX, int mouseY) {
        int listX = px + LIST_PADDING;
        int listW = panelW - LIST_PADDING * 2;
        int visibleCount = Math.min(MAX_VISIBLE, destinations.size() - scrollOffset);

        graphics.fill(listX - 2, listY - 2, listX + listW + 2, listY + MAX_VISIBLE * ENTRY_H + 2, COL_INSET);
        graphics.enableScissor(listX, listY, listX + listW, listY + MAX_VISIBLE * ENTRY_H);

        for (int i = 0; i < visibleCount; i++) {
            int idx = i + scrollOffset;
            FlooDestinationDto dto = destinations.get(idx);
            int entryY = listY + i * ENTRY_H;
            boolean hovered = mouseX >= listX && mouseX < listX + listW
                    && mouseY >= entryY && mouseY < entryY + ENTRY_H;

            if (idx == selectedIndex) {
                graphics.fill(listX, entryY, listX + listW, entryY + ENTRY_H, 0x8021B342);
                graphics.fill(listX, entryY, listX + 1, entryY + ENTRY_H, COL_EMERALD);
            } else if (hovered && dto.isEnabled()) {
                graphics.fill(listX, entryY, listX + listW, entryY + ENTRY_H, 0x40808080);
            }

            int textColour;
            if (!dto.isEnabled()) {
                textColour = COL_DISABLED;
            } else if (idx == selectedIndex) {
                textColour = COL_SELECTED;
            } else if (hovered) {
                textColour = COL_HOVER;
            } else {
                textColour = COL_ENTRY;
            }

            String label = dto.networkAddress();
            if (!dto.isEnabled()) {
                label += " " + Component.translatable("floo.wizards_and_beasts.gui.sealed_suffix").getString();
            }
            // Ellipsised, not clipped: an address can now be 48 characters and the row cannot. A hard
            // scissor cut would leave a name that looks like a different, shorter name.
            String shown = font.width(label) > listW - 6
                    ? font.plainSubstrByWidth(label, listW - 12) + "..."
                    : label;

            String dimLabel = formatDimension(dto.dimensionName());
            if (!dimLabel.isEmpty()) {
                graphics.drawString(font, shown, listX + 3, entryY + 2, textColour, false);
                graphics.drawString(font, dimLabel, listX + 3, entryY + 9, COL_DISABLED, false);
            } else {
                graphics.drawString(font, shown, listX + 3, entryY + 4, textColour, false);
            }
        }

        graphics.disableScissor();

        if (destinations.size() > MAX_VISIBLE) {
            int scrollbarX = px + panelW - LIST_PADDING - 2;
            int scrollbarTotalH = MAX_VISIBLE * ENTRY_H;
            int thumbH = Math.max(10, scrollbarTotalH * MAX_VISIBLE / destinations.size());
            int thumbY = listY + (scrollbarTotalH - thumbH) * scrollOffset
                    / Math.max(1, destinations.size() - MAX_VISIBLE);
            graphics.fill(scrollbarX, listY, scrollbarX + 2, listY + scrollbarTotalH, 0x40FFFFFF);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 2, thumbY + thumbH, 0xAA21B342);
        }
    }

    private static @NonNull String formatDimension(@NonNull String dimensionId) {
        if (dimensionId.equals("minecraft:overworld")) return "";
        int colon = dimensionId.lastIndexOf(':');
        return colon >= 0 ? "[" + dimensionId.substring(colon + 1).replace('_', ' ') + "]" : "";
    }

    private int listTop() {
        return layout.panelY() + layout.s(headInFire ? 36 : 54);
    }

    // -- input -----------------------------------------------------------------------------------

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (speakMode) {
            // Enter commits whatever is typed; everything else belongs to the field. Handled before
            // the list bindings so the arrow keys move the caret rather than a selection that is not
            // on screen.
            if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && confirmButton.active) {
                onConfirm();
                return true;
            }
            return super.keyPressed(event);
        }
        if (!destinations.isEmpty()) {
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                int dir = key == GLFW.GLFW_KEY_UP ? -1 : 1;
                int next = (selectedIndex < 0 ? (dir > 0 ? -1 : destinations.size()) : selectedIndex) + dir;
                while (next >= 0 && next < destinations.size() && !destinations.get(next).isEnabled()) {
                    next += dir;
                }
                if (next >= 0 && next < destinations.size()) {
                    select(next);
                    if (selectedIndex < scrollOffset) scrollOffset = selectedIndex;
                    else if (selectedIndex >= scrollOffset + MAX_VISIBLE) scrollOffset = selectedIndex - MAX_VISIBLE + 1;
                }
                return true;
            }
            if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && confirmButton.active) {
                onConfirm();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (speakMode) {
            return false;
        }
        int maxScroll = Math.max(0, destinations.size() - MAX_VISIBLE);
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) Math.signum(scrollY), maxScroll));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (!speakMode && event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();
            int px = layout.panelX();
            int listX = px + LIST_PADDING;
            int listY = listTop();
            int listW = layout.panelW() - LIST_PADDING * 2;
            int visibleCount = Math.min(MAX_VISIBLE, destinations.size() - scrollOffset);

            if (mouseX >= listX && mouseX < listX + listW) {
                for (int i = 0; i < visibleCount; i++) {
                    int entryY = listY + i * ENTRY_H;
                    if (mouseY >= entryY && mouseY < entryY + ENTRY_H) {
                        int idx = i + scrollOffset;
                        if (idx < destinations.size() && destinations.get(idx).isEnabled()) {
                            select(idx);
                            if (isDoubleClick) {
                                onConfirm();
                            }
                        }
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    private void select(int index) {
        boolean changed = selectedIndex != index;
        selectedIndex = index;
        refreshConfirm();
        if (changed) {
            playTick(1.8f);
        }
    }

    /** The soft tick of a name catching in the fire. Local only — nobody else hears you browse. */
    private void playTick(float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    ModSounds.FLOO_WHOOSH.get(), net.minecraft.sounds.SoundSource.BLOCKS,
                    0.22f, pitch, false);
        }
    }

    // -- confirm ---------------------------------------------------------------------------------

    private void onConfirm() {
        String address;
        boolean spoken = speakMode;
        if (speakMode) {
            address = addressField.getValue().trim();
            if (address.isEmpty()) return;
        } else {
            if (selectedIndex < 0 || selectedIndex >= destinations.size()) return;
            FlooDestinationDto dto = destinations.get(selectedIndex);
            // Belt and braces against a sealed row being confirmed: the button is already disabled
            // for one, and the server refuses one, and this is the third. A sealed destination is the
            // one thing this screen must never be able to send.
            if (!dto.isEnabled()) return;
            address = dto.networkAddress();
        }

        if (headInFire) {
            ClientPacketDistributor.sendToServer(new FlooCallRequestC2SPayload(address));
        } else {
            ClientPacketDistributor.sendToServer(new FlooTravelRequestC2SPayload(address, spoken));
        }
        predictSwirl();
        onClose();
    }

    /**
     * A puff of green the instant the button is pressed.
     *
     * <p>Purely cosmetic and purely local. The server is the authority on whether this journey
     * happens at all — it may refuse for a dozen reasons this screen cannot see — so the prediction
     * is deliberately a <em>flourish at the grate</em> and not the departure itself. If the hop is
     * refused a second later the player has seen some sparks, which is honest; predicting the
     * travel would mean animating a journey that never happened.
     */
    private void predictSwirl() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null
                || at.koopro.wizardsandbeasts.Config.reduceScreenEffects) {
            return;
        }
        DustParticleOptions emerald = at.koopro.wizardsandbeasts.floo.FlooCues.emerald(1.3f);
        double cx = mc.player.getX();
        double cy = mc.player.getY() + 0.4;
        double cz = mc.player.getZ();
        for (int i = 0; i < 28; i++) {
            double angle = (Mth.TWO_PI / 28) * i;
            double radius = 0.35 + mc.level.random.nextDouble() * 0.3;
            mc.level.addParticle(emerald,
                    cx + Math.cos(angle) * radius,
                    cy + mc.level.random.nextDouble() * 1.1,
                    cz + Math.sin(angle) * radius,
                    0, 0.05, 0);
        }
        mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, cx, cy, cz, 0, 0.08, 0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
