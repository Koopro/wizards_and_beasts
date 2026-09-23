package at.koopro.wizardsandbeasts.client.gui.config;

import java.util.function.Supplier;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedTextField;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.NonNull;

/**
 * Blood Quill gate modal shown before entering the Dark Arts config category.
 * The player must type their own username; a wrong name triggers a 40-tick red
 * splatter failure animation and clears the field.
 *
 * <p>Still paper and ink like every other screen, but a different sheet: the soot-stained stock
 * from the hearth with wax-red furniture ({@code tools/config_textures.py}), so it reads as a
 * warrant rather than a settings page.
 */
public class DarkArtsGateScreen extends Screen {
    private static final int FAIL_TICKS = 40;
    private static final int MODAL_WIDTH = 248;
    /** Room for the button to clear the frame's double rule at 4 and 6 from the bottom edge. */
    private static final int MODAL_HEIGHT = 136;
    private static final Identifier GATE_PANEL = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/gui/config/gate_panel.png");
    private static final int DIM = 0xAA000000;
    private static final int TITLE_TEXT = WizardsPalette.PAGE_RUBRIC;
    /** The soot stock's own ink, one shade blacker than the default page ink. */
    private static final int BODY_TEXT = WizardsPalette.GuiSkin.HEARTH.ink();
    private static final int ERROR_TEXT = WizardsPalette.PAGE_BAD;
    /** Blood from the quill. Semantic, not a page colour: the splatter is the failure. */
    private static final int SPLAT_DARK = 0xCC8B0000;
    private static final int SPLAT_LIGHT = 0xCCB01010;

    private final Screen parent;
    private final Supplier<Screen> target;
    private GuiScaleHelper.Layout layout;
    private ThemedTextField nameField;
    private ThemedButton confirmButton;
    private int failTicks;
    private long failSeed;

    public DarkArtsGateScreen(Screen parent, Supplier<Screen> target) {
        super(Component.literal("Unforgivable Arts — Restricted Access"));
        this.parent = parent;
        this.target = target;
    }

    @Override
    protected void init() {
        super.init();
        layout = GuiScaleHelper.Layout.fit(width, height, MODAL_WIDTH, MODAL_HEIGHT);

        nameField = new ThemedTextField(font, layout.x(24), layout.y(70),
                layout.s(MODAL_WIDTH - 48), layout.s(18), Component.literal("Username"))
                .inkHint(Component.literal("Enter your Minecraft username"));
        addRenderableWidget(nameField);

        confirmButton = new ThemedButton(layout.x(MODAL_WIDTH / 2 - 70), layout.y(MODAL_HEIGHT - 32),
                layout.s(140), layout.s(20), Component.literal("Sign with Blood Quill"), this::onConfirm)
                .tone(McStylePanel.ButtonTone.DANGER);
        addRenderableWidget(confirmButton);

        setInitialFocus(nameField);
    }

    private void onConfirm() {
        String expected = Minecraft.getInstance().getUser().getName();
        if (nameField.getValue().equals(expected)) {
            Minecraft.getInstance().setScreen(target.get());
        } else {
            failTicks = FAIL_TICKS;
            // New seed per failure so splatter positions vary between attempts.
            failSeed = Util.getMillis();
            nameField.setValue("");
            nameField.setEditable(false);
            confirmButton.active = false;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (failTicks > 0 && --failTicks == 0) {
            nameField.setEditable(true);
            confirmButton.active = true;
        }
    }

    @Override
    public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        layout.applyScale(graphics);
        int boxX = layout.panelX();
        int boxY = layout.panelY();
        int cx = boxX + MODAL_WIDTH / 2;

        McStylePanel.drawNineSliceTiled(graphics, GATE_PANEL, boxX, boxY, MODAL_WIDTH, MODAL_HEIGHT,
                WizardsMetrics.PANEL_SPRITE_SIZE, WizardsMetrics.PANEL_SPRITE_BORDER);

        ConfigWidgets.drawCenteredNoShadow(graphics, font, "UNFORGIVABLE ARTS — RESTRICTED ACCESS",
                cx, boxY + 12, TITLE_TEXT);
        McStylePanel.drawSkinDivider(graphics, WizardsPalette.GuiSkin.HEARTH,
                boxX + WizardsMetrics.SPACE_L, boxY + 20, MODAL_WIDTH - 2 * WizardsMetrics.SPACE_L);
        // Wrapped: the first line is wider than the modal, and on a sheet with a torn edge it would
        // run off the paper instead of merely off a dark box.
        int textY = boxY + 32;
        for (String paragraph : new String[] {
                "\"By entering your name you acknowledge full magical responsibility.\"",
                "\"The Ministry has been notified.\""}) {
            for (FormattedCharSequence line : font.split(Component.literal(paragraph),
                    MODAL_WIDTH - 2 * WizardsMetrics.SPACE_L)) {
                graphics.drawString(font, line, cx - font.width(line) / 2, textY, BODY_TEXT, false);
                textY += font.lineHeight;
            }
            textY += 3;
        }

        graphics.pose().popMatrix();
        super.render(graphics, mouseX, mouseY, partialTick);

        if (failTicks > 0) {
            renderFailure(graphics);
        }
    }

    private void renderFailure(GuiGraphics graphics) {
        // Deterministic per frame: reseeded from the failure seed and current tick.
        RandomSource random = RandomSource.create(failSeed ^ (failTicks * 0x9E3779B97F4A7C15L));
        int splats = 8 + random.nextInt(5);
        int fieldX = nameField.getX();
        int fieldY = nameField.getY();
        int fieldW = nameField.getWidth();
        int fieldH = nameField.getHeight();
        for (int i = 0; i < splats; i++) {
            int w = 2 + random.nextInt(5);
            int h = 2 + random.nextInt(5);
            int x = fieldX - 12 + random.nextInt(fieldW + 24);
            int y = fieldY - 10 + random.nextInt(fieldH + 20);
            int color = random.nextBoolean() ? SPLAT_DARK : SPLAT_LIGHT;
            graphics.fill(x, y, x + w, y + h, color);
        }
        ConfigWidgets.drawCenteredNoShadow(graphics, font, "That is not your name.",
                fieldX + fieldW / 2, fieldY + fieldH + 6, ERROR_TEXT);
    }

    @Override
    public void renderBackground(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, DIM);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
