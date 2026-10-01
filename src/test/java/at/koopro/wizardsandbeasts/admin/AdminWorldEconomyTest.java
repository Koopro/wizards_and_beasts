package at.koopro.wizardsandbeasts.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.config.AdminSettingRegistry;
import at.koopro.wizardsandbeasts.admin.config.ApplyMode;
import at.koopro.wizardsandbeasts.admin.config.catalog.ConfigSettingCatalog;
import at.koopro.wizardsandbeasts.admin.travel.ApparitionRuleSettings;
import at.koopro.wizardsandbeasts.apparition.ApparitionRules;
import at.koopro.wizardsandbeasts.apparition.ApparitionTier;
import at.koopro.wizardsandbeasts.apparition.charge.ApparitionWindow;
import at.koopro.wizardsandbeasts.apparition.licence.ApparitionLicence;
import at.koopro.wizardsandbeasts.apparition.splinch.WindupDamageMode;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 8 — Travel, Ministry, Economy, World: what is filed where, apply modes, the Apparition rules' arithmetic. */
class AdminWorldEconomyTest {

    /** Module switches added in Phase 8; each may warn, so each needs name, description and warning. */
    private static final List<String> MODULE_SETTINGS = List.of("module_floo_network", "module_apparition",
            "module_broom_flight", "module_ministry", "module_gringotts", "module_azkaban", "module_chamber_of_secrets",
            "module_pocket_dimensions", "module_structures");

    @AfterEach
    void restoreRules() {
        ApparitionRules.publish(ApparitionRules.Tuning.AUTHORED);
    }

    private static Set<String> langKeys() throws IOException {
        try (Reader reader = Files.newBufferedReader(Path.of("src", "main", "resources", "assets", "wizards_and_beasts",
                "lang", "en_us.json"))) {
            return JsonParser.parseReader(reader).getAsJsonObject().keySet();
        }
    }

    @Test
    void everyNewSettingSectionAndModeHasItsText() throws IOException {
        Set<String> keys = langKeys();
        Set<String> missing = new TreeSet<>();
        for (String path : MODULE_SETTINGS) {
            for (String key : List.of(AdminLangKeys.settingName(path), AdminLangKeys.settingDescription(path),
                    AdminLangKeys.settingWarning(path))) {
                if (!keys.contains(key)) {
                    missing.add(key);
                }
            }
        }
        AdminSettingRegistry registry = new AdminSettingRegistry();
        ApparitionRuleSettings.contribute(registry);
        for (AdminSetting<?> setting : registry.all()) {
            String path = setting.id().getPath();
            if (!keys.contains(AdminLangKeys.settingName(path)) || !keys.contains(AdminLangKeys.settingDescription(path))
                    || (setting.dangerous() && !keys.contains(AdminLangKeys.settingWarning(path)))) {
                missing.add(path);
            }
        }
        for (ApplyMode mode : ApplyMode.values()) {
            if (!keys.contains(mode.labelKey()) || !keys.contains(mode.explanationKey())) {
                missing.add(mode.labelKey());
            }
        }
        for (AdminCategory category : List.of(AdminCategory.ECONOMY, AdminCategory.WORLD)) {
            if (!keys.contains(category.nameKey()) || !keys.contains(category.summaryKey())) {
                missing.add(category.nameKey());
            }
        }
        assertTrue(missing.isEmpty(), () -> "phase 8 lang keys missing:\n  " + String.join("\n  ", missing));
    }

    @Test
    void economyHoldsPricesOnlyAndTravelHoldsTheFloo() {
        Set<String> economy = new TreeSet<>();
        Set<String> travel = new TreeSet<>();
        for (ConfigSettingCatalog.Entry entry : ConfigSettingCatalog.ENTRIES) {
            if (entry.category() == AdminCategory.ECONOMY) {
                economy.add(entry.path());
            }
            if (entry.category() == AdminCategory.TRAVEL && entry.path().startsWith("floo_")) {
                travel.add(entry.path());
            }
        }
        // Pinned: a new Economy setting is a deliberate change to this list, and never a way to move money.
        assertEquals(new TreeSet<>(Set.of("ollivander_wand_price_knuts", "floo_registration_fee_knuts",
                "skill_respec_cost_knuts", "dragot_galleon_rate")), economy);
        assertEquals(7, travel.size(), () -> "Floo settings under Travel: " + travel);
    }

    @Test
    void apparitionSettingsDefaultToTheAuthoredRules() {
        AdminSettingRegistry registry = new AdminSettingRegistry();
        ApparitionRuleSettings.contribute(registry);
        AdminSetting<?> blink = registry.get(ApparitionRuleSettings.id(ApparitionRuleSettings.BLINK_COOLDOWN));
        AdminSetting<?> anchored = registry.get(ApparitionRuleSettings.id(ApparitionRuleSettings.ANCHORED_COOLDOWN));
        AdminSetting<?> mode = registry.get(ApparitionRuleSettings.id(ApparitionRuleSettings.WINDUP_DAMAGE_MODE));
        AdminSetting<?> licence = registry.get(ApparitionRuleSettings.id(ApparitionRuleSettings.LICENCE_PROFICIENCY));
        assertNotNull(blink);
        assertNotNull(anchored);
        assertNotNull(mode);
        assertNotNull(licence);
        assertEquals(Integer.toString(ApparitionTier.BLINK.cooldownTicks()), blink.defaultText());
        assertEquals(Integer.toString(ApparitionTier.ANCHORED.cooldownTicks()), anchored.defaultText());
        assertEquals(WindupDamageMode.HYBRID.name(), mode.defaultText());
        assertEquals(Integer.toString(Math.round(ApparitionLicence.REQUIRED_PROFICIENCY * 100)), licence.defaultText());
        for (AdminSetting<?> setting : registry.all()) {
            assertEquals(AdminCategory.TRAVEL, setting.category(), setting.id().toString());
            assertEquals(ApplyMode.RUNTIME, setting.applyMode(), setting.id().toString());
        }
        assertFalse(blink.dangerous(), "only settings that can actually warn carry a danger rule");
        assertTrue(anchored.dangerous());
    }

    @Test
    void apparitionRulesArithmetic() {
        assertEquals(ApparitionRules.Tuning.AUTHORED, ApparitionRules.current());
        assertEquals(ApparitionTier.BLINK.cooldownTicks(), ApparitionRules.cooldownTicks(ApparitionTier.BLINK));
        assertEquals(17, ApparitionRules.scaleMiss(17), "100% is the authored ladder");

        ApparitionRules.publish(new ApparitionRules.Tuning(WindupDamageMode.CANCEL, 5, 600, 200, 50));
        assertEquals(5, ApparitionRules.cooldownTicks(ApparitionTier.BLINK));
        assertEquals(600, ApparitionRules.cooldownTicks(ApparitionTier.ANCHORED));
        assertEquals(34, ApparitionRules.scaleMiss(17));
        assertEquals(0.5f, ApparitionRules.requiredLicenceProficiency(), 1e-6);
        assertEquals(WindupDamageMode.CANCEL, ApparitionRules.windupDamageMode());

        ApparitionRules.publish(new ApparitionRules.Tuning(WindupDamageMode.HYBRID, 40, 1200, 0, 25));
        assertEquals(0, ApparitionRules.scaleMiss(30), "severity 0 forgives every miss");
        ApparitionRules.publish(new ApparitionRules.Tuning(WindupDamageMode.HYBRID, 40, 1200, 50, 25));
        assertEquals(ApparitionWindow.FORCED_DISCHARGE, ApparitionRules.scaleMiss(ApparitionWindow.FORCED_DISCHARGE),
                "the forced-discharge sentinel is never scaled");
    }

    @Test
    void settingIdsAreTheModsOwn() {
        Identifier id = ApparitionRuleSettings.id(ApparitionRuleSettings.SPLINCH_SEVERITY);
        assertEquals(WizardsAndBeastsMod.MODID, id.getNamespace());
        assertFalse(AdminLangKeys.entityScoped(id.getPath()), "Apparition rules are global settings, listed on their tab");
    }
}
