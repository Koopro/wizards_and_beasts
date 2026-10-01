package at.koopro.wizardsandbeasts.admin.creature;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.config.AdminSettingRegistry;
import at.koopro.wizardsandbeasts.admin.config.SettingBinding;
import at.koopro.wizardsandbeasts.admin.config.SettingTypes;
import at.koopro.wizardsandbeasts.creature.rules.CreatureRule;
import at.koopro.wizardsandbeasts.creature.rules.CreatureRules;
import at.koopro.wizardsandbeasts.creature.rules.CreatureRulesService;
import at.koopro.wizardsandbeasts.creature.variant.CreatureVariant;
import at.koopro.wizardsandbeasts.creature.variant.CreatureVariants;
import at.koopro.wizardsandbeasts.registry.ModCreatures;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The per-creature rules as admin settings, derived from the roster and the variant enums:
 * <ul>
 *   <li>{@code creature/<id>/natural_spawn} for every creature in {@link ModCreatures#ROSTER};</li>
 *   <li>{@code creature/<id>/variant/<variant>/enabled} and {@code .../weight} for every variant of every creature
 *       in {@link CreatureVariants}.</li>
 * </ul>
 * A creature added to the roster, or a variant added to an enum, gets its rows with no change here or in the screen.
 * Every change goes through {@code AdminSettingService}: authorisation, validation, history, undo, broadcast.
 */
@NullMarked
public final class CreatureRuleSettings {

    public static final String PREFIX = "creature/";
    public static final String NATURAL_SPAWN = "natural_spawn";
    public static final String VARIANT = "variant";
    public static final String ENABLED = "enabled";
    public static final String WEIGHT = "weight";
    public static final int MAX_WEIGHT = 1000;

    private CreatureRuleSettings() {}

    public static void contribute(AdminSettingRegistry registry) {
        for (String creature : new TreeSet<>(ModCreatures.ROSTER)) {
            registry.register(naturalSpawn(creature));
            for (CreatureVariant variant : CreatureVariants.of(creature)) {
                registry.register(variantEnabled(creature, variant));
                registry.register(variantWeight(creature, variant));
            }
        }
    }

    public static Identifier naturalSpawnId(String creature) {
        return id(creature + "/" + NATURAL_SPAWN);
    }

    public static Identifier variantId(String creature, String variant, String property) {
        return id(creature + "/" + VARIANT + "/" + variant + "/" + property);
    }

    private static Identifier id(String rest) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, PREFIX + rest);
    }

    /** The creature an id belongs to, or null when it is not a creature rule id. */
    public static @Nullable String creatureOf(Identifier id) {
        String path = id.getPath();
        if (!WizardsAndBeastsMod.MODID.equals(id.getNamespace()) || !path.startsWith(PREFIX)) {
            return null;
        }
        String[] parts = path.substring(PREFIX.length()).split("/");
        return parts.length >= 2 && ModCreatures.ROSTER.contains(parts[0]) ? parts[0] : null;
    }

    private static AdminSetting<Boolean> naturalSpawn(String creature) {
        return AdminSetting.builder(naturalSpawnId(creature), SettingTypes.bool(),
                        new RuleBinding<>(creature, Boolean.TRUE, CreatureRule::naturalSpawn, CreatureRule::withNaturalSpawn))
                .category(AdminCategory.CREATURES)
                .build();
    }

    private static AdminSetting<Boolean> variantEnabled(String creature, CreatureVariant variant) {
        String v = variant.variantId();
        return AdminSetting.builder(variantId(creature, v, ENABLED), SettingTypes.bool(),
                        new RuleBinding<Boolean>(creature, Boolean.TRUE,
                                rule -> Optional.ofNullable(rule.variantEnabled().get(v)),
                                (rule, value) -> rule.withVariantEnabled(v, value)))
                .category(AdminCategory.CREATURES)
                // The roll needs something to land on: the last enabled variant cannot be switched off.
                .validator((candidate, registry) -> candidate || anotherEnabled(creature, variant)
                        ? null : "admin.wizards_and_beasts.conflict.last_enabled_variant")
                .build();
    }

    private static AdminSetting<Integer> variantWeight(String creature, CreatureVariant variant) {
        String v = variant.variantId();
        return AdminSetting.builder(variantId(creature, v, WEIGHT), SettingTypes.integer(1, MAX_WEIGHT),
                        new RuleBinding<Integer>(creature, variant.authoredWeight(),
                                rule -> Optional.ofNullable(rule.variantWeight().get(v)),
                                (rule, value) -> rule.withVariantWeight(v, value)))
                .category(AdminCategory.CREATURES)
                .build();
    }

    static boolean anotherEnabled(String creature, CreatureVariant closing) {
        List<CreatureVariant> all = CreatureVariants.of(creature);
        for (CreatureVariant other : all) {
            if (other != closing && CreatureRules.variantEnabled(creature, other)) {
                return true;
            }
        }
        return false;
    }

    /** An optional override: absent means as shipped; setting the shipped value removes the override. */
    private record RuleBinding<T>(String creature, T shipped,
                                  Function<CreatureRule, Optional<T>> read,
                                  BiFunction<CreatureRule, Optional<T>, CreatureRule> write) implements SettingBinding<T> {

        @Override
        public T get() {
            return read.apply(CreatureRules.rule(creature)).orElse(shipped);
        }

        @Override
        public void set(T value) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                throw new IllegalStateException("no server to store a creature rule on");
            }
            Optional<T> stored = value.equals(shipped) ? Optional.empty() : Optional.of(value);
            CreatureRulesService.update(server, creature, rule -> write.apply(rule, stored));
        }

        @Override
        public T defaultValue() {
            return shipped;
        }

        @Override
        public boolean available() {
            return ServerLifecycleHooks.getCurrentServer() != null;
        }
    }
}
