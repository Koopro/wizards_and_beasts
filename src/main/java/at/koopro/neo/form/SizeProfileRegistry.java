package at.koopro.neo.form;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static registry of all {@link SizeProfile} instances, keyed by profile ID.
 * <p>
 * To add a new profile, call {@link #register(SizeProfile)} during mod initialization
 * or add it to the static block below.
 */
public final class SizeProfileRegistry {

    private static final Map<String, SizeProfile> PROFILES = new LinkedHashMap<>();

    static {
        // Wizardkind
        register(new SizeProfile("wizardkind_default", 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f));

        // Werewolf
        register(new SizeProfile("werewolf_human", 1.0f, 1.05f, 1.0f, 0.0f, 0.1f, 0.0f));
        register(new SizeProfile("werewolf_wolf", 1.2f, 1.3f, 1.2f, 0.5f, 0.3f, 0.5f));

        // Obscurial
        register(new SizeProfile("obscurial_human", 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f));
        register(new SizeProfile("obscurial_dark", 1.3f, 1.8f, 1.3f, 1.0f, 0.5f, 0.5f));

        // Goblin
        register(new SizeProfile("goblin_default", 0.8f, 0.65f, 0.8f, -0.5f, 0.0f, 0.0f));

        // House-Elf
        register(new SizeProfile("house_elf_default", 0.7f, 0.55f, 0.7f, -0.5f, 0.0f, 0.0f));

        // Veela
        register(new SizeProfile("veela_human", 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f));
        register(new SizeProfile("veela_harpy", 1.1f, 1.1f, 1.1f, 0.5f, 0.1f, 0.0f));

        // Giant
        register(new SizeProfile("giant_full", 1.8f, 3.5f, 1.8f, 2.0f, 0.6f, 1.5f));
        register(new SizeProfile("giant_half", 1.3f, 1.6f, 1.3f, 1.0f, 0.3f, 0.5f));

        // Centaur (non-uniform: wider and deeper than tall)
        register(new SizeProfile("centaur_default", 1.4f, 1.3f, 1.8f, 0.5f, 0.2f, 1.0f));

        // Vampire
        register(new SizeProfile("vampire_default", 1.0f, 1.0f, 1.0f, 0.0f, 0.1f, 0.0f));
        register(new SizeProfile("vampire_bat", 0.4f, 0.3f, 0.4f, -1.0f, 0.0f, 0.0f));

        // Merpeople
        register(new SizeProfile("merpeople_land", 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f));
        register(new SizeProfile("merpeople_water", 1.0f, 0.9f, 1.0f, 0.5f, 0.1f, 0.0f));
    }

    public static void register(SizeProfile profile) {
        PROFILES.put(profile.id(), profile);
    }

    @Nullable
    public static SizeProfile get(String id) {
        return PROFILES.get(id);
    }

    public static SizeProfile getOrDefault(String id) {
        return PROFILES.getOrDefault(id, SizeProfile.DEFAULT);
    }

    public static Map<String, SizeProfile> getAll() {
        return Collections.unmodifiableMap(PROFILES);
    }

    private SizeProfileRegistry() {}
}
