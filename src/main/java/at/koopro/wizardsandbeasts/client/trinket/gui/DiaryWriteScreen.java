package at.koopro.wizardsandbeasts.client.trinket.gui;

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
 * <p>A page of the diary, so it is drawn on the parchment sheet: in the book the writer's ink sinks
 * into the paper and Riddle's answer surfaces in ink of its own. The menace is in the replies, whose
 * ink darkens tier by tier towards blood red; the possession itself, where the diary swallows the
 * screen, is {@link DiaryPossessionScreen} and stays dark.
 */
public class DiaryWriteScreen extends Screen {

    @Nullable
    private static DiaryWriteScreen active;

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 200;
    private static final int MAX_LINES = 80;

    /** Clearance from the sheet's edge: its double rule runs 4-6px in. */
    private static final int EDGE = 12;
    private static final int TITLE_Y = 10;
    private static final int TITLE_RULE_Y = 18;
    private static final int LINE_H = 10;
    private static final int TEXT_W = PANEL_W - 2 * EDGE;
    private static final int CONTROL_H = 18;
    private static final int CONTROL_Y = PANEL_H - EDGE - CONTROL_H;
    private static final int WRITE_W = 64;
    private static final int FIELD_W = TEXT_W - WRITE_W - 8;
    /** Where the borderless field's text sits inside its well. */

    /** The writer's own lines: plain ink, as on any page. */
    private static final int WRITER_INK = WizardsPalette.PAGE_INK;
    /**
     * Riddle's replies by tier: the grey, dark grey, purple and dark red the transcript used to take
     * from chat codes, kept as hues but lifted onto the page so the calm first tier is still legible.
     */
    private static final int[] REPLY_INK = {
            UiContrast.readableOn(0xAAAAAA, WizardsPalette.PAGE, UiContrast.AA_TEXT),
            UiContrast.readableOn(0x555555, WizardsPalette.PAGE, UiContrast.AA_TEXT),
            UiContrast.readableOn(0xAA00AA, WizardsPalette.PAGE, UiContrast.AA_TEXT),
            UiContrast.readableOn(0xAA0000, WizardsPalette.PAGE, UiContrast.AA_TEXT),
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
        super(Component.literal("Riddle's Diary"));
    }

    /** Appends one of Riddle's replies to the active transcript. */
    public static void appendReply(String line, int tier) {
        if (active != null) {
            int ink = REPLY_INK[Math.max(0, Math.min(REPLY_INK.length - 1, tier))];
            active.add(new Line(Component.literal("T. M. Riddle: ").withStyle(ChatFormatting.ITALIC)
                    .append(Component.literal(line).withStyle(style -> style.withItalic(false))), ink));
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
                layout.s(FIELD_W), layout.s(CONTROL_H), Component.literal("write"))
                .inkHint(Component.literal("write a line…"));
        input.setTextColor(WRITER_INK);
        input.setMaxLength(80);
        addRenderableWidget(input);
        setInitialFocus(input);
        addRenderableWidget(new ThemedButton(layout.x(PANEL_W - EDGE - WRITE_W), layout.y(CONTROL_Y),
                layout.s(WRITE_W), layout.s(CONTROL_H), Component.literal("Write"), this::submit));
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
        McStylePanel.drawThemedPanel(graphics, panelX, panelY, PANEL_W, PANEL_H);
        graphics.drawString(font, "A half-blank diary, dated 1943", panelX + EDGE, panelY + TITLE_Y,
                WizardsPalette.PAGE_INK, false);
        McStylePanel.drawDivider(graphics, panelX + EDGE, panelY + TITLE_RULE_Y, TEXT_W);

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
