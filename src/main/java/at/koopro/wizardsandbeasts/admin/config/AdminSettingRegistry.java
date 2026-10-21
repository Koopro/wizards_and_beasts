package at.koopro.wizardsandbeasts.admin.config;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every administrable setting, in registration order — which is display order inside a section.
 *
 * <p>An instance, not a static map, so a test builds its own from {@link InMemoryBinding}s and the live
 * one ({@link at.koopro.wizardsandbeasts.admin.AdminSettings}) is never touched by a unit test.
 * {@link #freeze()} ends registration; after that the set of ids is fixed for the server's lifetime, which is
 * what lets a client cache descriptors by id.
 */
@NullMarked
public final class AdminSettingRegistry {

    private final Map<Identifier, AdminSetting<?>> byId = new LinkedHashMap<>();
    private final List<AdminSettingProvider> providers = new ArrayList<>();
    private boolean frozen;

    public <T> AdminSetting<T> register(AdminSetting<T> setting) {
        if (frozen) {
            throw new IllegalStateException("Admin settings are frozen; register " + setting.id()
                    + " through AdminSettings.addContributor before first use");
        }
        if (byId.putIfAbsent(setting.id(), setting) != null) {
            throw new IllegalArgumentException("Duplicate admin setting id: " + setting.id());
        }
        return setting;
    }

    /**
     * Adds a source of settings resolved on demand — for families of settings whose members come and go with
     * datapacks (one per spell and property). Must be added before {@link #freeze()}.
     */
    public void addProvider(AdminSettingProvider provider) {
        if (frozen) {
            throw new IllegalStateException("Admin settings are frozen; add providers before first use");
        }
        providers.add(provider);
    }

    public void freeze() {
        frozen = true;
    }

    /** The static setting with this id, else the first provider's answer, else null. */
    public @Nullable AdminSetting<?> get(Identifier id) {
        AdminSetting<?> fixed = byId.get(id);
        if (fixed != null) {
            return fixed;
        }
        for (AdminSettingProvider provider : providers) {
            AdminSetting<?> resolved = provider.resolve(id);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    /**
     * Resolves what a person typed: a bare path means this mod's namespace, so
     * {@code enforce_spell_requirements} and {@code wizards_and_beasts:enforce_spell_requirements} are the same.
     */
    public @Nullable AdminSetting<?> find(String raw) {
        String trimmed = raw.trim();
        Identifier id = trimmed.indexOf(':') >= 0
                ? Identifier.tryParse(trimmed)
                : Identifier.tryBuild(WizardsAndBeastsMod.MODID, trimmed);
        return id == null ? null : get(id);
    }

    /** The static settings only; provider-resolved settings are enumerated by their own panels. */
    public Collection<AdminSetting<?>> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    /**
     * Every setting that exists right now: the static ones, then each provider's enumerated ids resolved — the whole
     * server configuration, for profiles and snapshots. Ids that no longer resolve (a reload removed a spell) are
     * skipped; an id two sources claim is listed once.
     */
    public List<AdminSetting<?>> everything(net.minecraft.server.@Nullable MinecraftServer server) {
        Map<Identifier, AdminSetting<?>> out = new LinkedHashMap<>(byId);
        for (AdminSettingProvider provider : providers) {
            for (Identifier id : provider.enumerate(server)) {
                if (!out.containsKey(id)) {
                    AdminSetting<?> resolved = provider.resolve(id);
                    if (resolved != null) {
                        out.put(id, resolved);
                    }
                }
            }
        }
        return List.copyOf(out.values());
    }

    public List<AdminSetting<?>> inCategory(AdminCategory category) {
        List<AdminSetting<?>> matches = new ArrayList<>();
        for (AdminSetting<?> setting : byId.values()) {
            if (setting.category() == category) {
                matches.add(setting);
            }
        }
        return matches;
    }

    public int size() {
        return byId.size();
    }
}
