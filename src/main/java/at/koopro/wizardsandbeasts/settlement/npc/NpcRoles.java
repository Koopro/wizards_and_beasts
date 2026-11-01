package at.koopro.wizardsandbeasts.settlement.npc;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/** Every loaded {@code npc_roles} file. Server side: the client only ever sees a role's id, on the entity. */
@NullMarked
public final class NpcRoles {

    public static final String DIRECTORY = "npc_roles";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Map<Identifier, NpcRole> roles = Map.of();

    private NpcRoles() {}

    public static Map<Identifier, NpcRole> all() {
        return roles;
    }

    public static @Nullable NpcRole get(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed == null ? null : roles.get(parsed);
    }

    /** Replaces every role at once (reload, tests). */
    public static void replaceAll(Map<Identifier, NpcRole> loaded) {
        roles = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
    }

    public static final class Loader extends SimpleJsonResourceReloadListener<NpcRole> {

        public Loader() {
            super(NpcRole.CODEC, FileToIdConverter.json(DIRECTORY));
        }

        @Override
        protected void apply(Map<Identifier, NpcRole> entries, ResourceManager resourceManager,
                             ProfilerFiller profiler) {
            replaceAll(entries);
            LOGGER.info("[Settlement] Loaded {} NPC role(s): {}", entries.size(), entries.keySet());
        }
    }
}
