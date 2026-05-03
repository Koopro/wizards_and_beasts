package at.koopro.wizardsandbeasts.client.hud;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.state.ClientFormDataState;
import at.koopro.wizardsandbeasts.form.FormRegistry;
import at.koopro.wizardsandbeasts.form.PlayerForm;
import at.koopro.wizardsandbeasts.form.SizeProfile;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/**
 * Debug HUD overlay showing the local player's form, size, and hitbox info.
 * Toggled via {@code /WizardsAndBeastsMod debug wizmorph debug <player> on|off}.
 */
public final class FormDebugOverlay {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "form_debug");

    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int LABEL_COLOR = 0xFF88FF88;
    private static final int BG_COLOR = 0x88000000;

    private FormDebugOverlay() {}

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!ClientFormDataState.isDebugOverlay()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ClientFormDataState.FormData data = ClientFormDataState.get(mc.player.getUUID());

        int x = 4;
        int y = 4;
        int lineHeight = 11;

        // Background panel
        graphics.fill(x - 2, y - 2, x + 160, y + lineHeight * 8 + 4, BG_COLOR);

        graphics.drawString(mc.font, "-- Form Debug --", x, y, LABEL_COLOR, false);
        y += lineHeight;

        if (data == null) {
            graphics.drawString(mc.font, "Form: (none)", x, y, TEXT_COLOR, false);
            return;
        }

        // Form info
        graphics.drawString(mc.font, "Form: " + data.formId(), x, y, TEXT_COLOR, false);
        y += lineHeight;

        PlayerForm form = FormRegistry.get(data.formId());
        if (form != null) {
            graphics.drawString(mc.font, "Model: " + form.modelType().getDisplayName(),
                    x, y, TEXT_COLOR, false);
            y += lineHeight;
        }

        // Size profile
        SizeProfile size = data.sizeProfile();
        graphics.drawString(mc.font,
                String.format("Scale: %.2f x %.2f x %.2f", size.scaleX(), size.scaleY(), size.scaleZ()),
                x, y, TEXT_COLOR, false);
        y += lineHeight;

        // Hitbox dimensions (approximate based on scale * default player)
        float hitboxW = 0.6f * size.scaleX();
        float hitboxH = 1.8f * size.scaleY();
        graphics.drawString(mc.font,
                String.format("Hitbox: %.2f x %.2f", hitboxW, hitboxH),
                x, y, TEXT_COLOR, false);
        y += lineHeight;

        // Eye height
        float eyeHeight = 1.62f * size.scaleY();
        graphics.drawString(mc.font,
                String.format("Eye Height: %.2f", eyeHeight),
                x, y, TEXT_COLOR, false);
        y += lineHeight;

        // Reach/KB/Step
        graphics.drawString(mc.font,
                String.format("Reach: +%.1f  KB Res: %.1f", size.reachBonus(), size.knockbackResistance()),
                x, y, TEXT_COLOR, false);
        y += lineHeight;

        graphics.drawString(mc.font,
                String.format("Step: +%.1f  Flags: %s", size.stepHeight(), data.renderFlags()),
                x, y, TEXT_COLOR, false);
    }
}
