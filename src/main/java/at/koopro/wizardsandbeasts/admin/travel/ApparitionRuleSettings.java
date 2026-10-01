package at.koopro.wizardsandbeasts.admin.travel;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.config.AdminSettingRegistry;
import at.koopro.wizardsandbeasts.admin.config.SettingBinding;
import at.koopro.wizardsandbeasts.admin.config.SettingTypes;
import at.koopro.wizardsandbeasts.apparition.ApparitionRules;
import at.koopro.wizardsandbeasts.apparition.ApparitionRulesService;
import at.koopro.wizardsandbeasts.apparition.splinch.WindupDamageMode;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.NullMarked;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Apparition's rules as admin settings (Travel → Apparition), bound to the world's {@code ApparitionRulesData}
 * through {@link ApparitionRulesService}. Every default is the constant the rule replaced, so an untouched world
 * Apparates exactly as before; the server reads each value at its one point ({@link ApparitionRules}).
 *
 * <p>Not offered because the game has no such thing: a combat lockout (Apparition is deliberately legal in combat;
 * the wind-up damage mode is the combat rule), an item or money cost, a random failure roll (splinching is
 * computed from the release, never rolled — severity scales that computation).
 */
@NullMarked
public final class ApparitionRuleSettings {

    public static final String WINDUP_DAMAGE_MODE = "apparition_windup_damage_mode";
    public static final String BLINK_COOLDOWN = "apparition_blink_cooldown_ticks";
    public static final String ANCHORED_COOLDOWN = "apparition_anchored_cooldown_ticks";
    public static final String SPLINCH_SEVERITY = "apparition_splinch_severity_percent";
    public static final String LICENCE_PROFICIENCY = "apparition_licence_proficiency_percent";

    private static final ApparitionRules.Tuning AUTHORED = ApparitionRules.Tuning.AUTHORED;

    private ApparitionRuleSettings() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    public static void contribute(AdminSettingRegistry registry) {
        registry.register(AdminSetting.builder(id(WINDUP_DAMAGE_MODE), SettingTypes.enumeration(WindupDamageMode.class),
                        new Binding<>(ApparitionRules.Tuning::windupDamageMode, AUTHORED.windupDamageMode(),
                                (t, v) -> new ApparitionRules.Tuning(v, t.blinkCooldownTicks(), t.anchoredCooldownTicks(),
                                        t.splinchSeverityPercent(), t.licenceProficiencyPercent())))
                .category(AdminCategory.TRAVEL)
                .build());
        registry.register(AdminSetting.builder(id(BLINK_COOLDOWN),
                        SettingTypes.integer(0, ApparitionRules.MAX_COOLDOWN_TICKS),
                        new Binding<>(ApparitionRules.Tuning::blinkCooldownTicks, AUTHORED.blinkCooldownTicks(),
                                (t, v) -> new ApparitionRules.Tuning(t.windupDamageMode(), v, t.anchoredCooldownTicks(),
                                        t.splinchSeverityPercent(), t.licenceProficiencyPercent())))
                .category(AdminCategory.TRAVEL)
                .build());
        registry.register(AdminSetting.builder(id(ANCHORED_COOLDOWN),
                        SettingTypes.integer(0, ApparitionRules.MAX_COOLDOWN_TICKS),
                        new Binding<>(ApparitionRules.Tuning::anchoredCooldownTicks, AUTHORED.anchoredCooldownTicks(),
                                (t, v) -> new ApparitionRules.Tuning(t.windupDamageMode(), t.blinkCooldownTicks(), v,
                                        t.splinchSeverityPercent(), t.licenceProficiencyPercent())))
                .category(AdminCategory.TRAVEL)
                // Below half the authored minute, cross-map jumps become a movement ability rather than a journey.
                .dangerRule((previous, candidate, authored, s) -> candidate < authored / 2 ? s.warningKey() : null)
                .build());
        registry.register(AdminSetting.builder(id(SPLINCH_SEVERITY),
                        SettingTypes.integer(0, ApparitionRules.MAX_SPLINCH_SEVERITY_PERCENT),
                        new Binding<>(ApparitionRules.Tuning::splinchSeverityPercent, AUTHORED.splinchSeverityPercent(),
                                (t, v) -> new ApparitionRules.Tuning(t.windupDamageMode(), t.blinkCooldownTicks(),
                                        t.anchoredCooldownTicks(), v, t.licenceProficiencyPercent())))
                .category(AdminCategory.TRAVEL)
                // Under half, a careless release stops costing anything: the skill stops mattering.
                .dangerRule((previous, candidate, authored, s) -> candidate < authored / 2 ? s.warningKey() : null)
                .build());
        registry.register(AdminSetting.builder(id(LICENCE_PROFICIENCY), SettingTypes.integer(0, 100),
                        new Binding<>(ApparitionRules.Tuning::licenceProficiencyPercent, AUTHORED.licenceProficiencyPercent(),
                                (t, v) -> new ApparitionRules.Tuning(t.windupDamageMode(), t.blinkCooldownTicks(),
                                        t.anchoredCooldownTicks(), t.splinchSeverityPercent(), v)))
                .category(AdminCategory.TRAVEL)
                .build());
    }

    /** One field of the world's Apparition rules: read from the published rules, written through the service. */
    private record Binding<T>(Function<ApparitionRules.Tuning, T> read, T authored,
                              BiFunction<ApparitionRules.Tuning, T, ApparitionRules.Tuning> write) implements SettingBinding<T> {
        @Override
        public T get() {
            return read.apply(ApparitionRules.current());
        }

        @Override
        public void set(T value) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                throw new IllegalStateException("no server to store an Apparition rule on");
            }
            ApparitionRulesService.update(server, tuning -> write.apply(tuning, value));
        }

        @Override
        public T defaultValue() {
            return authored;
        }

        @Override
        public boolean available() {
            return ServerLifecycleHooks.getCurrentServer() != null;
        }
    }
}
