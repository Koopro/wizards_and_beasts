package at.koopro.wizardsandbeasts.client.trinket.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import at.koopro.wizardsandbeasts.client.gui.widget.ScrollList;
import at.koopro.wizardsandbeasts.memory.MemoryEntry;
import at.koopro.wizardsandbeasts.memory.MemoryType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The basin: every memory drawn out of a wizard's head, newest first.
 *
 * <p>Read-only. It browses the snapshot delivered by
 * {@link at.koopro.wizardsandbeasts.network.trinket.PensieveOpenS2CPayload} and decides nothing.
 *
 * <h2>What changed</h2>
 *
 * <p><strong>It had no English of its own; it had only English.</strong> Every string on this screen
 * was a Java literal with section codes baked in — {@code "§dPensieve §7— stored memories"},
 * {@code "§8The basin is still. No memories drawn."}, {@code "[Happy]"}, {@code "s ago"}, and a
 * plural built by concatenating {@code "memor" + (n == 1 ? "y" : "ies")}, which is a rule that holds
 * in English and almost nowhere else. All of it is lang keys now.
 *
 * <p><strong>The scrollbar was never drawn.</strong> {@code mouseScrolled} clamped an offset against
 * a {@code maxScroll()} that worked correctly, and nothing on screen said the list could move. It is
 * a {@link ScrollList} now, which draws the bar it scrolls.
 *
 * <p><strong>Selecting a row did nothing.</strong> A click toggled an index that changed only the
 * row's own tint. There is a detail band under the list now, so a selection has somewhere to go.
 *
 * <p><strong>The intensity bar was ten pipe characters.</strong> {@code "§d||||||...."} — a glyph
 * count standing in for a bar, at whatever width the font happened to give it.
 *
 * <p>Cut on the {@code pensieve} material: dark wet stone with an aubergine cast, and the
 * silver-white light a memory gives off. The old screen's {@code #100A1A} field and {@code #5B4B8A}
 * outline were the right instinct, hand-rolled — the hues survive, the {@code fill()} calls do not.
 */
public class PensieveScreen extends Screen {

    private static final GuiSkin SKIN = GuiSkin.PENSIEVE;

    private static final int PANEL_W = WizardsMetrics.PANEL_STANDARD_W;
    private static final int PANEL_H = WizardsMetrics.PANEL_STANDARD_H;
    private static final int FRAME = WizardsMetrics.PANEL_SPRITE_BORDER;
    private static final int PAD = WizardsMetrics.SPACE_M;

    private static final int HEADER_H = 30;
    /** Two lines and a bar, so a row can carry the source, the age and the strength together. */
    private static final int ROW_H = 24;
    private static final int DETAIL_H = 46;
    private static final int FOOTER_H = 16;
    private static final int BAR_H = 4;

    /** Ticks in a second, and the units the age readout steps through. */
    private static final long TICKS_PER_SECOND = 20L;
    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long MINUTES_PER_HOUR = 60L;
    private static final long HOURS_PER_DAY = 24L;

    private final List<MemoryEntry> memories;
    private ScrollList list;

    /** Memory-type inks, lifted onto this material rather than borrowed from a chat palette. */
    private final int happyInk;
    private final int painfulInk;

    private GuiScaleHelper.Layout layout;
    private int panelX;
    private int panelY;
    private int selected = -1;

    public PensieveScreen(List<MemoryEntry> memories) {
        super(Component.translatable("gui.wizards_and_beasts.pensieve.title"));
        this.memories = new ArrayList<>(memories);
        this.memories.sort(Comparator.comparingLong(MemoryEntry::createdGameTime).reversed());
        this.happyInk = UiContrast.readableOn(0xFFAA00, SKIN.base(), UiContrast.AA_TEXT);
        this.painfulInk = UiContrast.readableOn(0xFF5555, SKIN.base(), UiContrast.AA_TEXT);
    }

    /**
     * Sized and placed in screen space, with no pose pushed.
     *
     * <p>The old screen applied a scale and unmapped every mouse coordinate back through it. The
     * skinned screens in this mod pre-multiply their coordinates through {@link GuiScaleHelper} at
     * layout time instead and work in screen space throughout, which is both simpler and the reason
     * a tooltip issued from here needs no special handling.
     */
    @Override
    protected void init() {
        super.init();
        layout = GuiScaleHelper.Layout.panel(width, height, PANEL_W, PANEL_H);
        panelX = layout.panelX();
        panelY = layout.panelY();
        // Built here rather than at field init: a row's height depends on the scale, which does not
        // exist until the screen has a size.
        list = new ScrollList(rowStride());

        int listX = panelX + FRAME + PAD;
        int listY = panelY + headerH();
        int listW = layout.panelW() - 2 * (FRAME + PAD);
        int listH = listHeight();
        list.setBounds(listX, listY, listW, listH);
        list.setItemCount(memories.size());
    }

    // Bands scale with the panel. `Layout.panel` clamps between 0.72x and 1.35x and floors the
    // panel at 90px tall, so fixed bands would sum past the frame at the small end -- 30 + 46 + 16
    // plus a gap is 100, and a list asked for -10 pixels draws its rows outside the panel.
    private int headerH() {
        return layout.s(HEADER_H);
    }

    private int detailH() {
        return layout.s(DETAIL_H);
    }

    private int rowStride() {
        return Math.max(font.lineHeight * 2 + WizardsMetrics.SPACE_S, layout.s(ROW_H));
    }

    /** Whatever is left for the list, but never less than one row. */
    private int listHeight() {
        int remaining = layout.panelH() - headerH() - detailH()
                - layout.s(FOOTER_H) - WizardsMetrics.SPACE_M;
        return Math.max(rowStride(), remaining);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return list.mouseScrolled(scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() == 0) {
            Integer row = list.indexAt(event.x(), event.y());
            if (row != null) {
                selected = selected == row ? -1 : row;
                return true;
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderMenuBackground(graphics);

        int panelW = layout.panelW();
        int panelH = layout.panelH();
        McStylePanel.drawSkinPanel(graphics, SKIN, panelX, panelY, panelW, panelH);
        McStylePanel.drawSkinSeal(graphics, SKIN,
                panelX + panelW - FRAME - McStylePanel.SEAL_SIZE, panelY + FRAME);
        McStylePanel.drawSkinDivider(graphics, SKIN, panelX + FRAME,
                panelY + headerH() - WizardsMetrics.DIVIDER_H, panelW - 2 * FRAME);

        graphics.drawString(font, this.title, panelX + FRAME + PAD, panelY + FRAME + 2,
                SKIN.ink(), false);

        if (memories.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.wizards_and_beasts.pensieve.empty"),
                    panelX + panelW / 2, panelY + panelH / 2 - font.lineHeight, SKIN.muted());
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        renderList(graphics, mouseX, mouseY);
        renderDetail(graphics);
        renderFooter(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listX = list.rowLeft();
        int listY = panelY + headerH();
        McStylePanel.drawSkinInset(graphics, SKIN, listX - WizardsMetrics.SPACE_S,
                listY - WizardsMetrics.SPACE_S,
                layout.panelW() - 2 * (FRAME + PAD) + 2 * WizardsMetrics.SPACE_S,
                listHeight() + 2 * WizardsMetrics.SPACE_S);

        long now = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getGameTime() : 0L;
        Integer hovered = list.indexAt(mouseX, mouseY);
        int rowW = list.rowWidth();

        for (int index = list.firstVisible(); index < list.lastVisibleExclusive(); index++) {
            int rowY = list.rowTop(index);
            MemoryEntry memory = memories.get(index);
            boolean isSelected = index == selected;

            McStylePanel.drawSkinRow(graphics, SKIN, listX, rowY, rowW, list.rowHeight() - 2,
                    isSelected || (hovered != null && hovered == index));

            int tx = listX + WizardsMetrics.SPACE_S;
            int tw = rowW - 2 * WizardsMetrics.SPACE_S;
            graphics.drawString(font, typeLabel(memory.type()), tx, rowY + 2,
                    typeInk(memory.type()), false);

            int nameX = tx + font.width(typeLabel(memory.type())) + WizardsMetrics.SPACE_S;
            GuiText.drawFitted(graphics, font, prettySource(memory.source()), nameX, rowY + 2,
                    tw - (nameX - tx), SKIN.ink());

            renderIntensity(graphics, tx, rowY + 2 + WizardsMetrics.LINE_TIGHT + 1,
                    tw / 2, memory.intensity());
            String age = ageText(now, memory.createdGameTime()).getString();
            graphics.drawString(font, age, listX + rowW - WizardsMetrics.SPACE_S - font.width(age),
                    rowY + 2 + WizardsMetrics.LINE_TIGHT, SKIN.muted(), false);
        }

        list.renderScrollbar(graphics, SKIN);
    }

    /**
     * How strongly the memory is felt, as a drawn bar.
     *
     * <p>This was ten {@code |} characters in a magenta section code — a glyph count standing in for
     * a bar, quantised to tenths for no reason but that ten pipes was a convenient number to type.
     */
    private void renderIntensity(GuiGraphics graphics, int x, int y, int w, float intensity) {
        float clamped = Math.max(0.0f, Math.min(1.0f, intensity));
        graphics.fill(x, y, x + w, y + BAR_H, SKIN.frame());
        graphics.fill(x + 1, y + 1, x + 1 + (int) ((w - 2) * clamped), y + BAR_H - 1, SKIN.accent());
    }

    /** The selected memory in full, in the band below the list. */
    private void renderDetail(GuiGraphics graphics) {
        int x = panelX + FRAME + PAD;
        int w = layout.panelW() - 2 * (FRAME + PAD);
        int y = panelY + headerH() + listHeight() + WizardsMetrics.SPACE_M;

        McStylePanel.drawSkinInset(graphics, SKIN, x - WizardsMetrics.SPACE_S, y,
                w + 2 * WizardsMetrics.SPACE_S, detailH());

        int tx = x + WizardsMetrics.SPACE_S;
        int ty = y + WizardsMetrics.SPACE_M;
        if (selected < 0 || selected >= memories.size()) {
            graphics.drawString(font,
                    Component.translatable("gui.wizards_and_beasts.pensieve.pick"),
                    tx, ty, SKIN.muted(), false);
            return;
        }

        MemoryEntry memory = memories.get(selected);
        long now = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getGameTime() : 0L;

        graphics.drawString(font, typeLabel(memory.type()), tx, ty, typeInk(memory.type()), false);
        GuiText.drawFitted(graphics, font, prettySource(memory.source()),
                tx + font.width(typeLabel(memory.type())) + WizardsMetrics.SPACE_S, ty,
                w - font.width(typeLabel(memory.type())) - WizardsMetrics.SPACE_L, SKIN.ink());
        ty += WizardsMetrics.LINE_BODY;

        graphics.drawString(font, Component.translatable(
                        "gui.wizards_and_beasts.pensieve.intensity",
                        Math.round(memory.intensity() * 100.0f)),
                tx, ty, SKIN.muted(), false);
        ty += WizardsMetrics.LINE_TIGHT;
        graphics.drawString(font, Component.translatable(
                        "gui.wizards_and_beasts.pensieve.drawn", ageText(now, memory.createdGameTime())),
                tx, ty, SKIN.muted(), false);
    }

    /** How many memories there are, and whether the list moves. */
    private void renderFooter(GuiGraphics graphics) {
        Component count = Component.translatable("gui.wizards_and_beasts.pensieve.count",
                memories.size());
        Component note = list.scrollable()
                ? Component.translatable("gui.wizards_and_beasts.pensieve.count_scroll", count)
                : count;
        graphics.drawString(font, note, panelX + FRAME + PAD,
                panelY + layout.panelH() - FRAME - font.lineHeight, SKIN.muted(), false);
    }

    private Component typeLabel(MemoryType type) {
        return Component.translatable(
                "gui.wizards_and_beasts.pensieve.type." + type.name().toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * The type's colour on this material.
     *
     * <p>Happy and painful were {@code §6} and {@code §4} — chat colours, chosen for a black chat
     * window. Mundane is the material's own second voice, which is what "unremarkable" should look
     * like here rather than a grey borrowed from somewhere else.
     */
    private int typeInk(MemoryType type) {
        return switch (type) {
            case HAPPY -> happyInk;
            case PAINFUL -> painfulInk;
            case MUNDANE -> SKIN.muted();
        };
    }

    /**
     * A readable name for whatever produced the memory.
     *
     * <p>Still a mechanical transform of an id, and still English-shaped. It is left that way on
     * purpose: sources are arbitrary strings from wherever a memory was captured, so there is no key
     * to translate — unlike the type, the age and the headings, which now have them.
     */
    private static String prettySource(String source) {
        if (source == null || source.isBlank()) {
            return Component.translatable("gui.wizards_and_beasts.pensieve.unknown_source").getString();
        }
        String cleaned = source.contains(":") ? source.substring(source.indexOf(':') + 1) : source;
        return cleaned.replace('_', ' ').replace('.', ' ');
    }

    /**
     * How long ago, in the largest unit that still reads as a number.
     *
     * <p>Was {@code seconds + "s ago"} and three siblings. The unit letters and the word order are a
     * translator's business, so each step is its own key.
     */
    private static Component ageText(long now, long created) {
        long seconds = Math.max(0, now - created) / TICKS_PER_SECOND;
        if (seconds < SECONDS_PER_MINUTE) {
            return Component.translatable("gui.wizards_and_beasts.pensieve.age.seconds", seconds);
        }
        long minutes = seconds / SECONDS_PER_MINUTE;
        if (minutes < MINUTES_PER_HOUR) {
            return Component.translatable("gui.wizards_and_beasts.pensieve.age.minutes", minutes);
        }
        long hours = minutes / MINUTES_PER_HOUR;
        if (hours < HOURS_PER_DAY) {
            return Component.translatable("gui.wizards_and_beasts.pensieve.age.hours", hours);
        }
        return Component.translatable("gui.wizards_and_beasts.pensieve.age.days", hours / HOURS_PER_DAY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
