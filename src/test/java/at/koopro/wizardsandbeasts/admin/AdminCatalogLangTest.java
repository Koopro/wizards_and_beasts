package at.koopro.wizardsandbeasts.admin;

import at.koopro.wizardsandbeasts.admin.heritage.HeritageRuleSettings;
import at.koopro.wizardsandbeasts.admin.config.catalog.ConfigSettingCatalog;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every key the Control Center builds at runtime exists. {@code LangParityTest} cannot see these — they are
 * assembled from a setting's id — so a new catalog entry without its lang keys would render as raw keys in the
 * panel. This names the missing ones instead.
 */
class AdminCatalogLangTest {

    private static final Path MAIN_EN_US = Path.of("src", "main", "resources",
            "assets", "wizards_and_beasts", "lang", "en_us.json");
    private static final Pattern PATH = Pattern.compile("[a-z][a-z0-9_]*");

    private static Set<String> keys() throws IOException {
        try (Reader reader = Files.newBufferedReader(MAIN_EN_US)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return new HashSet<>(json.keySet());
        }
    }

    @Test
    void everyCatalogSettingHasItsText() throws IOException {
        Set<String> keys = keys();
        Set<String> missing = new TreeSet<>();
        for (ConfigSettingCatalog.Entry entry : ConfigSettingCatalog.ENTRIES) {
            require(keys, missing, AdminLangKeys.settingName(entry.path()));
            require(keys, missing, AdminLangKeys.settingDescription(entry.path()));
            if (entry.dangerous()) {
                require(keys, missing, AdminLangKeys.settingWarning(entry.path()));
            }
        }
        assertTrue(missing.isEmpty(), () -> "admin setting lang keys missing from en_us:\n  " + String.join("\n  ", missing));
    }

    @Test
    void everyHeritageRuleAndModuleSettingHasItsText() throws IOException {
        Set<String> keys = keys();
        Set<String> missing = new TreeSet<>();
        for (HeritageRuleSettings.Property property : HeritageRuleSettings.Property.values()) {
            String path = HeritageRuleSettings.PREFIX + "any/" + property.path();
            require(keys, missing, AdminLangKeys.settingName(path));
            require(keys, missing, AdminLangKeys.settingDescription(path));
        }
        // Only opening an unfinished heritage asks for confirmation.
        require(keys, missing, AdminLangKeys.settingWarning(HeritageRuleSettings.PREFIX + "any/selectable"));
        for (String property : List.of("natural_spawn", "variant/x/enabled", "variant/x/weight")) {
            String path = "creature/any/" + property;
            require(keys, missing, AdminLangKeys.settingName(path));
            require(keys, missing, AdminLangKeys.settingDescription(path));
        }
        require(keys, missing, "admin.wizards_and_beasts.conflict.last_enabled_variant");
        for (String path : List.of("brew/ns/x/enabled", "brew/ns/x/effects", "brew_recipe/ns/x/heat_time",
                "brew_recipe/ns/x/failure_chance", "brew_recipe/ns/x/cauldron_tier", "brew_recipe/ns/x/ingredient.minecraft.gold_ingot")) {
            require(keys, missing, AdminLangKeys.settingName(path));
            require(keys, missing, AdminLangKeys.settingDescription(path));
        }
        require(keys, missing, AdminLangKeys.settingWarning("brew/ns/x/effects"));
        require(keys, missing, "admin.wizards_and_beasts.conflict.brew_does_nothing");
        require(keys, missing, "admin.wizards_and_beasts.conflict.recipe_shadowed");
        for (String module : List.of("module_dark_arts", "module_heritage", "module_player_stats", "module_creatures")) {
            require(keys, missing, AdminLangKeys.settingName(module));
            require(keys, missing, AdminLangKeys.settingDescription(module));
            require(keys, missing, AdminLangKeys.settingWarning(module));
        }
        require(keys, missing, "admin.wizards_and_beasts.conflict.last_selectable_heritage");
        // Wand and broom settings are resolved from datapack ids; every family and stat has its text.
        List<String> wandBroom = new java.util.ArrayList<>(List.of("wand_wood/ns/x/enabled", "wand_core/ns/x/enabled",
                "wand_pair/ns/x/ns/y/enabled", "broom/ns/x/enabled"));
        for (at.koopro.wizardsandbeasts.broom.rules.BroomStat stat : at.koopro.wizardsandbeasts.broom.rules.BroomStat.values()) {
            wandBroom.add("broom/ns/x/" + stat.id());
        }
        for (String path : wandBroom) {
            require(keys, missing, AdminLangKeys.settingName(path));
            require(keys, missing, AdminLangKeys.settingDescription(path));
        }
        for (String path : List.of("wand_wood/ns/x/enabled", "wand_core/ns/x/enabled", "wand_pair/ns/x/ns/y/enabled",
                "broom/ns/x/enabled", "broom/ns/x/max_speed")) {
            require(keys, missing, AdminLangKeys.settingWarning(path));
        }
        require(keys, missing, "admin.wizards_and_beasts.conflict.last_wand_pair");
        for (String reason : List.of("valid", "unauthorized", "unknown_wood", "unknown_core", "no_recipe", "withdrawn",
                "bad_length", "bad_flexibility", "bad_preset", "inventory_full", "given")) {
            require(keys, missing, "admin.wizards_and_beasts.wand_action." + reason);
        }
        require(keys, missing, "wandcraft.bench.status.withdrawn");
        require(keys, missing, "broom.wizards_and_beasts.withdrawn");
        require(keys, missing, "broom.wizards_and_beasts.speed_guard");
        require(keys, missing, "message.wizards_and_beasts.type_selection.closed");
        assertTrue(missing.isEmpty(), () -> "heritage admin lang keys missing from en_us:\n  " + String.join("\n  ", missing));
    }

    @Test
    void everySectionAndRejectionHasItsText() throws IOException {
        Set<String> keys = keys();
        Set<String> missing = new TreeSet<>();
        for (AdminCategory category : AdminCategory.values()) {
            require(keys, missing, category.nameKey());
            require(keys, missing, category.summaryKey());
        }
        for (AdminRejection rejection : AdminRejection.values()) {
            require(keys, missing, rejection.translationKey());
        }
        assertTrue(missing.isEmpty(), () -> "admin lang keys missing from en_us:\n  " + String.join("\n  ", missing));
    }

    @Test
    void catalogPathsAreUniqueAndTypeable() {
        Set<String> seen = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (ConfigSettingCatalog.Entry entry : ConfigSettingCatalog.ENTRIES) {
            assertTrue(PATH.matcher(entry.path()).matches(), () -> entry.path() + " is not a snake_case word");
            assertTrue(seen.add(entry.path()), () -> "duplicate admin path " + entry.path());
            assertTrue(keys.add(entry.configKey()), () -> "config key " + entry.configKey() + " exposed twice");
        }
    }

    private static void require(Set<String> keys, Set<String> missing, String key) {
        if (!keys.contains(key)) {
            missing.add(key);
        }
    }
}
