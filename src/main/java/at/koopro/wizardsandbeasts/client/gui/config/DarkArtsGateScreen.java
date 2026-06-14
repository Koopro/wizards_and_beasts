package at.koopro.wizardsandbeasts.client.gui.config;

import java.util.function.Supplier;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.NonNull;

/**
 * Blood Quill gate modal shown before entering the Dark Arts config category.
 * The player must type their own username; a wrong name triggers a 40-tick red
 * splatter failure animation and clears the field.
 */
public class DarkArtsGateScreen extends Screen {
    private static final int FAIL_TICKS = 40;
    private static final int MODAL_WIDTH = 248;
    private static final int MODAL_HEIGHT = 132;
    private static final int MODAL_FILL = 0xFF1A0A0A;
    private static final int MODAL_BORDER = 0xFF8B0000;
    private static final int DIM = 0xAA000000;
    private static final int BODY_TEXT = 0xFFC8A0A0;
    private static final int ERROR_TEXT = 0xFFFF5555;

    private final Screen parent;
    private final Supplier<Screen> target;
    private EditBox nameField;
    private Button confirmButton;
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
        int boxX = width / 2 - MODAL_WIDTH / 2;
        int boxY = height / 2 - MODAL_HEIGHT / 2;

        nameField = new EditBox(font, boxX + 24, boxY + 70, MODAL_WIDTH - 48, 18,
                Component.literal("Username"));
        nameField.setHint(Component.literal("Enter your Minecraft username"));
        addRenderableWidget(nameField);

        confirmButton = Button.builder(Component.literal("Sign with Blood Quill"), b -> onConfirm())
                .bounds(boxX + MODAL_WIDTH / 2 - 70, boxY + MODAL_HEIGHT - 28, 140, 20)
                .build();
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
        int boxX = width / 2 - MODAL_WIDTH / 2;
        int boxY = height / 2 - MODAL_HEIGHT / 2;

        graphics.fill(boxX - 2, boxY - 2, boxX + MODAL_WIDTH + 2, boxY + MODAL_HEIGHT + 2, MODAL_BORDER);
        graphics.fill(boxX, boxY, boxX + MODAL_WIDTH, boxY + MODAL_HEIGHT, MODAL_FILL);

        ConfigWidgets.drawCenteredNoShadow(graphics, font, "UNFORGIVABLE ARTS — RESTRICTED ACCESS",
                width / 2, boxY + 12, MODAL_BORDER);
        ConfigWidgets.drawCenteredNoShadow(graphics, font, "\"By entering your name you acknowledge full magical responsibility.\"",
                width / 2, boxY + 34, BODY_TEXT);
        ConfigWidgets.drawCenteredNoShadow(graphics, font, "\"The Ministry has been notified.\"",
                width / 2, boxY + 48, BODY_TEXT);

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
            int color = random.nextBoolean() ? 0xCC8B0000 : 0xCCB01010;
            graphics.fill(x, y, x + w, y + h, color);
        }
        ConfigWidgets.drawCenteredNoShadow(graphics, font, "That is not your name.",
                width / 2, fieldY + fieldH + 6, ERROR_TEXT);
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
