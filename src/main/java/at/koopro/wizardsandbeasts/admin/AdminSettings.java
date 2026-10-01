package at.koopro.wizardsandbeasts.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.config.AdminSettingRegistry;
import at.koopro.wizardsandbeasts.admin.config.catalog.ConfigSettingBinder;
import at.koopro.wizardsandbeasts.admin.history.InMemoryChangeHistory;
import at.koopro.wizardsandbeasts.network.admin.AdminNetworkService;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The live administration service: one registry, one history, one mutation API for the whole server.
 *
 * <p>Built on first use rather than at mod construction, because the {@code Config} catalog reads
 * {@code Config.SPEC} and that must never happen in a JVM that is only loading classes (see the 2026-08-20
 * lesson on {@code Config} static initialisers). First use is always on a running server — a command, a
 * packet, a game test.
 *
 * <p>Other systems add settings through {@link #addContributor}, called from mod construction or common
 * setup. After the first {@link #service()} call the registry is frozen for the JVM's lifetime; the ids a
 * client has cached therefore never change under it.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class AdminSettings {

    /** Enough for a long session of administration, small enough that spam cannot grow it. */
    private static final int HISTORY_CAPACITY = at.koopro.wizardsandbeasts.admin.history.AdminHistoryData.CAPACITY;

    private static final List<Consumer<AdminSettingRegistry>> CONTRIBUTORS = new ArrayList<>();
    private static @Nullable AdminSettingService service;

    static {
        CONTRIBUTORS.add(ConfigSettingBinder::bindAll);
        CONTRIBUTORS.add(at.koopro.wizardsandbeasts.admin.config.catalog.ModuleStateSettings::contribute);
        // One rule per heritage and property, derived from the Heritage enum.
        CONTRIBUTORS.add(at.koopro.wizardsandbeasts.admin.heritage.HeritageRuleSettings::contribute);
        // One natural-spawn rule per creature and an enabled/weight pair per variant, derived from the roster.
        CONTRIBUTORS.add(at.koopro.wizardsandbeasts.admin.creature.CreatureRuleSettings::contribute);
        // One setting per brew / recipe and property, resolved live so /reload never leaves a stale one behind.
        CONTRIBUTORS.add(registry -> registry.addProvider(new at.koopro.wizardsandbeasts.admin.brew.BrewSettingProvider()));
        // Wood, core and pairing rules from the live wand registries and wandmaking recipes; broom flight numbers
        // from the authored broom definitions.
        CONTRIBUTORS.add(registry -> registry.addProvider(new at.koopro.wizardsandbeasts.admin.wand.WandSettingProvider()));
        CONTRIBUTORS.add(registry -> registry.addProvider(new at.koopro.wizardsandbeasts.admin.broom.BroomSettingProvider()));
        // One setting per spell and property, resolved live so /reload never leaves a stale one behind.
        CONTRIBUTORS.add(registry -> registry.addProvider(new at.koopro.wizardsandbeasts.admin.spell.SpellSettingProvider()));
        // Apparition's world-owned rules (Travel → Apparition); defaults are the constants they replaced.
        CONTRIBUTORS.add(at.koopro.wizardsandbeasts.admin.travel.ApparitionRuleSettings::contribute);
        // One visual setting per beam spell and look property (Visuals → Spell Beams); defaults are the code's looks.
        CONTRIBUTORS.add(registry -> registry.addProvider(new at.koopro.wizardsandbeasts.admin.visual.BeamVisualSettingProvider()));
    }

    private AdminSettings() {}

    /** Registers more settings. Must run before the first {@link #service()} call. */
    public static synchronized void addContributor(Consumer<AdminSettingRegistry> contributor) {
        if (service != null) {
            throw new IllegalStateException("Admin settings are already built; contribute during mod setup");
        }
        CONTRIBUTORS.add(contributor);
    }

    public static synchronized AdminSettingService service() {
        AdminSettingService built = service;
        if (built == null) {
            AdminSettingRegistry registry = new AdminSettingRegistry();
            CONTRIBUTORS.forEach(contributor -> contributor.accept(registry));
            registry.freeze();
            built = new AdminSettingService(registry, new InMemoryChangeHistory(HISTORY_CAPACITY),
                    System::currentTimeMillis);
            // Every applied change reaches every other open panel, whether a packet or a command made it.
            built.addObserver(AdminNetworkService::announce);
            service = built;
        }
        return built;
    }

    public static AdminSettingRegistry registry() {
        return service().registry();
    }

    /**
     * History is server-lifetime. In singleplayer the JVM outlives the server, and a world opened after
     * another must not offer to undo the previous world's changes.
     */
    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        AdminSettingService built;
        synchronized (AdminSettings.class) {
            built = service;
        }
        if (built != null) {
            built.history().clear();
        }
    }
}
