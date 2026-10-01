package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.AdminLangKeys;
import at.koopro.wizardsandbeasts.admin.AdminResult;
import at.koopro.wizardsandbeasts.admin.config.ApplyMode;
import at.koopro.wizardsandbeasts.admin.module.ModuleAdminInfo;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleDefaults;
import at.koopro.wizardsandbeasts.module.ModuleDependencies;
import at.koopro.wizardsandbeasts.module.ModuleIds;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.ModuleState;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Modules → Modules: every module the build registers (read from the {@link Module} enum, never a list kept here),
 * its live state, and on the right what it is, what it leans on, what leans on it, where its gate is read and when
 * a change takes effect.
 *
 * <p>State is read from {@link ModuleManager} — the client's copy of the server's {@code ModuleStateSyncPayload},
 * the module system's own sync — so the page is never staler than the last sync and needs no request of its own.
 *
 * <p>Enable / Preview / Disable send the module's ordinary admin setting ({@code module_<id>}) through
 * {@code AdminSettingService}: the server refuses an enable whose required module is off (and says which), and asks
 * for confirmation before a disable that takes dependants with it. The buttons are a courtesy; the server judges.
 */
@NullMarked
final class ModuleBrowserPanel extends AdminBrowserPanel<Module> {

    private static final String KEY = "admin.wizards_and_beasts.module_page.";
    private static final int BUTTON_H = 16;
    private static final long FEEDBACK_MS = 10_000L;

    static @Nullable String selected;

    /** The synced states the page was built from; a different snapshot rebuilds it. */
    private Map<Module, ModuleState> builtFrom = new EnumMap<>(Module.class);
    private int feedbackDoc = -1;

    @Override
    public AdminCategory section() {
        return AdminCategory.MODULES;
    }

    @Override
    protected List<Module> entries() {
        return List.of(Module.values());
    }

    @Override
    protected String idOf(Module entry) {
        return ModuleIds.of(entry).getPath();
    }

    @Override
    protected Component nameOf(Module entry) {
        return ModuleIds.displayName(entry);
    }

    @Override
    protected boolean enabledOf(Module entry) {
        return ModuleManager.isEnabled(entry);
    }

    @Override
    protected int swatchOf(Module entry) {
        return switch (ModuleManager.state(entry)) {
            case ENABLED -> AdminTheme.GOOD;
            case PREVIEW -> AdminTheme.GOLD;
            case DISABLED -> AdminTheme.BAD;
            case COMING_SOON -> AdminTheme.INK_3;
        } & 0xFFFFFF;
    }

    @Override
    protected boolean hasSwatches() {
        return true;
    }

    /** "!" while something it requires is off; the apply-mode mark for worldgen modules. */
    @Override
    protected String badgesOf(Module entry) {
        boolean starved = ModuleDependencies.dependenciesOf(entry).stream()
                .anyMatch(e -> e.kind() == ModuleDependencies.Kind.REQUIRES && !ModuleManager.isEnabled(e.dependency()));
        return (starved ? "!" : "") + ModuleAdminInfo.applyMode(entry).badge().trim();
    }

    @Override
    protected @Nullable String selected() {
        return selected;
    }

    @Override
    protected void setSelected(String id) {
        selected = id;
    }

    /** Module state arrives with the module system's own sync; nothing to request. */
    @Override
    protected boolean stale() {
        return false;
    }

    @Override
    protected void request() {}

    @Override
    protected Component pickPrompt() {
        return Component.translatable(KEY + "pick");
    }

    @Override
    protected String subtitleOf(Module entry) {
        ModuleState state = ModuleManager.state(entry);
        return state.displayName().getString() + " · " + Component.translatable(KEY + "default",
                ModuleDefaults.shipped(entry).displayName()).getString();
    }

    @Override
    protected List<Component> tooltipOf(Module entry) {
        return List.of(nameOf(entry), ModuleManager.state(entry).displayName(),
                Component.translatable(descriptionKey(entry)));
    }

    private static String descriptionKey(Module module) {
        return AdminLangKeys.settingDescription(ModuleAdminInfo.settingId(module).getPath());
    }

    private static Component nameAndState(Module module) {
        return Component.empty().append(ModuleIds.displayName(module)).append(" (")
                .append(ModuleManager.state(module).displayName()).append(")");
    }

    // ── page ──

    @Override
    protected int buildPage(AdminPanelHost host, Font font, Module module, int doc, int rowW) {
        builtFrom = ModuleManager.snapshot();
        ModuleState state = ModuleManager.state(module);
        Identifier settingId = ModuleAdminInfo.settingId(module);
        AdminSettingDescriptor setting = ClientAdminState.get(settingId);
        boolean locked = state == ModuleState.COMING_SOON;
        boolean editable = setting != null && setting.editable() && !locked;
        ModuleDependencies.Check enable = ModuleDependencies.check(ModuleManager.snapshot(), module, ModuleState.ENABLED);
        ModuleDependencies.Check disable = ModuleDependencies.check(ModuleManager.snapshot(), module, ModuleState.DISABLED);

        doc = header(doc, Component.translatable(KEY + "status"));
        int bw = Math.max(50, (rowW - 2 * AdminTheme.GAP) / 3);
        stateButton(host, settingId, ModuleState.ENABLED, KEY + "enable", 0, doc, bw,
                editable && state != ModuleState.ENABLED && enable.allowed(), AdminButton.Tone.PRIMARY);
        stateButton(host, settingId, ModuleState.PREVIEW, KEY + "preview", bw + AdminTheme.GAP, doc, bw,
                editable && state != ModuleState.PREVIEW && enable.allowed(), AdminButton.Tone.NEUTRAL);
        stateButton(host, settingId, ModuleState.DISABLED, KEY + "disable", 2 * (bw + AdminTheme.GAP), doc, bw,
                editable && state != ModuleState.DISABLED, AdminButton.Tone.DANGER);
        doc += BUTTON_H + AdminTheme.GAP;
        feedbackDoc = doc;
        doc += LINE;
        if (locked) {
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "coming_soon"), AdminTheme.INK_3);
        } else if (setting == null || !setting.editable()) {
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "read_only"), AdminTheme.INK_3);
        }
        // Said before anyone presses anything: why Enable is greyed, and what Disable would take with it.
        for (ModuleDependencies.Edge edge : enable.blockedBy()) {
            if (!state.grantsAccess()) {
                doc = wrap(font, doc, rowW, Component.translatable(edge.blockedKey()), AdminTheme.BAD);
            }
        }
        if (state.grantsAccess()) {
            for (Module dependant : disable.cascade()) {
                for (ModuleDependencies.Edge edge : ModuleDependencies.all()) {
                    if (edge.dependent() == dependant && edge.kind() == ModuleDependencies.Kind.REQUIRES
                            && (edge.dependency() == module || disable.cascade().contains(edge.dependency()))) {
                        doc = wrap(font, doc, rowW, Component.translatable(edge.effectKey()), AdminTheme.WAX);
                        break;
                    }
                }
            }
            for (ModuleDependencies.Edge edge : disable.weakened()) {
                doc = wrap(font, doc, rowW, Component.translatable(edge.effectKey()), AdminTheme.INK_2);
            }
        }

        doc = header(doc + 4, Component.translatable(KEY + "description"));
        doc = wrap(font, doc, rowW, Component.translatable(descriptionKey(module)), AdminTheme.INK);

        doc = header(doc + 4, Component.translatable(KEY + "dependencies"));
        List<ModuleDependencies.Edge> deps = ModuleDependencies.dependenciesOf(module);
        if (deps.isEmpty()) {
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "none"), AdminTheme.INK_3);
        }
        for (ModuleDependencies.Edge edge : deps) {
            boolean on = ModuleManager.isEnabled(edge.dependency());
            doc = wrap(font, doc, rowW, Component.literal(on ? "✔ " : "✖ ")
                    .append(Component.translatable(KEY + "kind." + edge.kind().name().toLowerCase(java.util.Locale.ROOT)))
                    .append(" ").append(nameAndState(edge.dependency())), on ? AdminTheme.GOOD : AdminTheme.BAD);
            doc = wrap(font, doc, rowW, Component.literal("   " + edge.evidence()), AdminTheme.INK_3);
        }

        doc = header(doc + 4, Component.translatable(KEY + "dependants"));
        List<ModuleDependencies.Edge> dependants = ModuleDependencies.dependantsOf(module);
        if (dependants.isEmpty()) {
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "none"), AdminTheme.INK_3);
        }
        for (ModuleDependencies.Edge edge : dependants) {
            doc = wrap(font, doc, rowW, Component.empty().append(nameAndState(edge.dependent())).append(" — ")
                    .append(Component.translatable(KEY + "kind_of." + edge.kind().name().toLowerCase(java.util.Locale.ROOT))),
                    AdminTheme.INK_2);
        }

        doc = header(doc + 4, Component.translatable(KEY + "runs"));
        ModuleAdminInfo.Side side = ModuleAdminInfo.side(module);
        doc = wrap(font, doc, rowW, Component.translatable(KEY + "side", Component.translatable(side.labelKey())),
                AdminTheme.INK_2);
        ApplyMode mode = ModuleAdminInfo.applyMode(module);
        doc = wrap(font, doc, rowW, Component.translatable(KEY + "apply", Component.translatable(mode.labelKey()),
                Component.translatable(mode == ApplyMode.RUNTIME ? KEY + "apply.runtime_note" : mode.explanationKey())),
                mode == ApplyMode.RUNTIME ? AdminTheme.INK_2 : AdminTheme.RUBRIC);
        AdminCategory home = ModuleAdminInfo.home(module);
        if (home != AdminCategory.MODULES) {
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "home", Component.translatable(home.nameKey())),
                    AdminTheme.INK_3);
        }
        doc = wrap(font, doc + 2, rowW, Component.translatable(KEY + "id", ModuleIds.of(module).toString()), AdminTheme.INK_3);
        return doc;
    }

    /** Sends the change at once; the server applies, refuses with the reason, or asks for confirmation. */
    private void stateButton(AdminPanelHost host, Identifier settingId, ModuleState target, String labelKey,
                             int x, int doc, int w, boolean active, AdminButton.Tone tone) {
        AdminButton button = new AdminButton(0, 0, w, BUTTON_H, Component.translatable(labelKey), tone,
                () -> AdminClientRequests.change(settingId, target.name(), false));
        button.active = active;
        place(button, x, doc);
    }

    @Override
    protected void renderPage(net.minecraft.client.gui.GuiGraphics g, Font font, Module module, int top, int width,
                              int mouseX, int mouseY) {
        if (feedbackDoc < 0) {
            return;
        }
        int fy = top + feedbackDoc;
        if (fy < y || fy + LINE > y + h) {
            return;
        }
        Identifier id = ModuleAdminInfo.settingId(module);
        Component line = null;
        int color = AdminTheme.INK_2;
        if (ClientAdminState.isPending(id)) {
            line = Component.translatable("admin.wizards_and_beasts.status.saving");
        } else {
            ClientAdminState.Feedback feedback = ClientAdminState.feedback(id);
            if (feedback != null && Util.getMillis() - feedback.atMillis() < FEEDBACK_MS) {
                AdminResult result = feedback.result();
                if (result.rejected()) {
                    MutableComponent text = Component.literal("✖ ");
                    text.append(result.detailKey() != null ? Component.translatable(result.detailKey())
                            : Component.translatable(result.rejection() == null
                            ? "admin.wizards_and_beasts.status.timeout" : result.rejection().translationKey()));
                    line = text;
                    color = AdminTheme.BAD;
                } else if (result.applied()) {
                    line = Component.translatable(KEY + "applied", ModuleManager.state(module).displayName());
                    color = AdminTheme.GOOD;
                }
            }
        }
        if (line != null) {
            g.drawString(font, at.koopro.wizardsandbeasts.client.admin.widget.AdminText.clip(font, line.getString(), width),
                    detailX, fy, color, false);
        }
    }

    /** A module sync (or any admin reply) that changed a state rebuilds the page; everything on it derives from state. */
    @Override
    public void onServerState() {
        if (host != null && !ModuleManager.snapshot().equals(builtFrom)) {
            host.requestRebuild();
            return;
        }
        super.onServerState();
    }

    /** Names of every module that is currently off but required by an open one — for the section summary. */
    static List<Module> starved() {
        List<Module> out = new ArrayList<>();
        for (ModuleDependencies.Edge edge : ModuleDependencies.all()) {
            if (edge.kind() == ModuleDependencies.Kind.REQUIRES && ModuleManager.isEnabled(edge.dependent())
                    && !ModuleManager.isEnabled(edge.dependency())) {
                out.add(edge.dependent());
            }
        }
        return out;
    }
}
