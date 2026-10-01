package at.koopro.wizardsandbeasts.client.admin.widget;

import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import net.minecraft.client.gui.Font;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * Setting → control. The single place that decides which widget draws a setting, from its descriptor alone,
 * so no panel ever hand-builds a control for a particular setting.
 *
 * <ul>
 *   <li>boolean → {@link AdminToggle}</li>
 *   <li>integer with a span of at most {@value #MAX_SLIDER_SPAN} → {@link AdminSlider}; wider → {@link AdminTextField}</li>
 *   <li>decimal with a finite range of at most {@value #MAX_DECIMAL_POSITIONS} steps → {@link AdminSlider}; wider or unbounded → {@link AdminTextField}</li>
 *   <li>enum → {@link AdminEnumSelector}</li>
 *   <li>string, and any kind this client does not know → {@link AdminTextField}</li>
 * </ul>
 */
@NullMarked
public final class AdminControlFactory {

    /** Past this many positions a slider stops being a precise instrument; a 0–10000 slider moves in 80s. */
    static final int MAX_SLIDER_SPAN = 200;

    /** A decimal slider past this many steps is as coarse as a wide integer one (spell damage runs 0 to 1000). */
    static final int MAX_DECIMAL_POSITIONS = 1000;

    private AdminControlFactory() {}

    private static double positions(AdminSettingDescriptor setting) {
        double step = setting.step() > 0 && Double.isFinite(setting.step()) ? setting.step() : (setting.max() - setting.min()) / 100.0;
        return (setting.max() - setting.min()) / step;
    }

    public static AdminControl create(AdminSettingDescriptor setting, Font font, int x, int y, int w, int h,
                                      String shown, Consumer<String> onEdit) {
        AdminControl control = switch (setting.kind()) {
            case BOOLEAN -> new AdminToggle(x, y, w, h, Boolean.parseBoolean(shown), onEdit);
            case INTEGER -> finiteSpan(setting) && setting.max() - setting.min() <= MAX_SLIDER_SPAN
                    ? new AdminSlider(x, y, w, h, setting.min(), setting.max(), 1.0, true, shown, onEdit)
                    : new AdminTextField(font, x, y, w, h, setting.kind(), 11, shown, onEdit);
            case DOUBLE -> finiteSpan(setting) && positions(setting) <= MAX_DECIMAL_POSITIONS
                    ? new AdminSlider(x, y, w, h, setting.min(), setting.max(), setting.step(), false, shown, onEdit)
                    : new AdminTextField(font, x, y, w, h, setting.kind(), 24, shown, onEdit);
            case ENUM -> new AdminEnumSelector(x, y, w, h, setting.options(), shown, onEdit);
            case STRING -> new AdminTextField(font, x, y, w, h, setting.kind(), setting.maxLength(), shown, onEdit);
        };
        control.setEnabled(setting.editable());
        return control;
    }

    private static boolean finiteSpan(AdminSettingDescriptor setting) {
        double span = setting.max() - setting.min();
        return Double.isFinite(span) && span > 0.0 && Math.abs(setting.max()) < 1.0e9 && Math.abs(setting.min()) < 1.0e9;
    }
}
