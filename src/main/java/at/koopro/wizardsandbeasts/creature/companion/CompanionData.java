package at.koopro.wizardsandbeasts.creature.companion;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * The loaded companion profiles and personality traits.
 *
 * <p>Two reload listeners, one registry: {@code companions/<creature id>.json} keyed by entity id like the bond
 * profiles, and {@code companion_traits/<trait>.json} keyed by trait id. A profile whose pool names an unknown
 * trait keeps loading — the unknown trait is simply never rolled — and the data test pins that none does.
 */
@NullMarked
public final class CompanionData {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Map<Identifier, CompanionProfile> profiles = Map.of();
    private static volatile Map<String, PersonalityTrait> traits = Map.of();

    private CompanionData() {}

    public static @Nullable CompanionProfile profile(EntityType<?> type) {
        return profiles.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    public static @Nullable CompanionProfile profile(Identifier species) {
        return profiles.get(species);
    }

    public static @Nullable PersonalityTrait trait(String id) {
        return traits.get(id);
    }

    public static Map<String, PersonalityTrait> traits() {
        return traits;
    }

    public static void replaceAll(Map<Identifier, CompanionProfile> nextProfiles, Map<String, PersonalityTrait> nextTraits) {
        profiles = Map.copyOf(nextProfiles);
        traits = Map.copyOf(nextTraits);
    }

    public static final class ProfileLoader extends SimpleJsonResourceReloadListener<CompanionProfile> {
        public ProfileLoader() {
            super(CompanionProfile.CODEC, FileToIdConverter.json("companions"));
        }

        @Override
        protected void apply(Map<Identifier, CompanionProfile> entries, ResourceManager manager, ProfilerFiller profiler) {
            profiles = Map.copyOf(new HashMap<>(entries));
            LOGGER.info("[Companion] Loaded {} companion profile(s).", entries.size());
        }
    }

    public static final class TraitLoader extends SimpleJsonResourceReloadListener<PersonalityTrait> {
        public TraitLoader() {
            super(PersonalityTrait.CODEC, FileToIdConverter.json("companion_traits"));
        }

        @Override
        protected void apply(Map<Identifier, PersonalityTrait> entries, ResourceManager manager, ProfilerFiller profiler) {
            Map<String, PersonalityTrait> byId = new HashMap<>();
            entries.forEach((id, trait) -> byId.put(id.getPath(), trait));
            traits = Map.copyOf(byId);
            LOGGER.info("[Companion] Loaded {} personality trait(s).", byId.size());
        }
    }
}
