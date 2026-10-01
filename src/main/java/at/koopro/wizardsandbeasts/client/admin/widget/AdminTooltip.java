package at.koopro.wizardsandbeasts.client.admin.widget;

import at.koopro.wizardsandbeasts.admin.config.ApplyMode;
import at.koopro.wizardsandbeasts.admin.config.SettingKind;
import at.koopro.wizardsandbeasts.admin.config.SettingScope;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The hover text of a setting, assembled from its descriptor so every row explains itself the same way:
 *
 * <ol>
 *   <li>its name, then what it does and why one would change it (the setting's own description);</li>
 *   <li><b>Affects</b> — every player, or only each player's own game;</li>
 *   <li><b>Takes effect</b> — immediately, after a reload, after a restart, or only in new chunks;</li>
 *   <li>a caution line when changing it asks for confirmation, a read-only line when the viewer may not change it;</li>
 *   <li><b>Default</b> and <b>Range</b>, and the current value when it differs from the default.</li>
 * </ol>
 *
 * Written for a server owner, not a developer — the lang file holds the words. The lines are kept for the last
 * descriptor asked about, so hovering a row does not re-wrap the text every frame.
 */
@NullMarked
public final class AdminTooltip {

    private static final int WIDTH = 240;
    private static final String KEY = "admin.wizards_and_beasts.tooltip.";

    private static @Nullable AdminSettingDescriptor cachedFor;
    private static List<FormattedCharSequence> cached = List.of();

    private AdminTooltip() {}

    public static List<FormattedCharSequence> forSetting(Font font, AdminSettingDescriptor setting) {
        if (setting.equals(cachedFor)) {
            return cached;
        }
        cached = build(font, setting);
        cachedFor = setting;
        return cached;
    }

    private static List<FormattedCharSequence> build(Font font, AdminSettingDescriptor setting) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.translatable(setting.nameKey()).withStyle(ChatFormatting.GOLD).getVisualOrderText());
        lines.addAll(font.split(Component.translatable(setting.descriptionKey()).withStyle(ChatFormatting.GRAY), WIDTH));
        lines.add(FormattedCharSequence.EMPTY);

        lines.addAll(font.split(Component.translatable(KEY + "affects", Component.translatable(
                setting.scope() == SettingScope.CLIENT ? KEY + "affects.client" : KEY + "affects.server"))
                .withStyle(setting.scope() == SettingScope.CLIENT ? ChatFormatting.DARK_AQUA : ChatFormatting.AQUA), WIDTH));

        if (setting.restartRequired() || setting.applyMode() == ApplyMode.RESTART) {
            lines.addAll(font.split(Component.translatable(KEY + "when", Component.translatable(KEY + "when.restart"))
                    .withStyle(ChatFormatting.YELLOW), WIDTH));
        } else if (setting.applyMode() == ApplyMode.RUNTIME) {
            lines.addAll(font.split(Component.translatable(KEY + "when", Component.translatable(KEY + "when.now"))
                    .withStyle(ChatFormatting.GREEN), WIDTH));
        } else {
            lines.addAll(font.split(Component.translatable(KEY + "when", Component.translatable(setting.applyMode().labelKey()))
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), WIDTH));
            lines.addAll(font.split(Component.translatable(setting.applyMode().explanationKey())
                    .withStyle(ChatFormatting.YELLOW), WIDTH));
        }
        if (setting.dangerous()) {
            lines.addAll(font.split(Component.literal("⚠ ").append(Component.translatable(KEY + "dangerous"))
                    .withStyle(ChatFormatting.RED), WIDTH));
        }
        if (!setting.editable() && setting.scope() == SettingScope.SERVER) {
            lines.addAll(font.split(Component.literal("⊘ ").append(Component.translatable(KEY + "locked"))
                    .withStyle(ChatFormatting.DARK_GRAY), WIDTH));
        }

        MutableComponent facts = Component.translatable(KEY + "default", display(setting, setting.defaultValue()));
        if (setting.kind() == SettingKind.INTEGER || setting.kind() == SettingKind.DOUBLE) {
            facts.append(" · ").append(Component.translatable(KEY + "range",
                    number(setting, setting.min()), number(setting, setting.max())));
        }
        lines.addAll(font.split(facts.withStyle(ChatFormatting.DARK_GRAY), WIDTH));
        if (!setting.isDefault()) {
            lines.addAll(font.split(Component.translatable(KEY + "now", display(setting, setting.value()))
                    .withStyle(ChatFormatting.DARK_GRAY), WIDTH));
        }
        return List.copyOf(lines);
    }

    /** A value as the administrator reads it: On/Off, an option's name, "(none)" for empty text. */
    public static Component display(AdminSettingDescriptor setting, String value) {
        return switch (setting.kind()) {
            case BOOLEAN -> Component.translatable(Boolean.parseBoolean(value)
                    ? "admin.wizards_and_beasts.value.on" : "admin.wizards_and_beasts.value.off");
            case ENUM -> AdminEnumSelector.optionLabel(value);
            default -> value.isEmpty() ? Component.translatable("admin.wizards_and_beasts.value.none") : Component.literal(value);
        };
    }

    private static String number(AdminSettingDescriptor setting, double bound) {
        if (setting.kind() == SettingKind.INTEGER) {
            long rounded = Math.round(bound);
            return rounded == Integer.MAX_VALUE ? "∞" : Long.toString(rounded);
        }
        return java.math.BigDecimal.valueOf(bound).stripTrailingZeros().toPlainString();
    }
}
