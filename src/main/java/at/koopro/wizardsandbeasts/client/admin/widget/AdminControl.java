package at.koopro.wizardsandbeasts.client.admin.widget;

import net.minecraft.client.gui.components.AbstractWidget;
import org.jspecify.annotations.NullMarked;

/**
 * A widget that edits one setting's value <em>as text</em>, the same text the server parses.
 *
 * <p>Controls never validate and never send. They show a value, and report an edit through the listener
 * they were built with; the panel keeps the edit as a draft until Apply, and the server decides. A control
 * may constrain input to what its kind can express (a slider cannot leave its range) as a courtesy, but that
 * is presentation, not validation.
 */
@NullMarked
public interface AdminControl {

    AbstractWidget widget();

    /** Shows {@code value} without reporting it as an edit. */
    void display(String value);

    default void setEnabled(boolean enabled) {
        widget().active = enabled;
    }
}
