package at.koopro.wizardsandbeasts.admin.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.config.AdminSettingRegistry;
import at.koopro.wizardsandbeasts.admin.config.SettingBinding;
import at.koopro.wizardsandbeasts.admin.config.SettingTypes;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageTransformService;
import at.koopro.wizardsandbeasts.heritage.rules.HeritageRule;
import at.koopro.wizardsandbeasts.heritage.rules.HeritageRules;
import at.koopro.wizardsandbeasts.heritage.rules.HeritageRulesService;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The per-heritage rules as admin settings: {@code heritage/<id>/selectable} for every heritage, and
 * {@code heritage/<id>/transformation} for every heritage whose change of shape {@link HeritageTransformService}
 * owns. Derived from {@link Heritage#values()} and {@link HeritageTransformService#SERVED} — a heritage added to
 * the enum gets its rows without a line here or in the screen.
 *
 * <p>Registered up front rather than through a provider: heritages are an enum, fixed for the life of the game,
 * so there is nothing for a {@code /reload} to invalidate. Every change goes through {@code AdminSettingService}
 * like any other setting — authorisation, validation, confirmation, history, undo, broadcast.
 */
@NullMarked
public final class HeritageRuleSettings {

    public static final String PREFIX = "heritage/";

    /** The per-heritage rules this mod can actually enforce. Anything else is documented, not invented. */
    public enum Property {
        SELECTABLE("selectable"),
        TRANSFORMATION("transformation");

        private final String path;

        Property(String path) {
            this.path = path;
        }

        public String path() {
            return path;
        }

        public static @Nullable Property byPath(String path) {
            for (Property property : values()) {
                if (property.path.equals(path)) {
                    return property;
                }
            }
            return null;
        }
    }

    private HeritageRuleSettings() {}

    public static void contribute(AdminSettingRegistry registry) {
        for (Heritage heritage : Heritage.values()) {
            for (Property property : applicable(heritage)) {
                registry.register(build(heritage, property));
            }
        }
    }

    /** Which rules mean something for {@code heritage}: no transformation switch for a people that never changes. */
    public static List<Property> applicable(Heritage heritage) {
        List<Property> out = new ArrayList<>(2);
        out.add(Property.SELECTABLE);
        if (HeritageTransformService.SERVED.contains(heritage)) {
            out.add(Property.TRANSFORMATION);
        }
        return out;
    }

    public static Identifier id(Heritage heritage, Property property) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
                PREFIX + heritage.getId() + "/" + property.path());
    }

    /** The heritage an id belongs to, or null when it is not a heritage rule id. */
    public static @Nullable Heritage heritageOf(Identifier id) {
        String path = id.getPath();
        if (!WizardsAndBeastsMod.MODID.equals(id.getNamespace()) || !path.startsWith(PREFIX)) {
            return null;
        }
        String[] parts = path.substring(PREFIX.length()).split("/");
        return parts.length == 2 && Property.byPath(parts[1]) != null ? Heritage.byId(parts[0]) : null;
    }

    private static AdminSetting<Boolean> build(Heritage heritage, Property property) {
        Identifier id = id(heritage, property);
        return switch (property) {
            case SELECTABLE -> AdminSetting.builder(id, SettingTypes.bool(),
                            new RuleBinding(heritage, heritage.isAlphaAvailable(),
                                    HeritageRule::selectable, HeritageRule::withSelectable))
                    .category(AdminCategory.HERITAGES)
                    .validator((candidate, registry) -> candidate || anotherSelectable(heritage)
                            ? null : "admin.wizards_and_beasts.conflict.last_selectable_heritage")
                    // Opening a heritage the mod does not ship as finished offers new players unfinished content.
                    .dangerRule((previous, candidate, shipped, setting) ->
                            candidate && !previous && !shipped ? setting.warningKey() : null)
                    .build();
            case TRANSFORMATION -> AdminSetting.builder(id, SettingTypes.bool(),
                            new RuleBinding(heritage, Boolean.TRUE,
                                    HeritageRule::transformation, HeritageRule::withTransformation))
                    .category(AdminCategory.HERITAGES)
                    .build();
        };
    }

    /**
     * Closing the last selectable heritage would leave every new player in front of a gate they cannot pass —
     * the onboarding screen swallows ESC. The service refuses that as a conflict.
     */
    static boolean anotherSelectable(Heritage closing) {
        for (Heritage other : Heritage.values()) {
            if (other != closing && !other.getSubtypes().isEmpty() && HeritageRules.selectable(other)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A rule stored as an optional override: absent means "as shipped". Setting the shipped value removes the
     * override rather than pinning a copy of it.
     */
    private record RuleBinding(Heritage heritage, Boolean shipped,
                               Function<HeritageRule, Optional<Boolean>> read,
                               BiFunction<HeritageRule, Optional<Boolean>, HeritageRule> write)
            implements SettingBinding<Boolean> {

        @Override
        public Boolean get() {
            return read.apply(HeritageRules.local().getOrDefault(heritage.getId(), HeritageRule.NONE)).orElse(shipped);
        }

        @Override
        public void set(Boolean value) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                throw new IllegalStateException("no server to store a heritage rule on");
            }
            Optional<Boolean> stored = value.equals(shipped) ? Optional.empty() : Optional.of(value);
            HeritageRulesService.update(server, heritage, rule -> write.apply(rule, stored));
        }

        @Override
        public Boolean defaultValue() {
            return shipped;
        }

        @Override
        public boolean available() {
            return ServerLifecycleHooks.getCurrentServer() != null;
        }
    }
}
