package at.koopro.wizardsandbeasts.admin.config.catalog;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.config.AdminSettingRegistry;
import at.koopro.wizardsandbeasts.admin.config.ConfigSpecBinding;
import at.koopro.wizardsandbeasts.admin.config.SettingType;
import at.koopro.wizardsandbeasts.admin.config.SettingTypes;
import at.koopro.wizardsandbeasts.admin.config.SettingValidator;
import at.koopro.wizardsandbeasts.spell.cast.SpellPower;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Turns {@link ConfigSettingCatalog} entries into {@link AdminSetting}s bound to the live {@code Config}
 * spec.
 *
 * <p>The type of each setting is <em>derived</em> from its {@link ModConfigSpec.ConfigValue}: a
 * {@code BooleanValue} becomes a toggle, an {@code IntValue}/{@code DoubleValue} a number whose bounds are
 * the spec's own {@code defineInRange} range, an {@code EnumValue} a choice over its enum. Nothing about a
 * value is restated here, so {@code Config.java} stays the single owner.
 *
 * <p>A catalog entry naming a key the spec does not have, or a kind this binder cannot adapt, fails loudly at
 * first use — a silent skip would leave an administrator wondering why a setting vanished.
 */
@NullMarked
public final class ConfigSettingBinder {

    private static final String SPELL_POWER_CONFLICT = "admin.wizards_and_beasts.conflict.spell_power_bounds";

    private ConfigSettingBinder() {}

    /** The live contributor: every catalog entry against {@link Config#SPEC}. */
    public static void bindAll(AdminSettingRegistry registry) {
        bind(registry, Config.SPEC, ConfigSettingCatalog.ENTRIES);
    }

    public static void bind(AdminSettingRegistry registry, ModConfigSpec spec, List<ConfigSettingCatalog.Entry> entries) {
        for (ConfigSettingCatalog.Entry entry : entries) {
            Object raw = spec.getValues().get(entry.configKey());
            if (!(raw instanceof ModConfigSpec.ConfigValue<?> value)) {
                throw new IllegalStateException("Admin catalog names config key '" + entry.configKey()
                        + "' which Config does not define");
            }
            registry.register(adapt(spec, value, entry));
        }
    }

    @SuppressWarnings("unchecked")
    private static AdminSetting<?> adapt(ModConfigSpec spec, ModConfigSpec.ConfigValue<?> value,
                                         ConfigSettingCatalog.Entry entry) {
        if (value instanceof ModConfigSpec.BooleanValue bool) {
            return build(entry, SettingTypes.bool(), new ConfigSpecBinding<>(spec, bool), null);
        }
        if (value instanceof ModConfigSpec.IntValue integer) {
            ModConfigSpec.Range<Integer> range = integer.getSpec().getRange();
            SettingType<Integer> type = range == null
                    ? SettingTypes.integer(Integer.MIN_VALUE, Integer.MAX_VALUE)
                    : SettingTypes.integer(range.getMin(), range.getMax());
            return build(entry, type, new ConfigSpecBinding<>(spec, integer), null);
        }
        if (value instanceof ModConfigSpec.DoubleValue decimal) {
            ModConfigSpec.Range<Double> range = decimal.getSpec().getRange();
            SettingType<Double> type = range == null
                    ? SettingTypes.decimal(-Double.MAX_VALUE, Double.MAX_VALUE)
                    : SettingTypes.decimal(range.getMin(), range.getMax());
            return build(entry, type, new ConfigSpecBinding<>(spec, decimal), doubleRule(entry));
        }
        if (value instanceof ModConfigSpec.EnumValue<?> choice) {
            return adaptEnum(spec, (ModConfigSpec.EnumValue) choice, entry);
        }
        throw new IllegalStateException("Admin catalog entry '" + entry.path() + "' is a "
                + value.getClass().getSimpleName() + ", which the binder cannot adapt yet");
    }

    private static <E extends Enum<E>> AdminSetting<E> adaptEnum(ModConfigSpec spec, ModConfigSpec.EnumValue<E> value,
                                                                 ConfigSettingCatalog.Entry entry) {
        Class<E> type = value.getDefault().getDeclaringClass();
        return build(entry, SettingTypes.enumeration(type), new ConfigSpecBinding<>(spec, value), null);
    }

    private static <T> AdminSetting<T> build(ConfigSettingCatalog.Entry entry, SettingType<T> type,
                                             ConfigSpecBinding<T> binding, @Nullable SettingValidator<T> rule) {
        AdminSetting.Builder<T> builder = AdminSetting.builder(id(entry.path()), type, binding)
                .category(entry.category())
                .scope(entry.scope());
        AdminSetting.DangerRule<T> danger = dangerRule(entry.danger());
        if (danger != null) {
            builder.dangerRule(danger);
        }
        if (rule != null) {
            builder.validator(rule);
        }
        return builder.build();
    }

    /**
     * The confirmation rule for a danger kind, judged on the values themselves: a switch's new state, or a
     * number against the setting's default.
     */
    static <T> AdminSetting.@Nullable DangerRule<T> dangerRule(ConfigSettingCatalog.Danger danger) {
        return switch (danger) {
            case NONE -> null;
            case ALWAYS -> AdminSetting.DangerRule.always();
            case WHEN_TURNED_ON -> (previous, candidate, authored, setting) ->
                    Boolean.TRUE.equals(candidate) ? setting.warningKey() : null;
            case WHEN_TURNED_OFF -> (previous, candidate, authored, setting) ->
                    Boolean.FALSE.equals(candidate) ? setting.warningKey() : null;
            case ABOVE_DOUBLE_DEFAULT -> (previous, candidate, authored, setting) ->
                    ((Number) candidate).doubleValue() > 2.0 * ((Number) authored).doubleValue() ? setting.warningKey() : null;
            case BELOW_HALF_DEFAULT -> (previous, candidate, authored, setting) ->
                    ((Number) candidate).doubleValue() < 0.5 * ((Number) authored).doubleValue() ? setting.warningKey() : null;
        };
    }

    private static @Nullable SettingValidator<Double> doubleRule(ConfigSettingCatalog.Entry entry) {
        ConfigSettingCatalog.Rule rule = entry.rule();
        if (rule == null) {
            return null;
        }
        return switch (rule) {
            case SPELL_POWER_BOUNDS -> spellPowerBounds(entry.path());
        };
    }

    /** Order matches the {@link SpellPower.Bounds} constructor. */
    private static final List<String> SPELL_POWER_PATHS = List.of(
            "spell_power_soft_cap_knee",
            "spell_power_max_multiplier",
            "spell_power_min_multiplier",
            "spell_cooldown_min_multiplier",
            "spell_cooldown_max_multiplier");

    /**
     * Asks {@link SpellPower.Bounds} itself whether the five bounds, with this candidate in its own slot and
     * the other four as stored, are a valid tuning. The rule (knee inside (0, max), min in (0, knee], cooldown
     * floor at or below its ceiling) is written once, in that constructor; restating it here is how the two
     * would drift.
     *
     * <p>Without this the change would still be "safe" — {@code SpellPower.applyBounds} falls back to the
     * shipped defaults on an invalid tuning — but silently: the panel would show the new number while every
     * cast used different ones.
     */
    private static SettingValidator<Double> spellPowerBounds(String ownPath) {
        int slot = SPELL_POWER_PATHS.indexOf(ownPath);
        if (slot < 0) {
            throw new IllegalStateException("'" + ownPath + "' carries SPELL_POWER_BOUNDS but is not a spell power bound");
        }
        return (candidate, registry) -> {
            // Floats, because Config.onLoad narrows each value to float before handing it to SpellPower.
            float[] bounds = new float[SPELL_POWER_PATHS.size()];
            for (int i = 0; i < bounds.length; i++) {
                AdminSetting<?> other = registry.get(id(SPELL_POWER_PATHS.get(i)));
                bounds[i] = i == slot ? candidate.floatValue()
                        : other == null ? Float.NaN : ((Number) other.current()).floatValue();
            }
            try {
                new SpellPower.Bounds(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4]);
                return null;
            } catch (IllegalArgumentException invalid) {
                return SPELL_POWER_CONFLICT;
            }
        };
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }
}
