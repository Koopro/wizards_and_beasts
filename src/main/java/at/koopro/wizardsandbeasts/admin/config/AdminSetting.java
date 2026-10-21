package at.koopro.wizardsandbeasts.admin.config;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.AdminLangKeys;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One administrable setting: what it is, what values it accepts, where its value lives, and who may change
 * it.
 *
 * <p>A definition validates; it does not store. The value belongs to its {@link SettingBinding}, the rules to
 * its {@link SettingType} and {@link SettingValidator}s. {@link at.koopro.wizardsandbeasts.admin.AdminSettingService}
 * is the only caller of {@link #binding()}'s setter — widgets, commands and packets all go through it.
 *
 * <p>Display text is never stored here: the name, description and warning are translation keys derived from
 * {@link #id()}, so the server sends ids and every client renders them in its own language.
 *
 * @param <T> the value type
 */
@NullMarked
public final class AdminSetting<T> {

    private final Identifier id;
    private final AdminCategory category;
    private final SettingType<T> type;
    private final SettingBinding<T> binding;
    private final SettingScope scope;
    private final ApplyMode applyMode;
    private final @Nullable DangerRule<T> dangerRule;
    private final AdminCapability capability;
    private final List<SettingValidator<T>> validators;
    private final List<ChangeListener<T>> listeners;

    private AdminSetting(Builder<T> builder) {
        this.id = builder.id;
        this.category = Objects.requireNonNull(builder.category, "category");
        this.type = builder.type;
        this.binding = builder.binding;
        this.scope = builder.scope;
        this.applyMode = builder.restartRequired || builder.binding.storeRequiresRestart()
                ? ApplyMode.RESTART : builder.applyMode;
        this.dangerRule = builder.dangerRule;
        this.capability = builder.capability != null ? builder.capability : category.defaultCapability();
        this.validators = List.copyOf(builder.validators);
        this.listeners = List.copyOf(builder.listeners);
    }

    public static <T> Builder<T> builder(Identifier id, SettingType<T> type, SettingBinding<T> binding) {
        return new Builder<>(id, type, binding);
    }

    // ── identity and metadata ──

    public Identifier id() {
        return id;
    }

    public AdminCategory category() {
        return category;
    }

    public SettingType<T> type() {
        return type;
    }

    public SettingBinding<T> binding() {
        return binding;
    }

    public SettingScope scope() {
        return scope;
    }

    public boolean restartRequired() {
        return applyMode == ApplyMode.RESTART;
    }

    public ApplyMode applyMode() {
        return applyMode;
    }

    /**
     * Whether some changes to this setting need an explicit confirmation. The server enforces it: an
     * unconfirmed change the {@link DangerRule} flags is answered with {@code CONFIRMATION_REQUIRED} and not
     * applied, whichever door it came through.
     */
    public boolean dangerous() {
        return dangerRule != null;
    }

    /**
     * The warning to confirm before changing {@code previous} to {@code candidate}, or null when the change is
     * harmless. Null for every change of a setting with no rule.
     */
    public @Nullable String confirmationFor(T previous, T candidate) {
        return dangerRule == null ? null : dangerRule.warn(previous, candidate, binding.defaultValue(), this);
    }

    public AdminCapability capability() {
        return capability;
    }

    /** Whether the server may change this at all — false for {@link SettingScope#CLIENT}. */
    public boolean writable() {
        return scope == SettingScope.SERVER;
    }

    public String nameKey() {
        return AdminLangKeys.settingName(id.getPath());
    }

    public String descriptionKey() {
        return AdminLangKeys.settingDescription(id.getPath());
    }

    /** The confirmation text for a {@link #dangerous()} setting. */
    public String warningKey() {
        return AdminLangKeys.settingWarning(id.getPath());
    }

    // ── values ──

    public T current() {
        return binding.get();
    }

    public String currentText() {
        return type.format(binding.get());
    }

    public String defaultText() {
        return type.format(binding.defaultValue());
    }

    public boolean isDefault() {
        return Objects.equals(binding.get(), binding.defaultValue());
    }

    /** Runs every extra rule; the first objection wins. */
    public @Nullable String firstObjection(T candidate, AdminSettingRegistry registry) {
        for (SettingValidator<T> validator : validators) {
            String objection = validator.check(candidate, registry);
            if (objection != null) {
                return objection;
            }
        }
        return null;
    }

    public void notifyChanged(T previous, T now, AdminContext actor) {
        for (ChangeListener<T> listener : listeners) {
            listener.onChange(previous, now, actor);
        }
    }

    /**
     * Decides which changes of a setting are dangerous enough to need confirmation.
     *
     * @param <T> the value type
     */
    @FunctionalInterface
    public interface DangerRule<T> {
        /** Every change needs confirmation; the warning is the setting's own {@code .warning} key. */
        static <T> DangerRule<T> always() {
            return (previous, candidate, authored, setting) -> setting.warningKey();
        }

        /**
         * @return a translation key describing the danger, or null when this particular change is harmless
         */
        @Nullable String warn(T previous, T candidate, T authoredDefault, AdminSetting<T> setting);
    }

    /** Called after a change has been stored, on the server thread. */
    @FunctionalInterface
    public interface ChangeListener<T> {
        void onChange(T previous, T now, AdminContext actor);
    }

    public static final class Builder<T> {
        private final Identifier id;
        private final SettingType<T> type;
        private final SettingBinding<T> binding;
        private @Nullable AdminCategory category;
        private SettingScope scope = SettingScope.SERVER;
        private boolean restartRequired;
        private ApplyMode applyMode = ApplyMode.RUNTIME;
        private @Nullable DangerRule<T> dangerRule;
        private @Nullable AdminCapability capability;
        private final List<SettingValidator<T>> validators = new ArrayList<>();
        private final List<ChangeListener<T>> listeners = new ArrayList<>();

        private Builder(Identifier id, SettingType<T> type, SettingBinding<T> binding) {
            this.id = id;
            this.type = type;
            this.binding = binding;
        }

        public Builder<T> category(AdminCategory category) {
            this.category = category;
            return this;
        }

        public Builder<T> scope(SettingScope scope) {
            this.scope = scope;
            return this;
        }

        public Builder<T> restartRequired(boolean restartRequired) {
            this.restartRequired = restartRequired;
            return this;
        }

        /** When a stored value reaches the game; {@link ApplyMode#RUNTIME} unless its reader says otherwise. */
        public Builder<T> applyMode(ApplyMode applyMode) {
            this.applyMode = applyMode;
            return this;
        }

        /** Every change needs confirmation (or, with false, none does). */
        public Builder<T> dangerous(boolean dangerous) {
            this.dangerRule = dangerous ? DangerRule.always() : null;
            return this;
        }

        /** Only the changes {@code rule} flags need confirmation. */
        public Builder<T> dangerRule(DangerRule<T> rule) {
            this.dangerRule = rule;
            return this;
        }

        public Builder<T> capability(AdminCapability capability) {
            this.capability = capability;
            return this;
        }

        public Builder<T> validator(SettingValidator<T> validator) {
            this.validators.add(validator);
            return this;
        }

        public Builder<T> onChange(ChangeListener<T> listener) {
            this.listeners.add(listener);
            return this;
        }

        public AdminSetting<T> build() {
            return new AdminSetting<>(this);
        }
    }
}
