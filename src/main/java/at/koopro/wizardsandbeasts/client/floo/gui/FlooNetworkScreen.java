package at.koopro.wizardsandbeasts.client.floo.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import at.koopro.wizardsandbeasts.client.gui.widget.ScrollList;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedTextField;
import at.koopro.wizardsandbeasts.floo.FlooDestinationDto;
import at.koopro.wizardsandbeasts.network.floo.FlooCallRequestC2SPayload;
import at.koopro.wizardsandbeasts.network.floo.FlooTravelRequestC2SPayload;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
    /**
     * Content inset from the sheet's edge. The list's well is drawn {@code SPACE_S} outside this,
     * and the sheet's double ink rule sits 4 and 6px in, so 16 keeps the well clear of both rules.
     */
    private static final int LIST_PADDING = 16;
    /** Tabs and other furniture: clear of the sheet's ink rule, which anything nearer 12 crosses. */
    private static final int EDGE = WizardsMetrics.SPACE_L;
    /** The hearth sprite's nine-slice border. Content clears it; the fire sits just inside it. */
    private static final int FRAME = WizardsMetrics.PANEL_SPRITE_BORDER;

    /** Matches {@code FlooAddress.MAX_LENGTH}; the server validates regardless. */
    private static final int ADDRESS_MAX_LENGTH = 48;

    // -- the material ----------------------------------------------------------------------------
    // Soot and green fire, not a chest. The Floo screen is something you are looking at from inside
    // a fireplace, and vanilla's inventory beige says "container" louder than any text can say
    // "hearth".
    //
    // `gui/sprites/hearth/` is that material properly cut: a soot-stained sheet from beside the
    // grate, soot ink, and an accent that is FlooCues.EMERALD to the byte. It is paper now, like
    // every screen, so text is soot ink and the green is kept for what it means -- the fire, the
    // row the fire has caught, the line the fire speaks. What survives as a literal is only that.
    private static final GuiSkin SKIN = GuiSkin.HEARTH;

    /** The network's green. Semantic: it is the Floo, not the decor. Furniture only, never text. */
    private static final int COL_EMERALD = at.koopro.wizardsandbeasts.floo.FlooCues.EMERALD;
    /** Selected row: a wash of the fire over the sheet, under soot ink. */
    private static final int COL_SELECTED_WASH = 0x5521B342;
    /** Hovered row: a breath of soot, so hover never competes with the green of a selection. */
    private static final int COL_HOVER_WASH = 0x221E1A18;
    /**
     * A call is not a journey: the light goes out of the sheet. Soot at a sixth, not the old 40%
     * black -- the sheet has to stay light enough for its ink to read.
     */
    private static final int COL_CALL_DIM = 0x2A1E1A18;
    /** {@code SKIN.base()} under {@link #COL_CALL_DIM}: the face a head-in-fire call is read on. */
    private static final int CALL_SHEET = 0xFFAA9F86;
    /**
     * The same green as ink: the line spoken by the fire itself, and a row the cursor is over.
     * Pure emerald is 2 : 1 on the soot sheet, so it is taken down to text contrast with its hue kept
     * -- measured on {@link #CALL_SHEET}, the darker of the two grounds it is read on.
     */
    private static final int COL_FIRE_INK = UiContrast.readableOn(COL_EMERALD, CALL_SHEET);

    private static boolean lastSpeakMode = false;

    private final List<FlooDestinationDto> destinations;
    private final String originAddress;
    private final boolean headInFire;

    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private boolean speakMode;

    private ThemedButton confirmButton;
    private ThemedButton listTab;
    private ThemedButton speakTab;
    private EditBox addressField;
    private GuiScaleHelper.Layout layout;
    /**
     * Geometry and art for the list's scrollbar.
     *
     * <p>The list itself still scrolls on this screen's own {@code scrollOffset}, which the keyboard
     * and wheel handlers already drive correctly; this is handed that offset each frame and asked
     * only to draw. Taking over the scrolling too would be a second source of truth for one number.
     */
    private final ScrollList scrollbar = new ScrollList(ENTRY_H);

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
            int gap = layout.s(4);
            int tabW = (panelW - 2 * layout.s(EDGE) - gap) / 2;
            int tabH = Math.max(MIN_SKINNED, layout.s(16));
            listTab = addRenderableWidget(hearthButton(px + layout.s(EDGE), py + layout.s(34),
                    tabW, tabH, "floo.wizards_and_beasts.gui.tab.known", () -> setSpeakMode(false)));
            speakTab = addRenderableWidget(hearthButton(px + layout.s(EDGE) + tabW + gap, py + layout.s(34),
                    tabW, tabH, "floo.wizards_and_beasts.gui.tab.speak", () -> setSpeakMode(true)));
        }

        // A well pressed into the sheet, written in soot ink: vanilla's field is a black box with
        // white text, the one thing that cannot sit on paper.
        addressField = new ThemedTextField(font, px + LIST_PADDING, py + layout.s(74),
                panelW - LIST_PADDING * 2, Math.max(MIN_SKINNED, layout.s(18)),
                Component.translatable("floo.wizards_and_beasts.gui.speak.hint"))
                .inkHint(Component.translatable("floo.wizards_and_beasts.gui.speak.hint"))
                .skin(SKIN);
        addressField.setMaxLength(ADDRESS_MAX_LENGTH);
        addressField.setResponder(text -> refreshConfirm());
        addRenderableWidget(addressField);

        int btnW = layout.s(84);
        int btnY = py + panelH - layout.s(26);
        int btnH = Math.max(MIN_SKINNED, layout.s(18));
        confirmButton = addRenderableWidget(hearthButton(
                px + panelW / 2 - btnW - layout.s(3), btnY, btnW, btnH,
                headInFire ? "floo.wizards_and_beasts.gui.button.call"
                        : "floo.wizards_and_beasts.gui.button.travel",
                this::onConfirm));

        addRenderableWidget(hearthButton(px + panelW / 2 + layout.s(3), btnY, btnW, btnH,
                "floo.wizards_and_beasts.gui.button.cancel", this::onClose));

        applyMode();
    }

    /**
     * A control cut from this screen's material.
     *
     * <p>{@value #MIN_SKINNED} is the floor a nine-slice needs: the sprites are 32px cut on 8, so
     * below {@code 2 * 8 + 2} the two borders overlap and the face disappears. {@code Layout.panel}
     * can scale to 0.72, and 16 * 0.72 is 11.
     */
    private ThemedButton hearthButton(int x, int y, int w, int h, String key, Runnable action) {
        return ThemedButton.skinned(x, y, Math.max(MIN_SKINNED, w), h,
                Component.translatable(key), action, SKIN);
    }

    /** Smallest a nine-sliced control can be drawn: two 8px borders plus a pixel of face. */
    private static final int MIN_SKINNED = 2 * WizardsMetrics.PANEL_SPRITE_BORDER + 2;

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

        // Soot ink on the sheet, flat. The title was once drawn in the emerald, which on paper is
        // 2 : 1; the green now stays in the fire and the rows it catches.
        drawCentred(graphics, this.title, px + panelW / 2, py + layout.s(10), SKIN.ink());

        // Subtitle: the grate you are standing in. A network screen that never says where you are is
        // disorienting in exactly the place the fiction is already disorienting.
        if (!originAddress.isBlank()) {
            drawCentred(graphics, Component.translatable("floo.wizards_and_beasts.gui.from", originAddress),
                    px + panelW / 2, py + layout.s(21), SKIN.muted());
        }

        int contentTop = py + layout.s(headInFire ? 36 : 54);

        if (speakMode) {
            if (headInFire) {
                drawWrapped(graphics, Component.translatable("floo.wizards_and_beasts.gui.head_in_fire"),
                        px + LIST_PADDING, contentTop, panelW - LIST_PADDING * 2, COL_FIRE_INK);
            } else {
                drawCentred(graphics, Component.translatable("floo.wizards_and_beasts.gui.speak.prompt"),
                        px + panelW / 2, contentTop, SKIN.ink());
            }
        } else if (destinations.isEmpty()) {
            // An empty network has exactly one cause and one cure, so the empty state says both.
            // "No connected fireplaces found" alone reads as a fault; it is actually the starting
            // state of every world, and a player has no way to guess that a name tag is the answer.
            drawCentred(graphics, Component.translatable("floo.wizards_and_beasts.gui.empty"),
                    px + panelW / 2, contentTop + layout.s(20), SKIN.muted());
            drawCentred(graphics, Component.translatable("floo.wizards_and_beasts.gui.empty_hint"),
                    px + panelW / 2, contentTop + layout.s(32), SKIN.muted());
        } else {
            renderList(graphics, px, py, panelW, contentTop, mouseX, mouseY);
        }

        drawCentred(graphics, Component.translatable("floo.wizards_and_beasts.gui.footer"),
                px + panelW / 2, py + panelH - layout.s(38), SKIN.muted());

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Centred and flat: a drop shadow under ink on paper reads as a second copy of the glyph. */
    private void drawCentred(GuiGraphics graphics, Component text, int cx, int y, int colour) {
        graphics.drawString(font, text, cx - font.width(text) / 2, y, colour, false);
    }

    /**
     * The soot-stained sheet with green fire licking the inside of its frame.
     *
     * <p>The fire is drawn from fills rather than a texture, on purpose: the border animates, and an
     * animated nine-slice would need a sprite sheet and an mcmeta for something that is four fills
     * and a sine. The flicker is what stops the panel reading as a static dialog — the fire is
     * supposed to be alive while you are standing in it.
     */
    private void drawHearthFrame(GuiGraphics graphics, int px, int py, int w, int h) {
        McStylePanel.drawSkinPanel(graphics, SKIN, px, py, w, h);
        if (headInFire) {
            // A call is not a journey and should not look like the screen that moves you. The
            // material carries the soot; this is the light going out of it.
            graphics.fill(px + FRAME, py + FRAME, px + w - FRAME, py + h - FRAME, COL_CALL_DIM);
        }

        // Two counter-phased sines, so opposite edges never pulse together and the frame never looks
        // like it is breathing as one object.
        float t = (Minecraft.getInstance().level == null ? 0f
                : (Minecraft.getInstance().level.getGameTime() % 200L) / 200.0f) * Mth.TWO_PI;
        float a = 0.62f + 0.38f * Mth.sin(t * 2.0f);
        float b = 0.62f + 0.38f * Mth.sin(t * 2.0f + Mth.PI);

        // The fire moved inside the sprite's border rather than replacing it. The panel is a
        // nine-slice now and the flame is still four fills, because it animates: an animated
        // nine-slice needs a sheet and an mcmeta for what is two sines, and a static frame would
        // cost the one thing this screen has that no other screen does — the fire is supposed to be
        // alive while you are standing in it.
        int inset = FRAME - 1;
        int thick = Math.max(1, layout.s(2));
        int fx = px + inset;
        int fy = py + inset;
        int fw = w - 2 * inset;
        int fh = h - 2 * inset;
        graphics.fill(fx, fy, fx + fw, fy + thick, flame(a));
        graphics.fill(fx, fy + fh - thick, fx + fw, fy + fh, flame(b));
        graphics.fill(fx, fy, fx + thick, fy + fh, flame(b));
        graphics.fill(fx + fw - thick, fy, fx + fw, fy + fh, flame(a));

        McStylePanel.drawSkinDivider(graphics, SKIN, px + FRAME,
                py + layout.s(30) - WizardsMetrics.DIVIDER_H / 2, w - 2 * FRAME);
    }

    /**
     * Emerald at {@code intensity}, as opacity over the sheet.
     *
     * <p>It used to scale the colour toward black, which on stone read as the fire guttering; on
     * paper it read as a black line. Fading the alpha instead lets the sheet show through a dying
     * flame, and the green never goes dark.
     */
    private static int flame(float intensity) {
        int a = Math.clamp((int) (0xFF * intensity), 0, 0xFF);
        return (a << 24) | (COL_EMERALD & 0x00FFFFFF);
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
        // Rows stop short of the scrollbar. The old bar was two pixels drawn over the rows and got
        // away with it; the shared one is eight, and a row running under it would put an address
        // behind the thumb.
        int rowW = destinations.size() > MAX_VISIBLE
                ? listW - WizardsMetrics.SCROLLBAR_W : listW;
        int visibleCount = Math.min(MAX_VISIBLE, destinations.size() - scrollOffset);

        McStylePanel.drawSkinInset(graphics, SKIN, listX - WizardsMetrics.SPACE_S,
                listY - WizardsMetrics.SPACE_S,
                listW + 2 * WizardsMetrics.SPACE_S,
                MAX_VISIBLE * ENTRY_H + 2 * WizardsMetrics.SPACE_S);
        graphics.enableScissor(listX, listY, listX + listW, listY + MAX_VISIBLE * ENTRY_H);

        for (int i = 0; i < visibleCount; i++) {
            int idx = i + scrollOffset;
            FlooDestinationDto dto = destinations.get(idx);
            int entryY = listY + i * ENTRY_H;
            boolean hovered = mouseX >= listX && mouseX < listX + rowW
                    && mouseY >= entryY && mouseY < entryY + ENTRY_H;

            if (idx == selectedIndex) {
                graphics.fill(listX, entryY, listX + rowW, entryY + ENTRY_H, COL_SELECTED_WASH);
                graphics.fill(listX, entryY, listX + 1, entryY + ENTRY_H, COL_EMERALD);
            } else if (hovered && dto.isEnabled()) {
                graphics.fill(listX, entryY, listX + rowW, entryY + ENTRY_H, COL_HOVER_WASH);
            }

            // The selected row keeps soot ink: its green is the wash under it, and green lettering
            // on a green wash is the one pairing that cannot be read.
            int textColour;
            if (!dto.isEnabled()) {
                textColour = SKIN.muted();
            } else if (idx == selectedIndex) {
                textColour = SKIN.ink();
            } else if (hovered) {
                textColour = COL_FIRE_INK;
            } else {
                textColour = SKIN.ink();
            }

            String label = dto.networkAddress();
            if (!dto.isEnabled()) {
                label += " " + Component.translatable("floo.wizards_and_beasts.gui.sealed_suffix").getString();
            }
            // Ellipsised, not clipped: an address can now be 48 characters and the row cannot. A hard
            // scissor cut would leave a name that looks like a different, shorter name.
            String shown = font.width(label) > rowW - 6
                    ? font.plainSubstrByWidth(label, rowW - 12) + "..."
                    : label;

            String dimLabel = formatDimension(dto.dimensionName());
            if (!dimLabel.isEmpty()) {
                graphics.drawString(font, shown, listX + 3, entryY + 2, textColour, false);
                graphics.drawString(font, dimLabel, listX + 3, entryY + 9, SKIN.muted(), false);
            } else {
                graphics.drawString(font, shown, listX + 3, entryY + 4, textColour, false);
            }
        }

        graphics.disableScissor();

        // Was a hand-rolled two-pixel bar out of two fills. Every skin has shipped a track and a
        // thumb from the start; until `drawSkinScrollbar` existed there was no way to ask for them.
        scrollbar.setBounds(listX, listY, listW, MAX_VISIBLE * ENTRY_H);
        scrollbar.setItemCount(destinations.size());
        scrollbar.setScrollOffset(scrollOffset);
        scrollbar.renderScrollbar(graphics, SKIN);
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
