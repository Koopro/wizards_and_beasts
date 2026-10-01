package at.koopro.wizardsandbeasts.client.admin.widget;

import at.koopro.wizardsandbeasts.admin.config.SettingKind;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedTextField;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Free text entry for a setting: large numeric ranges (a fee in knuts has no useful slider) and strings.
 *
 * <p>Built on {@link ThemedTextField}, so it wears the page's inset well. For numeric kinds the field only
 * accepts characters a number can contain — a keystroke filter, not validation: "1-2" still passes the
 * filter, and the server is what says no.
 */
@NullMarked
public final class AdminTextField extends ThemedTextField implements AdminControl {

    private static final Pattern NUMERIC_CHARS = Pattern.compile("[-+0-9.eE]*");
    private static final int DEFAULT_MAX = 64;

    private boolean silent;

    public AdminTextField(Font font, int x, int y, int w, int h, SettingKind kind, int maxLength,
                          String initial, Consumer<String> onEdit) {
        super(font, x, y, w, h, Component.empty());
        setMaxLength(maxLength > 0 ? maxLength : DEFAULT_MAX);
        if (kind == SettingKind.INTEGER || kind == SettingKind.DOUBLE) {
            setFilter(text -> NUMERIC_CHARS.matcher(text).matches());
        }
        display(initial);
        setResponder(text -> {
            if (!silent) {
                onEdit.accept(text);
            }
        });
    }

    /** The one search box every list uses: same size, same "Search…" hint, filtering as the administrator types. */
    public static AdminTextField search(Font font, int x, int y, int w, String initial, Consumer<String> onEdit) {
        AdminTextField field = new AdminTextField(font, x, y, w, 14, SettingKind.STRING, 32, initial, onEdit);
        field.inkHint(Component.translatable("admin.wizards_and_beasts.search.hint"));
        return field;
    }

    @Override
    public AbstractWidget widget() {
        return this;
    }

    @Override
    public void display(String value) {
        if (value.equals(getValue())) {
            return;
        }
        silent = true;
        setValue(value);
        moveCursorToStart(false);
        silent = false;
    }

    @Override
    public void setEnabled(boolean enabled) {
        active = enabled;
        setEditable(enabled);
    }
}
