package at.koopro.wizardsandbeasts.admin.profile;

import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.config.AdminSettingRegistry;
import at.koopro.wizardsandbeasts.admin.config.SettingType;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleIds;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.ModuleState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The running server's settings as a {@link ProfileTarget}: every setting in the registry, providers enumerated, seen
 * with one actor's capabilities. Built once per validation, so one plan reads one consistent picture.
 */
@NullMarked
public final class LiveProfileTarget implements ProfileTarget {

    private static final String MODULE_PREFIX = "module_";

    private final AdminSettingRegistry registry;
    private final AdminContext actor;
    private final Map<Identifier, AdminSetting<?>> settings = new LinkedHashMap<>();
    private final List<Identifier> serverSettings = new ArrayList<>();

    public LiveProfileTarget(AdminSettingRegistry registry, AdminContext actor, @Nullable MinecraftServer server) {
        this.registry = registry;
        this.actor = actor;
        for (AdminSetting<?> setting : registry.everything(server)) {
            settings.put(setting.id(), setting);
            if (setting.writable() && setting.binding().available()) {
                serverSettings.add(setting.id());
            }
        }
    }

    @Override
    public List<Identifier> serverSettings() {
        return serverSettings;
    }

    private @Nullable AdminSetting<?> setting(Identifier id) {
        AdminSetting<?> known = settings.get(id);
        return known != null ? known : registry.get(id);
    }

    @Override
    public @Nullable Facts facts(Identifier id) {
        AdminSetting<?> setting = setting(id);
        if (setting == null) {
            return null;
        }
        boolean available = setting.binding().available();
        return new Facts(!setting.writable(), actor.canModify(setting.capability()),
                available ? setting.currentText() : setting.defaultText(), setting.defaultText(), setting.applyMode(),
                moduleOf(id));
    }

    static @Nullable Module moduleOf(Identifier id) {
        String path = id.getPath();
        return path.startsWith(MODULE_PREFIX) ? ModuleIds.parse(path.substring(MODULE_PREFIX.length())) : null;
    }

    @Override
    public Check check(Identifier id, String text) {
        AdminSetting<?> setting = setting(id);
        return setting == null ? Check.refused("unknown_setting") : typed(setting.type(), text);
    }

    private static <T> Check typed(SettingType<T> type, String text) {
        T parsed = type.parse(text);
        if (parsed == null) {
            return Check.refused("invalid_value");
        }
        return type.inBounds(parsed) ? Check.ok(type.format(parsed)) : Check.refused("out_of_range");
    }

    @Override
    public Map<Module, ModuleState> moduleStates() {
        return ModuleManager.snapshot();
    }
}
