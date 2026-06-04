package at.koopro.wizardsandbeasts.module;

import java.util.EnumMap;
import java.util.Map;

/**
 * Central enable/preview flags for gameplay modules. WANDS defaults to enabled;
 * PREVIEW adds a {@code [Preview]} prefix to select player feedback (see wand resonance helpers).
 */
public final class ModuleManager {
    public enum State {
        DISABLED,
        ENABLED,
        PREVIEW
    }

    private static final Map<Module, State> STATES = new EnumMap<>(Module.class);

    static {
        STATES.put(Module.WANDS, State.ENABLED);
        STATES.put(Module.WANDS_AND_SPELLS, State.ENABLED);
        STATES.put(Module.SKILL_TREES, State.ENABLED);
        STATES.put(Module.PROFICIENCY, State.PREVIEW);
        STATES.put(Module.DARK_ARTS, State.DISABLED);
        STATES.put(Module.PLAYER_ABILITIES, State.PREVIEW);
        STATES.put(Module.CREATURES, State.DISABLED);
        STATES.put(Module.BESTIARY, State.PREVIEW);
        STATES.put(Module.BROOM_FLIGHT, State.ENABLED);
        STATES.put(Module.POCKET_DIMENSIONS, State.ENABLED);
        STATES.put(Module.OWLS, State.PREVIEW);
        // Azkaban worldgen (crag + NBT-template fortress) is a Beta v0.3 feature.
        // PREVIEW keeps it generating + flagged at alpha; set DISABLED to stop
        // generation entirely (registration of type/biome/structure is unaffected).
        STATES.put(Module.AZKABAN, State.PREVIEW);
        STATES.put(Module.FLOO_NETWORK, State.DISABLED);
        STATES.put(Module.CHARACTER_SHEET, State.ENABLED);
        STATES.put(Module.PLAYER_STATS, State.PREVIEW);
    }

    private ModuleManager() {
    }

    public static void setState(Module module, State state) {
        STATES.put(module, state == null ? State.DISABLED : state);
    }

    public static boolean isEnabled(Module module) {
        State s = STATES.getOrDefault(module, State.DISABLED);
        return s == State.ENABLED || s == State.PREVIEW;
    }

    public static boolean isPreview(Module module) {
        return STATES.getOrDefault(module, State.DISABLED) == State.PREVIEW;
    }
}
