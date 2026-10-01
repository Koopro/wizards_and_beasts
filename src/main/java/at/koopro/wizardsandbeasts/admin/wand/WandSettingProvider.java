package at.koopro.wizardsandbeasts.admin.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.config.AdminSettingProvider;
import at.koopro.wizardsandbeasts.admin.config.SettingBinding;
import at.koopro.wizardsandbeasts.admin.config.SettingTypes;
import at.koopro.wizardsandbeasts.wand.registry.WandDatapackRegistries;
import at.koopro.wizardsandbeasts.wand.rules.WandRules;
import at.koopro.wizardsandbeasts.wand.rules.WandRulesService;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Resolves the wandmaking rules as admin settings, from the live datapack registries and recipes:
 * <pre>
 * wand_wood/&lt;ns&gt;/&lt;wood&gt;/enabled
 * wand_core/&lt;ns&gt;/&lt;core&gt;/enabled
 * wand_pair/&lt;wood ns&gt;/&lt;wood&gt;/&lt;core ns&gt;/&lt;core&gt;/enabled     (only for pairs a wandmaking recipe allows)
 * </pre>
 * A wood, core or recipe added by any datapack gets its setting with no code. Every change goes through
 * {@code AdminSettingService}; withdrawing anything asks for confirmation, and nothing may withdraw the last pairing a
 * wand can still be made from.
 */
@NullMarked
public final class WandSettingProvider implements AdminSettingProvider {

    /** Every wood, core and recipe pairing switch, for profiles and snapshots. Needs the server's wand registries. */
    @Override
    public java.util.Collection<Identifier> enumerate(@Nullable MinecraftServer server) {
        java.util.List<Identifier> out = new java.util.ArrayList<>();
        if (server == null) {
            return out;
        }
        server.registryAccess().lookupOrThrow(WandDatapackRegistries.WAND_WOOD_REGISTRY).keySet().forEach(id -> out.add(woodId(id)));
        server.registryAccess().lookupOrThrow(WandDatapackRegistries.WAND_CORE_REGISTRY).keySet().forEach(id -> out.add(coreId(id)));
        for (String pair : WandRulesService.recipePairs(server)) {
            String[] halves = pair.split("\\|", 2);
            Identifier wood = Identifier.tryParse(halves[0]);
            Identifier core = halves.length > 1 ? Identifier.tryParse(halves[1]) : null;
            if (wood != null && core != null) {
                out.add(pairId(wood, core));
            }
        }
        return out;
    }

    public static final String WOOD = "wand_wood/";
    public static final String CORE = "wand_core/";
    public static final String PAIR = "wand_pair/";
    public static final String ENABLED = "enabled";

    public static Identifier woodId(Identifier wood) {
        return id(WOOD + wood.getNamespace() + "/" + wood.getPath() + "/" + ENABLED);
    }

    public static Identifier coreId(Identifier core) {
        return id(CORE + core.getNamespace() + "/" + core.getPath() + "/" + ENABLED);
    }

    public static Identifier pairId(Identifier wood, Identifier core) {
        return id(PAIR + wood.getNamespace() + "/" + wood.getPath() + "/" + core.getNamespace() + "/" + core.getPath()
                + "/" + ENABLED);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    public static boolean isWandSetting(Identifier id) {
        String path = id.getPath();
        return WizardsAndBeastsMod.MODID.equals(id.getNamespace())
                && (path.startsWith(WOOD) || path.startsWith(CORE) || path.startsWith(PAIR));
    }

    @Override
    public @Nullable AdminSetting<?> resolve(Identifier id) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !isWandSetting(id) || !id.getPath().endsWith("/" + ENABLED)) {
            return null;
        }
        String path = id.getPath();
        String[] parts = path.split("/");
        if (path.startsWith(WOOD) && parts.length == 4) {
            Identifier wood = Identifier.fromNamespaceAndPath(parts[1], parts[2]);
            if (!exists(server, true, wood)) {
                return null;
            }
            return setting(id, WandRules.woodEnabled(wood), on -> WandRulesService.setWood(server, wood, !on),
                    candidate -> survivesWithout(server, pair -> !pair.startsWith(wood + "|")));
        }
        if (path.startsWith(CORE) && parts.length == 4) {
            Identifier core = Identifier.fromNamespaceAndPath(parts[1], parts[2]);
            if (!exists(server, false, core)) {
                return null;
            }
            return setting(id, WandRules.coreEnabled(core), on -> WandRulesService.setCore(server, core, !on),
                    candidate -> survivesWithout(server, pair -> !pair.endsWith("|" + core)));
        }
        if (path.startsWith(PAIR) && parts.length == 6) {
            Identifier wood = Identifier.fromNamespaceAndPath(parts[1], parts[2]);
            Identifier core = Identifier.fromNamespaceAndPath(parts[3], parts[4]);
            if (WandRulesService.recipeFor(server, wood, core).isEmpty()) {
                return null;
            }
            String key = WandRules.pairKey(wood, core);
            return setting(id, WandRules.pairEnabled(wood, core), on -> WandRulesService.setPair(server, wood, core, !on),
                    candidate -> survivesWithout(server, pair -> !pair.equals(key)));
        }
        return null;
    }

    private static boolean exists(MinecraftServer server, boolean wood, Identifier id) {
        return wood
                ? server.registryAccess().lookup(WandDatapackRegistries.WAND_WOOD_REGISTRY).flatMap(l -> l.get(
                        net.minecraft.resources.ResourceKey.create(WandDatapackRegistries.WAND_WOOD_REGISTRY, id))).isPresent()
                : server.registryAccess().lookup(WandDatapackRegistries.WAND_CORE_REGISTRY).flatMap(l -> l.get(
                        net.minecraft.resources.ResourceKey.create(WandDatapackRegistries.WAND_CORE_REGISTRY, id))).isPresent();
    }

    /**
     * Whether some recipe pairing stays makeable once the pairs {@code keep} rejects are also withdrawn. The Ollivander
     * trial always has its fallback, but the bench would be dead: refuse the change that leaves nothing to make.
     */
    private static boolean survivesWithout(MinecraftServer server, Predicate<String> keep) {
        for (String pair : WandRulesService.recipePairs(server)) {
            String[] halves = pair.split("\\|", 2);
            if (keep.test(pair) && WandRules.mayMake(Identifier.parse(halves[0]), Identifier.parse(halves[1]))) {
                return true;
            }
        }
        return false;
    }

    private static AdminSetting<Boolean> setting(Identifier id, boolean current, Consumer<Boolean> write,
                                                 Predicate<Boolean> survives) {
        return AdminSetting.builder(id, SettingTypes.bool(), new RuleBinding(current, write))
                .category(AdminCategory.WANDS)
                .validator((candidate, registry) -> candidate || survives.test(false)
                        ? null : "admin.wizards_and_beasts.conflict.last_wand_pair")
                // Withdrawing a wood, a core or a pairing stops new wands of it on the whole server.
                .dangerRule((previous, candidate, shipped, setting) -> !candidate && previous ? setting.warningKey() : null)
                .build();
    }

    /** Enabled unless withdrawn; the shipped value is always "enabled". */
    private record RuleBinding(boolean current, Consumer<Boolean> write) implements SettingBinding<Boolean> {
        @Override
        public Boolean get() {
            return current;
        }

        @Override
        public void set(Boolean value) {
            write.accept(value);
        }

        @Override
        public Boolean defaultValue() {
            return Boolean.TRUE;
        }

        @Override
        public boolean available() {
            return ServerLifecycleHooks.getCurrentServer() != null;
        }
    }
}
