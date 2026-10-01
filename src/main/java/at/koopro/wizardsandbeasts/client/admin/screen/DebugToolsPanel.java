package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.debug.DebugLeases;
import at.koopro.wizardsandbeasts.admin.debug.DebugLeases.Tool;
import at.koopro.wizardsandbeasts.admin.debug.LiveDiagnostics;
import at.koopro.wizardsandbeasts.admin.debug.ModLogLevel;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminOpsState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminEnumSelector;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.network.admin.AdminOpsPayloads;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Debug → Tools: the switches. Your own debug mode (the in-world inspector beside whatever you look at, and your spell
 * events in the log), debug mode for everyone, spell logging for everyone, this mod's log level, and the hitbox overlay
 * on this client. Everything switched on here is leased: it goes off again when you close the Control Center, log out,
 * the server stops, or the panel stops renewing it. Each button shows the state the server reported.
 */
@NullMarked
final class DebugToolsPanel extends OpsDocPanel {

    static final String KEY = "admin.wizards_and_beasts.debug_tools.";
    private static final int BUTTON_H = 16;

    @Override
    public AdminCategory section() {
        return AdminCategory.DEBUG;
    }

    @Override
    protected Component title() {
        return Component.translatable(KEY + "title");
    }

    @Override
    protected Component summary() {
        return Component.translatable(KEY + "summary");
    }

    @Override
    protected int pollTicks() {
        return 40;
    }

    @Override
    protected void poll() {
        ClientAdminOpsState.requestDiagnostics();
    }

    @Override
    protected boolean rebuildsControls() {
        return true;
    }

    @Override
    protected int controls(AdminPanelHost host, Font font, int left, int top, int width) {
        DebugLeases.State state = ClientAdminOpsState.debugState();
        if (state == null) {
            return 0;
        }
        int half = (width - AdminTheme.GAP) / 2;
        int row = top;
        row = toggle(host, left, row, half, Tool.MY_DEBUG_MODE, state.myDebugMode());
        row = toggle(host, left, row, half, Tool.ALL_DEBUG_MODE, state.allDebugMode());
        row = toggle(host, left, row, half, Tool.SPELL_LOGGING, state.spellLogging());
        List<String> levels = Arrays.stream(ModLogLevel.Choice.values()).map(ModLogLevel.Choice::id).toList();
        host.addPanelWidget(new AdminEnumSelector(left + half + AdminTheme.GAP, row, half, BUTTON_H, levels,
                state.logLevel().id(), value -> ClientAdminOpsState.setTool(Tool.LOG_LEVEL, value),
                value -> Component.translatable(KEY + "level." + value)));
        row += BUTTON_H + AdminTheme.GAP;
        boolean hitboxes = ClientAdminOpsState.hitboxes();
        host.addPanelWidget(new AdminButton(left + half + AdminTheme.GAP, row, half, BUTTON_H,
                Component.translatable(KEY + (hitboxes ? "turn_off" : "turn_on")),
                hitboxes ? AdminButton.Tone.PRIMARY : AdminButton.Tone.NEUTRAL, () -> {
            ClientAdminOpsState.setHitboxes(!hitboxes);
            host.requestRebuild();
        }));
        row += BUTTON_H + AdminTheme.GAP;
        host.addPanelWidget(new AdminButton(left + half + AdminTheme.GAP, row, half, BUTTON_H,
                Component.translatable(KEY + "release_all"), AdminButton.Tone.DANGER, () -> {
            ClientAdminOpsState.releaseNow();
            host.requestRebuild();
        }));
        row += BUTTON_H;
        return row - top;
    }

    private int toggle(AdminPanelHost host, int left, int row, int half, Tool tool, boolean on) {
        host.addPanelWidget(new AdminButton(left + half + AdminTheme.GAP, row, half, BUTTON_H,
                Component.translatable(KEY + (on ? "turn_off" : "turn_on")),
                on ? AdminButton.Tone.PRIMARY : AdminButton.Tone.NEUTRAL,
                () -> ClientAdminOpsState.setTool(tool, on ? "false" : "true")));
        return row + BUTTON_H + AdminTheme.GAP;
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        DebugLeases.State state = ClientAdminOpsState.debugState();
        if (host == null || state == null) {
            return;
        }
        // Labels beside the controls, drawn (not widgets): what each switch is and whether it is on now.
        Font font = host.font();
        int top = y + 32;
        int half = (w - 8 - AdminTheme.GAP) / 2;
        String[] keys = {"my_debug", "all_debug", "spell_logging", "log_level", "hitboxes", "release"};
        boolean[] on = {state.myDebugMode(), state.allDebugMode(), state.spellLogging(),
                state.logLevel() != ModLogLevel.Choice.INFO, ClientAdminOpsState.hitboxes(), false};
        for (int i = 0; i < keys.length; i++) {
            int ly = top + i * (BUTTON_H + AdminTheme.GAP) + 4;
            Component label = Component.translatable(KEY + keys[i]);
            boolean leased = i < 3 && state.held().contains(Tool.values()[i])
                    || i == 3 && state.held().contains(Tool.LOG_LEVEL) || i == 4 && ClientAdminOpsState.hitboxesChanged();
            if (leased) {
                label = label.copy().append(" ⏲");
            }
            g.drawString(font, at.koopro.wizardsandbeasts.client.admin.widget.AdminText.clip(font, label.getString(), half),
                    x, ly, on[i] ? AdminTheme.RUBRIC : AdminTheme.INK, false);
        }
    }

    @Override
    protected List<Line> lines() {
        List<Line> out = new ArrayList<>();
        DebugLeases.State state = ClientAdminOpsState.debugState();
        if (state == null) {
            out.add(note("admin.wizards_and_beasts.status.loading"));
            return out;
        }
        AdminOpsPayloads.ToolReply last = ClientAdminOpsState.lastTool();
        if (last != null && !last.success()) {
            out.add(Line.text(Component.translatable(KEY + "refused." + last.code()), AdminTheme.BAD));
        }
        out.add(note(KEY + "lease_note", (int) (DebugLeases.TTL_MILLIS / 60_000)));
        out.add(note(KEY + "notes"));
        LiveDiagnostics.Snapshot live = ClientAdminOpsState.diagnostics();
        if (live != null && !live.holdings().isEmpty()) {
            out.add(heading(KEY + "holders"));
            for (LiveDiagnostics.Holding holding : live.holdings()) {
                out.add(plain("• " + holding.admin() + ": " + holding.tools() + " (" + holding.idleSeconds() + "s)",
                        AdminTheme.INK_2));
            }
        }
        return out;
    }
}
