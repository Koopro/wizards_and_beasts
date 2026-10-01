package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminEnumSelector;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminSectionHeader;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.client.beam.BeamChannelClient;
import at.koopro.wizardsandbeasts.client.beam.BeamQuality;
import at.koopro.wizardsandbeasts.client.beam.ClientBeamVisuals;
import at.koopro.wizardsandbeasts.client.spell.gui.BeamStyleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * Visuals → Debug: this client's beam render budget ({@link BeamQuality}, the same switch as
 * {@code /wandb debug beam preset}), the beam debug editor (a global override of every beam on this client only), a
 * readout of what the beam renderer is holding, and a way to the Debug section's server switches. Nothing here is a
 * server setting and nothing here reaches another player.
 */
@NullMarked
final class DebugRenderingPanel implements AdminPanel {

    private static final String KEY = "admin.wizards_and_beasts.visual_debug.";

    private @Nullable AdminPanelHost host;
    private int x;
    private int y;
    private int w;
    private int readoutTop;

    @Override
    public AdminCategory section() {
        return AdminCategory.VISUALS;
    }

    @Override
    public void init(AdminPanelHost host, int x, int y, int w, int h) {
        this.host = host;
        this.x = x;
        this.y = y;
        this.w = w;
        Font font = host.font();
        int row = y + 32;
        int labelW = Math.min(150, w / 2);
        host.addPanelWidget(new AdminEnumSelector(x + labelW, row, Math.min(120, w - labelW), 16,
                Arrays.stream(BeamQuality.Level.values()).map(Enum::name).toList(), BeamQuality.level().name(),
                value -> BeamQuality.set(BeamQuality.Level.valueOf(value)),
                value -> Component.translatable(KEY + "quality." + value.toLowerCase(java.util.Locale.ROOT))));
        row += 20 + font.split(Component.translatable(KEY + "quality_note"), w).size() * 10 + 6;
        host.addPanelWidget(new AdminButton(x, row, Math.min(160, w), 16, Component.translatable(KEY + "open_editor"),
                AdminButton.Tone.NEUTRAL, () -> Minecraft.getInstance().setScreen(new BeamStyleScreen())));
        host.addPanelWidget(new AdminButton(x + Math.min(160, w) + 4, row, Math.min(120, Math.max(40, w - 164)), 16,
                Component.translatable(KEY + "debug_section"), AdminButton.Tone.QUIET,
                () -> host.navigate(AdminCategory.DEBUG)));
        row += 20 + font.split(Component.translatable(KEY + "editor_note"), w).size() * 10 + 6;
        readoutTop = row;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (host == null) {
            return;
        }
        Font font = host.font();
        AdminSectionHeader.render(g, font, x, y, w, Component.translatable(KEY + "title"), Component.translatable(KEY + "summary"));
        int row = y + 32;
        g.drawString(font, Component.translatable(KEY + "quality"), x, row + 4, AdminTheme.INK, false);
        row += 20;
        row = paragraph(g, font, row, Component.translatable(KEY + "quality_note")) + 6;
        row += 20;
        paragraph(g, font, row, Component.translatable(KEY + "editor_note"));

        int ry = readoutTop;
        g.drawString(font, Component.translatable(KEY + "readout"), x, ry, AdminTheme.RUBRIC, false);
        ry += 12;
        ry = line(g, font, ry, Component.translatable(KEY + "live_beams", BeamChannelClient.liveBeamCount()));
        ry = line(g, font, ry, Component.translatable(KEY + "preview",
                Component.translatable(KEY + (BeamChannelClient.previewRunning() ? "preview.running" : "preview.none"))));
        ry = line(g, font, ry, Component.translatable(KEY + "overridden", ClientBeamVisuals.overriddenCount()));
        line(g, font, ry, Component.translatable(KEY + "quality_now",
                Component.translatable(KEY + "quality." + BeamQuality.level().name().toLowerCase(java.util.Locale.ROOT))));
    }

    private int paragraph(GuiGraphics g, Font font, int top, Component text) {
        for (FormattedCharSequence seq : font.split(text, w)) {
            g.drawString(font, seq, x, top, AdminTheme.INK_3, false);
            top += 10;
        }
        return top;
    }

    private int line(GuiGraphics g, Font font, int top, Component text) {
        g.drawString(font, text, x + 4, top, AdminTheme.INK_2, false);
        return top + 10;
    }
}
