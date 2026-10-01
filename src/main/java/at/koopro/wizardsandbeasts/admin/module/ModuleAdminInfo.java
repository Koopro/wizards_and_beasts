package at.koopro.wizardsandbeasts.admin.module;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.config.ApplyMode;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleIds;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * What the Control Center says about a module beyond its state: the setting that switches it, the section that owns
 * that switch, when a change reaches the game, and where its gate is read. Every {@link Module} has an answer — the
 * maps below hold only the exceptions, so a module added to the enum appears everywhere with sensible defaults and no
 * edit here.
 */
@NullMarked
public final class ModuleAdminInfo {

    /** Where the module's gate is read. Module <em>state</em> is always server-owned and synced. */
    public enum Side {
        /** Server gates enforce it; clients also read it (content hidden from creative tabs and JEI, HUD, screens). */
        BOTH,
        /** Only presentation reads it: a client screen or renderer. */
        CLIENT;

        public String labelKey() {
            return "admin.wizards_and_beasts.module_side." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Modules whose switch lives on a system's own page; the rest are switched under Modules. */
    private static final Map<Module, AdminCategory> HOME = new EnumMap<>(Module.class);
    /** Decided when terrain generates (the structure gates). */
    private static final Map<Module, ApplyMode> APPLY = new EnumMap<>(Module.class);
    /** Presentation-only modules, found by auditing who reads each gate (client code only). */
    private static final Map<Module, Side> SIDE = new EnumMap<>(Module.class);

    static {
        HOME.put(Module.DARK_ARTS, AdminCategory.DARK_ARTS);
        HOME.put(Module.HERITAGE, AdminCategory.HERITAGES);
        HOME.put(Module.PLAYER_STATS, AdminCategory.HERITAGES);
        HOME.put(Module.CREATURES, AdminCategory.CREATURES);
        HOME.put(Module.FLOO_NETWORK, AdminCategory.TRAVEL);
        HOME.put(Module.APPARITION, AdminCategory.TRAVEL);
        HOME.put(Module.BROOM_FLIGHT, AdminCategory.TRAVEL);
        HOME.put(Module.MINISTRY, AdminCategory.MINISTRY);
        HOME.put(Module.GRINGOTTS, AdminCategory.ECONOMY);
        HOME.put(Module.AZKABAN, AdminCategory.WORLD);
        HOME.put(Module.CHAMBER_OF_SECRETS, AdminCategory.WORLD);
        HOME.put(Module.POCKET_DIMENSIONS, AdminCategory.WORLD);
        HOME.put(Module.STRUCTURES, AdminCategory.WORLD);

        APPLY.put(Module.AZKABAN, ApplyMode.NEW_CHUNKS);
        APPLY.put(Module.CHAMBER_OF_SECRETS, ApplyMode.NEW_CHUNKS);

        SIDE.put(Module.CHARACTER_SHEET, Side.CLIENT);
        SIDE.put(Module.PLAYER_ANIMATION, Side.CLIENT);
    }

    private ModuleAdminInfo() {}

    /** {@code wizards_and_beasts:module_<id>} — the admin setting that switches {@code module}. */
    public static Identifier settingId(Module module) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "module_" + ModuleIds.of(module).getPath());
    }

    public static AdminCategory home(Module module) {
        return HOME.getOrDefault(module, AdminCategory.MODULES);
    }

    /** Every module change is runtime (recipes reload automatically) except the worldgen gates. */
    public static ApplyMode applyMode(Module module) {
        return APPLY.getOrDefault(module, ApplyMode.RUNTIME);
    }

    public static Side side(Module module) {
        return SIDE.getOrDefault(module, Side.BOTH);
    }
}
