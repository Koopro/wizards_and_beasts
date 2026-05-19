package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/**
 * HUD overlay for the Model Debug Editor.
 * Shows the part list, selected highlight, current values, mode/axis indicators,
 * and keybind legend. Registered via {@link net.neoforged.neoforge.client.event.RegisterGuiLayersEvent}.
 */
public final class DebugHudRenderer {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "model_debug_editor");

    private static final int BG_COLOR = 0xAA000000;
    private static final int WHITE   = 0xFFFFFFFF;

    private DebugHudRenderer() {}

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        ModelDebugEditor editor = ModelDebugEditor.get();
        if (!editor.isActive()) return;

        var font = Minecraft.getInstance().font;
        int x = 10;
        int y = 10;

        String[] parts = editor.getCurrentPartNames();
        int panelHeight = 14 + 12 + (parts.length * 11) + 6 + 12 + 12 + 4;
        int panelWidth = 460;

        // Background
        graphics.fill(x - 4, y - 4, x + panelWidth, y + panelHeight, BG_COLOR);

        // Header
        String header = "\u00a76\u25c6 MODEL DEBUG \u00a77\u2014 \u00a7f" + editor.getCreatureType()
                + " \u00a77[" + editor.getModelMode() + "]";
        graphics.drawString(font, header, x, y, WHITE, false);
        y += 14;

        graphics.drawString(font, "\u00a78\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500", x, y, WHITE, false);
        y += 12;

        // Part list
        for (int i = 0; i < parts.length; i++) {
            boolean sel = (i == editor.getSelectedIndex());
            String prefix = sel ? "\u00a7e\u25b8 " : "\u00a77  ";
            graphics.drawString(font, prefix + parts[i], x, y, WHITE, false);

            if (sel) {
                DebugTransformData d = editor.getData(parts[i]);
                String values = String.format(
                        "\u00a7aS\u00a77[\u00a7f%.2f %.2f %.2f\u00a77] "
                        + "\u00a79O\u00a77[\u00a7f%.2f %.2f %.2f\u00a77] "
                        + "\u00a7cR\u00a77[\u00a7f%.2f %.2f %.2f\u00a77]",
                        d.scaleX, d.scaleY, d.scaleZ,
                        d.offX, d.offY, d.offZ,
                        d.rotX, d.rotY, d.rotZ
                );
                graphics.drawString(font, values, x + 110, y, WHITE, false);
            }
            y += 11;
        }

        // Footer: mode, axis, step
        y += 6;
        graphics.drawString(font,
                "\u00a76Mode: \u00a7f" + editor.getMode()
                + "  \u00a76Axis: \u00a7f" + editor.getAxis()
                + "  \u00a76Step: \u00a77Shift=0.01 Default=0.05 Ctrl=0.25",
                x, y, WHITE, false);
        y += 12;
        graphics.drawString(font,
                "\u00a78F6 toggle \u00b7 \u2191\u2193 part \u00b7 TAB mode \u00b7 XYZ axis \u00b7 Scroll adjust \u00b7 R reset \u00b7 F7 export \u00b7 F8 vanilla/gecko",
                x, y, WHITE, false);
    }
}
