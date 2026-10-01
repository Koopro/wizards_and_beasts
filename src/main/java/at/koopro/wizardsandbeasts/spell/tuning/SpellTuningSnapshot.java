package at.koopro.wizardsandbeasts.spell.tuning;

import at.koopro.wizardsandbeasts.spell.core.SpellRequirement;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * One immutable view of the server's spell administration state: every per-spell {@link SpellOverride} and
 * the global {@link Globals}. Published whole, so a reader never sees half of an administrator's change.
 *
 * <p>Requirement overrides are parsed once here, at construction, because {@code Spell#getRequirement} is
 * read on every cast and every learning check.
 */
@NullMarked
public final class SpellTuningSnapshot {

    /**
     * Server-wide spell rules. The multipliers default to 1 and the switches to the behaviour the mod had
     * before they existed, so a server that never touches them plays exactly as it did.
     *
     * @param damageMultiplier   scales every spell's base damage
     * @param cooldownMultiplier scales every spell's base cooldown
     * @param rangeMultiplier    scales every targeted, cone and beam spell's range
     * @param allowUnforgivables false refuses every spell the spell law classes as Unforgivable
     * @param blockDamage        false stops spell explosions breaking blocks and spells setting blocks alight
     */
    public record Globals(float damageMultiplier, float cooldownMultiplier, float rangeMultiplier,
                          boolean allowUnforgivables, boolean blockDamage) {
        public static final Globals DEFAULT = new Globals(1.0f, 1.0f, 1.0f, true, true);
    }

    public static final SpellTuningSnapshot EMPTY = new SpellTuningSnapshot(Map.of(), Globals.DEFAULT);

    private final Map<String, SpellOverride> overrides;
    private final Globals globals;
    private final Map<String, SpellRequirement> requirements;

    public SpellTuningSnapshot(Map<String, SpellOverride> overrides, Globals globals) {
        Map<String, SpellOverride> copy = new HashMap<>();
        Map<String, SpellRequirement> parsed = new HashMap<>();
        overrides.forEach((id, override) -> {
            if (override.isEmpty()) {
                return;
            }
            copy.put(id, override);
            override.requirement().ifPresent(text -> {
                SpellRequirement requirement = SpellRequirementText.parse(text);
                if (requirement != null) {
                    parsed.put(id, requirement);
                }
            });
        });
        this.overrides = Map.copyOf(copy);
        this.globals = globals;
        this.requirements = Map.copyOf(parsed);
    }

    public Map<String, SpellOverride> overrides() {
        return overrides;
    }

    public Globals globals() {
        return globals;
    }

    public SpellOverride override(String spellId) {
        return overrides.getOrDefault(spellId, SpellOverride.NONE);
    }

    public @Nullable SpellRequirement requirementOverride(String spellId) {
        return requirements.get(spellId);
    }

    public SpellTuningSnapshot withOverrides(Map<String, SpellOverride> next) {
        return new SpellTuningSnapshot(next, globals);
    }

    public SpellTuningSnapshot withGlobals(Globals next) {
        return new SpellTuningSnapshot(overrides, next);
    }
}
