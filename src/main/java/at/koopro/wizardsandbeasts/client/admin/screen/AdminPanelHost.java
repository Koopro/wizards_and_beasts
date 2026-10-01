package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import org.jspecify.annotations.NullMarked;

/** What the Control Center screen offers the panel inside it. */
@NullMarked
interface AdminPanelHost {

    <T extends AbstractWidget> T addPanelWidget(T widget);

    Font font();

    AdminEditSession edits();

    void navigate(AdminCategory section);

    /** Opens Magic → Spells on one spell's page. */
    void showSpell(String spellId);

    /** Shows a confirmation dialog over the panel. */
    void openDialog(at.koopro.wizardsandbeasts.client.admin.widget.AdminConfirmDialog dialog);

    void closeDialog();

    /**
     * Hides the Control Center for {@code ticks} so the administrator can watch the world — a preview or a test
     * cast happens behind the panel otherwise. Any click or key ends it early.
     */
    void peek(int ticks);

    /** Recreate every widget — for a panel whose set of rows changed. */
    void requestRebuild();
}
