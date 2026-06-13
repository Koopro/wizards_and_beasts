package at.koopro.wizardsandbeasts.client.gui.config;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Static factory for parchment-and-ink themed config widgets. Every widget writes its
 * value back to the live {@link ModConfigSpec.ConfigValue} holder immediately on change
 * (set + save, no batching), matching NeoForge default config screen behaviour.
 */
public final class ConfigWidgets {

    // Shared parchment/ink palette for the config screens.
    public static final int PARCHMENT = 0xFFF5EDD6;
    public static final int PARCHMENT_DARK = 0xFF2A2318;
    public static final int INK = 0xFF3B2A1A;
    public static final int INK_FADED = 0xFFC8B89A;
    public static final int STAMP_RED = 0xFF8B0000;

    private static final int WIDGET_FILL = 0xFFEFE3C4;
    private static final int WIDGET_FILL_HOVER = 0xFFE6D6AC;
    private static final int TOGGLE_ON_FILL = 0xFF3F6B3A;
    private static final int TOGGLE_ON_TEXT = 0xFFEFFFE9;

    private ConfigWidgets() {}

    /**
     * Creates the themed widget for one config entry, or null if the value type is unsupported.
     * Caller must ensure the config is loaded before calling.
     */
    @SuppressWarnings("unchecked")
    public static @Nullable AbstractWidget create(ModConfigSpec.ConfigValue<?> value, ModConfigSpec.ValueSpec valueSpec,
            int x, int y, int width, int height) {
        Object current = value.get();
        if (current instanceof Boolean) {
            return new ToggleButton(x, y, width, height, (ModConfigSpec.ConfigValue<Boolean>) value);
        }
        if (current instanceof Enum<?> enumValue) {
            return new EnumCycleButton<>(x, y, width, height, (ModConfigSpec.ConfigValue<Enum<?>>) value, enumValue.getDeclaringClass());
        }
        if (current instanceof Integer || current instanceof Double) {
            ModConfigSpec.Range<?> range = valueSpec.getRange();
            if (range != null && isFiniteRange(range)) {
                return new RangedSlider(x, y, width, height, (ModConfigSpec.ConfigValue<Number>) value, range, current instanceof Integer);
            }
            return numericEditBox(x, y, width, height, (ModConfigSpec.ConfigValue<Number>) value, valueSpec, current instanceof Integer);
        }
        if (current instanceof String) {
            return stringEditBox(x, y, width, height, (ModConfigSpec.ConfigValue<String>) value, valueSpec);
        }
        if (current instanceof List<?> list) {
            return listDisplayBox(x, y, width, height, list.size());
        }
        return null;
    }

    /** Humanises a config key or enum constant: splits camelCase and underscores, title-cases words. */
    public static String humanise(String key) {
        String spaced = key.replace('_', ' ')
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(spaced.length());
        boolean wordStart = true;
        for (char c : spaced.toCharArray()) {
            out.append(wordStart && Character.isLetter(c) ? Character.toUpperCase(c) : c);
            wordStart = c == ' ';
        }
        return out.toString();
    }

    private static boolean isFiniteRange(ModConfigSpec.Range<?> range) {
        double min = ((Number) range.getMin()).doubleValue();
        double max = ((Number) range.getMax()).doubleValue();
        // Unbounded defineInRange(..., 0, Integer.MAX_VALUE) style ranges are unusable as sliders.
        return max - min <= 100_000;
    }

    private static <T> void persist(ModConfigSpec.ConfigValue<T> value, T newValue) {
        value.set(newValue);
        value.save();
    }

    private static EditBox numericEditBox(int x, int y, int width, int height,
            ModConfigSpec.ConfigValue<Number> value, ModConfigSpec.ValueSpec valueSpec, boolean integer) {
        EditBox box = new EditBox(Minecraft.getInstance().font, x, y, width, height, Component.literal("value"));
        box.setValue(String.valueOf(value.get()));
        box.setFilter(s -> s.isEmpty() || s.matches(integer ? "-?\\d*" : "-?\\d*\\.?\\d*"));
        box.setResponder(s -> {
            try {
                Number parsed = integer ? Integer.valueOf(s) : Double.valueOf(s);
                if (valueSpec.test(parsed)) {
                    persist(value, parsed);
                }
            } catch (NumberFormatException ignored) {
                // Incomplete input ("", "-", "1."); keep last persisted value.
            }
        });
        return box;
    }

    private static EditBox stringEditBox(int x, int y, int width, int height,
            ModConfigSpec.ConfigValue<String> value, ModConfigSpec.ValueSpec valueSpec) {
        EditBox box = new EditBox(Minecraft.getInstance().font, x, y, width, height, Component.literal("value"));
        box.setValue(value.get());
        box.setResponder(s -> {
            if (valueSpec.test(s)) {
                persist(value, s);
            }
        });
        return box;
    }

    private static EditBox listDisplayBox(int x, int y, int width, int height, int entryCount) {
        EditBox box = new EditBox(Minecraft.getInstance().font, x, y, width, height, Component.literal("list"));
        box.setValue("[" + entryCount + " entries]");
        box.setEditable(false);
        box.setTooltip(Tooltip.create(Component.literal("Edit in TOML file directly.")));
        return box;
    }

    private static void drawThemedBox(GuiGraphics graphics, AbstractWidget widget, int fill) {
        int x0 = widget.getX();
        int y0 = widget.getY();
        int x1 = x0 + widget.getWidth();
        int y1 = y0 + widget.getHeight();
        graphics.fill(x0, y0, x1, y1, fill);
        graphics.hLine(x0, x1 - 1, y0, INK);
        graphics.hLine(x0, x1 - 1, y1 - 1, INK);
        graphics.vLine(x0, y0, y1 - 1, INK);
        graphics.vLine(x1 - 1, y0, y1 - 1, INK);
    }

    /** Boolean toggle: filled rect changes colour, label flips Enabled/Disabled. */
    private static final class ToggleButton extends AbstractButton {
        private final ModConfigSpec.ConfigValue<Boolean> value;
        private boolean state;

        ToggleButton(int x, int y, int width, int height, ModConfigSpec.ConfigValue<Boolean> value) {
            super(x, y, width, height, Component.literal(value.get() ? "Enabled" : "Disabled"));
            this.value = value;
            this.state = value.get();
        }

        @Override
        public void onPress(@NonNull InputWithModifiers input) {
            state = !state;
            persist(value, state);
            setMessage(Component.literal(state ? "Enabled" : "Disabled"));
        }

        @Override
        protected void renderContents(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int fill = state ? TOGGLE_ON_FILL : (isHovered() ? WIDGET_FILL_HOVER : WIDGET_FILL);
            drawThemedBox(graphics, this, fill);
            int textColor = state ? TOGGLE_ON_TEXT : INK;
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage().getString(),
                    getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, textColor);
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /** Enum cycle button: each click advances to the next constant. */
    private static final class EnumCycleButton<E extends Enum<E>> extends AbstractButton {
        private final ModConfigSpec.ConfigValue<Enum<?>> value;
        private final E[] constants;
        private int index;

        @SuppressWarnings("unchecked")
        EnumCycleButton(int x, int y, int width, int height, ModConfigSpec.ConfigValue<Enum<?>> value, Class<E> enumClass) {
            super(x, y, width, height, Component.literal(humanise(value.get().name())));
            this.value = value;
            this.constants = enumClass.getEnumConstants();
            this.index = ((E) value.get()).ordinal();
        }

        @Override
        public void onPress(@NonNull InputWithModifiers input) {
            index = (index + 1) % constants.length;
            persist(value, constants[index]);
            setMessage(Component.literal(humanise(constants[index].name())));
        }

        @Override
        protected void renderContents(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            drawThemedBox(graphics, this, isHovered() ? WIDGET_FILL_HOVER : WIDGET_FILL);
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage().getString(),
                    getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, INK);
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /** Vanilla slider subclass for ranged numeric values; current value drawn as the message. */
    private static final class RangedSlider extends AbstractSliderButton {
        private final ModConfigSpec.ConfigValue<Number> configValue;
        private final double min;
        private final double max;
        private final boolean integer;

        RangedSlider(int x, int y, int width, int height, ModConfigSpec.ConfigValue<Number> configValue,
                ModConfigSpec.Range<?> range, boolean integer) {
            super(x, y, width, height, Component.empty(), 0.0);
            this.configValue = configValue;
            this.min = ((Number) range.getMin()).doubleValue();
            this.max = ((Number) range.getMax()).doubleValue();
            this.integer = integer;
            double current = configValue.get().doubleValue();
            this.value = max > min ? (current - min) / (max - min) : 0.0;
            updateMessage();
        }

        private Number currentValue() {
            double raw = min + value * (max - min);
            return integer ? (Number) (int) Math.round(raw) : (Number) (Math.round(raw * 100.0) / 100.0);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(String.valueOf(currentValue())));
        }

        @Override
        protected void applyValue() {
            persist(configValue, currentValue());
        }
    }
}
