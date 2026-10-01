package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminProfileState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads.Op;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Profiles → Import: the profile files in the world's profile folder. Importing reads, checks and validates a
 * file on the server and keeps it as a custom profile — never applies it; a file that cannot be read, is from an
 * incompatible schema or names invalid values is refused with every reason. The first entry explains the folder.
 */
@NullMarked
final class ProfileImportPanel extends AdminBrowserPanel<String> {

    private static final String KEY = ProfileText.KEY;
    private static final String ABOUT = "@about";
    private static final int BUTTON_H = 16;

    static @Nullable String selected = ABOUT;
    private int builtVersion = -1;

    @Override
    public AdminCategory section() {
        return AdminCategory.PROFILES;
    }

    @Override
    protected List<String> entries() {
        List<String> out = new ArrayList<>();
        if (ClientAdminProfileState.loaded()) {
            out.add(ABOUT);
            out.addAll(ClientAdminProfileState.files());
        }
        return out;
    }

    @Override
    protected String idOf(String entry) {
        return entry;
    }

    @Override
    protected Component nameOf(String entry) {
        return entry.equals(ABOUT) ? Component.translatable(KEY + "import.about") : Component.literal(entry);
    }

    @Override
    protected boolean enabledOf(String entry) {
        return true;
    }

    @Override
    protected String badgesOf(String entry) {
        return entry.equals(ABOUT) ? "?" : "";
    }

    @Override
    protected @Nullable String selected() {
        return selected;
    }

    @Override
    protected void setSelected(String id) {
        selected = id;
    }

    @Override
    protected boolean stale() {
        return ClientAdminProfileState.stale();
    }

    @Override
    protected void request() {
        AdminClientRequests.profileList();
    }

    @Override
    protected Component pickPrompt() {
        return Component.translatable(KEY + "pick_file");
    }

    @Override
    protected String subtitleOf(String entry) {
        return ClientAdminProfileState.folder();
    }

    @Override
    public void onServerState() {
        super.onServerState();
        if (host != null && builtVersion != ClientAdminProfileState.version()) {
            host.requestRebuild();
        }
    }

    @Override
    protected int buildPage(AdminPanelHost host, Font font, String entry, int doc, int rowW) {
        builtVersion = ClientAdminProfileState.version();
        if (entry.equals(ABOUT)) {
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "import.folder", ClientAdminProfileState.folder()),
                    AdminTheme.INK);
            doc = wrap(font, doc + 4, rowW, Component.translatable(KEY + "import.how"), AdminTheme.INK_2);
            doc = wrap(font, doc + 4, rowW, Component.translatable(KEY + "import.checks"), AdminTheme.INK_3);
            if (ClientAdminProfileState.files().isEmpty()) {
                doc = wrap(font, doc + 4, rowW, Component.translatable(KEY + "import.none"), AdminTheme.GOLD_DARK);
            }
        } else {
            AdminButton button = new AdminButton(0, 0, Math.min(rowW, 160), BUTTON_H,
                    Component.translatable(KEY + "button.import"), AdminButton.Tone.PRIMARY,
                    () -> AdminClientRequests.profileAction(Op.IMPORT, entry, ""));
            place(button, 0, doc);
            doc += BUTTON_H + AdminTheme.GAP;
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "import.never_applies"), AdminTheme.INK_3);
        }
        AdminProfilePayloads.ActionReply last = ClientAdminProfileState.lastAction();
        if (last != null && (last.op() == Op.IMPORT || last.op() == Op.EXPORT)) {
            doc = header(doc + 4, Component.translatable(KEY + "import.last"));
            doc = wrap(font, doc, rowW, ProfileText.outcome(last), last.ok() ? AdminTheme.GOOD : AdminTheme.BAD);
            for (AdminProfilePayloads.IssueRow issue : last.issues()) {
                doc = wrap(font, doc, rowW, Component.literal(last.ok() ? "⚠ " : "✖ ").append(ProfileText.issue(issue)),
                        last.ok() ? AdminTheme.GOLD_DARK : AdminTheme.BAD);
            }
        }
        return doc;
    }

    @Override
    protected boolean loaded() {
        return ClientAdminProfileState.loaded();
    }

    @Override
    protected Component emptyMessage() {
        return Component.translatable("admin.wizards_and_beasts.empty.imports");
    }
}
