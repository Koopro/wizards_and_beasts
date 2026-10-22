package at.koopro.wizardsandbeasts.client.trinket.gui;

import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import net.minecraft.resources.Identifier;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedTextField;
import at.koopro.wizardsandbeasts.network.trinket.DiaryWriteC2SPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Write-into-the-diary dialogue. Your lines and Riddle's replies scroll in a transcript.
 *
 * <p>The diary itself: worn black leather with one gold-tooled line, an aged cream page inside, ruled
 * under every line of the transcript with a faint red margin, and the writer's line written straight onto
 * the page (art from {@code tools/gui_chrome.py}, {@code artefact/diary/}). In the book the writer's ink
 * sinks into the paper and Riddle's answer surfaces in ink of its own. The menace is in the replies, whose
 * ink darkens tier by tier towards blood red; the possession itself, where the diary swallows the
 * screen, is {@link DiaryPossessionScreen} and stays dark.
 */
public class DiaryWriteScreen extends Screen {

    private static Identifier art(String name) {
        return Identifier.fromNamespaceAndPath(at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID,
                "textures/gui/artefact/diary/" + name + ".png");
    }

    @Nullable
    private static DiaryWriteScreen active;

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 200;
    private static final int MAX_LINES = 80;

    /** How much black cover shows round the page. */
    private static final int COVER = 10;
    /** Text clearance from the panel's edge: the cover, then the page's own margin. */
    private static final int EDGE = 18;
    private static final int TITLE_Y = 17;
    private static final int TITLE_RULE_Y = 27;
    private static final int LINE_H = 10;
    private static final int TEXT_W = PANEL_W - 2 * EDGE;
    private static final int CONTROL_H = 18;
    private static final int CONTROL_Y = PANEL_H - EDGE - CONTROL_H;
    private static final int WRITE_W = 64;
    private static final int FIELD_W = TEXT_W - WRITE_W - 8;
    private static final String KEY = "gui.wizards_and_beasts.diary.";

    private static final Identifier COVER_TEX = art("cover");
    private static final Identifier PAGE_TEX = art("page");
    /** The page's stock, for clamping inks onto it (gui_chrome.py DIARY paper). */
    private static final int PAGE = 0xFFDDD1AF;
    /** Ruling: a faded blue-grey, under each line of text. */
    private static final int RULING = 0x709DA9B5;
    /** The margin, in the faded red diaries of the day used. */
    private static final int MARGIN = 0x80B05A4E;
    /** The writer's own lines: iron-gall ink, as on any page. */
    private static final int WRITER_INK = 0xFF2A2118;
    private static final int HINT_INK = 0xFF6E6150;
    /** Leather tab label: the page's cream on the cover's black. */
    private static final int TAB_INK = 0xFFE8DEC1;
    private static final int TAB_INK_OFF = 0xFF7A706A;
    /**
     * Riddle's replies by tier: the grey, dark grey, purple and dark red the transcript used to take
     * from chat codes, kept as hues but lifted onto the page so the calm first tier is still legible.
     */
    private static final int[] REPLY_INK = {
            UiContrast.readableOn(0xAAAAAA, PAGE, UiContrast.AA_TEXT),
            UiContrast.readableOn(0x555555, PAGE, UiContrast.AA_TEXT),
            UiContrast.readableOn(0xAA00AA, PAGE, UiContrast.AA_TEXT),
            UiContrast.readableOn(0xAA0000, PAGE, UiContrast.AA_TEXT),
    };

    /** One transcript entry, unwrapped; it is split to the page width when drawn. */
    private record Line(Component text, int colour) {}

    private record Row(FormattedCharSequence text, int colour) {}

    private final List<Line> transcript = new ArrayList<>();
    private ThemedTextField input;
    private GuiScaleHelper.Layout layout;
    private int panelX;
    private int panelY;

    public DiaryWriteScreen() {
        super(Component.translatable(KEY + "title"));
    }

    /** Appends one of Riddle's replies to the active transcript. */
    public static void appendReply(String line, int tier) {
        if (active != null) {
            int ink = REPLY_INK[Math.max(0, Math.min(REPLY_INK.length - 1, tier))];
            active.add(new Line(Component.translatable(KEY + "riddle").withStyle(ChatFormatting.ITALIC)
                    .append(Component.literal(" " + line).withStyle(style -> style.withItalic(false))), ink));
        }
    }

    private void add(Line line) {
        transcript.add(line);
        while (transcript.size() > MAX_LINES) {
            transcript.remove(0);
        }
    }

    @Override
    protected void init() {
        super.init();
        active = this;
        layout = GuiScaleHelper.Layout.fit(width, height, PANEL_W, PANEL_H);
        panelX = layout.panelX();
        panelY = layout.panelY();
        // Widgets live in screen space, so their bounds are scaled to line up
        // with the pose-scaled panel art drawn in render().
        input = new ThemedTextField(font, layout.x(EDGE), layout.y(CONTROL_Y),
                layout.s(FIELD_W), layout.s(CONTROL_H), Component.translatable(KEY + "input"))
                .bare(WRITER_INK, HINT_INK, WRITER_INK)
                .inkHint(Component.translatable(KEY + "hint"));
        input.setMaxLength(80);
        addRenderableWidget(input);
        setInitialFocus(input);
        addRenderableWidget(ThemedButton.skinned(layout.x(PANEL_W - EDGE - WRITE_W), layout.y(CONTROL_Y),
                layout.s(WRITE_W), layout.s(CONTROL_H), Component.translatable(KEY + "write"), this::submit,
                "diary", null, 0, TAB_INK, TAB_INK_OFF));
    }

    private void submit() {
        String text = input.getValue().trim();
        if (text.isEmpty()) {
            return;
        }
        add(new Line(Component.literal(text), WRITER_INK));
        ClientPacketDistributor.sendToServer(new DiaryWriteC2SPayload(text));
        input.setValue("");
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && input.isFocused()) {
            submit();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (active == this) {
            active = null;
        }
        super.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);
        layout.applyScale(graphics);
        McStylePanel.drawNineSliceTiled(graphics, COVER_TEX, panelX, panelY, PANEL_W, PANEL_H,
                WizardsMetrics.PANEL_SPRITE_SIZE, WizardsMetrics.PANEL_SPRITE_BORDER);
        McStylePanel.drawNineSliceTiled(graphics, PAGE_TEX, panelX + COVER, panelY + COVER,
                PANEL_W - 2 * COVER, PANEL_H - 2 * COVER,
                WizardsMetrics.PANEL_SPRITE_SIZE, WizardsMetrics.PANEL_SPRITE_BORDER);
        graphics.drawString(font, Component.translatable(KEY + "heading"), panelX + EDGE, panelY + TITLE_Y,
                WRITER_INK, false);
        // A printed double rule under the heading, then the margin and the ruling the lines sit on.
        graphics.fill(panelX + EDGE, panelY + TITLE_RULE_Y - 2, panelX + EDGE + TEXT_W, panelY + TITLE_RULE_Y - 1, HINT_INK);
        graphics.fill(panelX + EDGE, panelY + TITLE_RULE_Y, panelX + EDGE + TEXT_W, panelY + TITLE_RULE_Y + 1, HINT_INK);
        graphics.fill(panelX + EDGE - 4, panelY + TITLE_RULE_Y + 2, panelX + EDGE - 3, panelY + PANEL_H - COVER - 2, MARGIN);
        int ruleTop = panelY + TITLE_RULE_Y + LINE_H;
        for (int y = ruleTop + LINE_H - 1; y < panelY + CONTROL_Y; y += LINE_H) {
            graphics.fill(panelX + COVER + 2, y, panelX + PANEL_W - COVER - 2, y + 1, RULING);
        }
        graphics.fill(panelX + EDGE, panelY + CONTROL_Y + CONTROL_H - 1, panelX + EDGE + FIELD_W,
                panelY + CONTROL_Y + CONTROL_H, RULING);

        renderTranscript(graphics);

        graphics.pose().popMatrix();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * The newest lines that fit, oldest at the top, each wrapped to the page.
     *
     * <p>Wrapped because an 80-character line is wider than the sheet, and unwrapped it ran out
     * through the frame.
     */
    private void renderTranscript(GuiGraphics graphics) {
        int top = panelY + TITLE_RULE_Y + LINE_H;
        int bottom = panelY + CONTROL_Y - 4;
        int maxRows = (bottom - top) / LINE_H;

        List<Row> rows = new ArrayList<>();
        for (int i = transcript.size() - 1; i >= 0 && rows.size() < maxRows; i--) {
            Line line = transcript.get(i);
            List<FormattedCharSequence> split = font.split(line.text(), TEXT_W);
            for (int j = split.size() - 1; j >= 0 && rows.size() < maxRows; j--) {
                rows.add(new Row(split.get(j), line.colour()));
            }
        }

        int y = top;
        for (int k = rows.size() - 1; k >= 0; k--) {
            Row row = rows.get(k);
            graphics.drawString(font, row.text(), panelX + EDGE, y, row.colour(), false);
            y += LINE_H;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
