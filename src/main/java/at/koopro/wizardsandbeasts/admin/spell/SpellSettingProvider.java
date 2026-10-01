package at.koopro.wizardsandbeasts.admin.spell;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.config.AdminSettingProvider;
import at.koopro.wizardsandbeasts.admin.config.SettingBinding;
import at.koopro.wizardsandbeasts.admin.config.SettingKind;
import at.koopro.wizardsandbeasts.admin.config.SettingType;
import at.koopro.wizardsandbeasts.admin.config.SettingTypes;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellProperties;
import at.koopro.wizardsandbeasts.spell.core.SpellRequirement;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.tuning.SpellAvailability;
import at.koopro.wizardsandbeasts.spell.tuning.SpellOverride;
import at.koopro.wizardsandbeasts.spell.tuning.SpellRequirementText;
import at.koopro.wizardsandbeasts.spell.tuning.SpellTuning;
import at.koopro.wizardsandbeasts.spell.tuning.SpellTuningService;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Resolves {@code spell/<ns>/<path>/<property>} setting ids into live {@link AdminSetting}s bound to the
 * world's {@link SpellOverride}s.
 *
 * <p>Built fresh on every lookup from the live spell registry, so a {@code /reload} that adds or removes spells
 * is reflected at once and a setting never outlives its spell. Each setting carries the spell's authored value
 * as its default, its own bounds and rules, and a danger rule — and then goes through
 * {@code AdminSettingService} like any other setting: authorisation, parsing, bounds, rules, confirmation,
 * history and broadcast all happen there, exactly once.
 */
@NullMarked
public final class SpellSettingProvider implements AdminSettingProvider {

    /** Every spell's applicable values, for profiles and snapshots. */
    @Override
    public java.util.Collection<net.minecraft.resources.Identifier> enumerate(
            net.minecraft.server.@org.jspecify.annotations.Nullable MinecraftServer server) {
        java.util.List<net.minecraft.resources.Identifier> out = new java.util.ArrayList<>();
        for (at.koopro.wizardsandbeasts.spell.core.Spell spell : at.koopro.wizardsandbeasts.spell.core.Spells.all()) {
            for (SpellProperty property : applicable(spell)) {
                out.add(SpellSettingIds.of(spell.getId(), property));
            }
        }
        return out;
    }

    /** An hour. Longer than any authored cooldown, short enough that a typo is not a permanent lockout. */
    private static final int MAX_COOLDOWN_TICKS = 72_000;
    private static final double MAX_DAMAGE = 1000.0;
    private static final double MIN_RANGE = 0.5;
    private static final double MAX_RANGE = 128.0;
    private static final int MAX_REQUIREMENT_DEPTH = 32;
    private static final Pattern SKILL_ID = Pattern.compile("[a-z0-9_.:/-]*");

    @Override
    public @Nullable AdminSetting<?> resolve(Identifier id) {
        SpellSettingIds.Parsed parsed = SpellSettingIds.parse(id);
        if (parsed == null) {
            return null;
        }
        Spell spell = SpellSettingIds.resolveSpell(parsed.spellId());
        if (spell == null || !applicable(spell).contains(parsed.property())) {
            return null;
        }
        return build(id, spell, parsed.property());
    }

    /** The properties that mean something for this spell: no damage knob for a spell that deals none. */
    public static List<SpellProperty> applicable(Spell spell) {
        List<SpellProperty> out = new ArrayList<>(List.of(SpellProperty.ENABLED, SpellProperty.COOLDOWN_TICKS));
        if (spell.getAuthoredDamage() > 0.0f) {
            out.add(SpellProperty.DAMAGE);
        }
        SpellProperties authored = spell.getAuthoredProperties();
        if (authored != null && authored.getRange() > 0.0f) {
            out.add(SpellProperty.RANGE);
        }
        out.add(SpellProperty.PREREQUISITE);
        out.add(SpellProperty.REQUIRED_SKILL);
        return out;
    }

    public static AdminCategory categoryOf(Spell spell) {
        return SpellAvailability.isDark(spell) ? AdminCategory.DARK_ARTS : AdminCategory.MAGIC;
    }

    private static AdminSetting<?> build(Identifier id, Spell spell, SpellProperty property) {
        String spellId = spell.getId();
        AdminCategory category = categoryOf(spell);
        return switch (property) {
            case ENABLED -> AdminSetting.builder(id, SettingTypes.bool(),
                            new OverrideBinding<>(spellId, Boolean.TRUE, SpellOverride::enabled, SpellOverride::withEnabled))
                    .category(category)
                    // Switching an Unforgivable back on arms it for every player.
                    .dangerRule((previous, candidate, authored, setting) ->
                            candidate && !previous && SpellAvailability.isUnforgivable(spell) ? setting.warningKey() : null)
                    .build();
            case COOLDOWN_TICKS -> AdminSetting.builder(id, SettingTypes.integer(0, MAX_COOLDOWN_TICKS),
                            new OverrideBinding<>(spellId, spell.getAuthoredCooldownTicks(),
                                    SpellOverride::cooldownTicks, SpellOverride::withCooldownTicks))
                    .category(category)
                    .dangerRule((previous, candidate, authored, setting) ->
                            candidate < authored / 2 ? setting.warningKey() : null)
                    .build();
            case DAMAGE -> AdminSetting.builder(id, SettingTypes.decimal(0.0, MAX_DAMAGE, 0.5),
                            new OverrideBinding<>(spellId, shortest(spell.getAuthoredDamage()),
                                    o -> o.damage().map(SpellSettingProvider::shortest),
                                    (o, v) -> o.withDamage(v.map(Double::floatValue))))
                    .category(category)
                    .dangerRule((previous, candidate, authored, setting) ->
                            candidate > 2.0 * authored ? setting.warningKey() : null)
                    .build();
            case RANGE -> AdminSetting.builder(id, SettingTypes.decimal(MIN_RANGE, MAX_RANGE, 0.5),
                            new OverrideBinding<>(spellId, shortest(Objects.requireNonNull(spell.getAuthoredProperties()).getRange()),
                                    o -> o.range().map(SpellSettingProvider::shortest),
                                    (o, v) -> o.withRange(v.map(Double::floatValue))))
                    .category(category)
                    .dangerRule((previous, candidate, authored, setting) ->
                            candidate > 2.0 * authored ? setting.warningKey() : null)
                    .build();
            case PREREQUISITE -> AdminSetting.builder(id, RequirementTextType.INSTANCE,
                            new OverrideBinding<>(spellId, SpellRequirementText.format(spell.getAuthoredRequirement()),
                                    SpellOverride::requirement, SpellOverride::withRequirement))
                    .category(category)
                    .validator((candidate, registry) -> checkPrerequisite(spell, candidate))
                    // Removing a prerequisite someone authored bypasses that step of progression.
                    .dangerRule((previous, candidate, authored, setting) ->
                            SpellRequirementText.NONE.equals(candidate) && !SpellRequirementText.NONE.equals(authored)
                                    ? setting.warningKey() : null)
                    .build();
            case REQUIRED_SKILL -> AdminSetting.builder(id, SettingTypes.text(128, SKILL_ID),
                            new OverrideBinding<>(spellId, Objects.requireNonNullElse(spell.getRequiredSkillId(), ""),
                                    SpellOverride::requiredSkill, SpellOverride::withRequiredSkill))
                    .category(category)
                    .validator((candidate, registry) -> candidate.isEmpty() || SkillTrees.byId(candidate) != null
                            ? null : "admin.wizards_and_beasts.conflict.unknown_skill")
                    .dangerRule((previous, candidate, authored, setting) ->
                            candidate.isEmpty() && !authored.isEmpty() ? setting.warningKey() : null)
                    .build();
        };
    }

    /**
     * Every spell the prerequisite names must exist, and following prerequisites from them must never lead
     * back here — a cycle would make the spell unlearnable for everyone.
     */
    static @Nullable String checkPrerequisite(Spell spell, String text) {
        SpellRequirement requirement = SpellRequirementText.parse(text);
        if (requirement == null) {
            return "admin.wizards_and_beasts.conflict.prerequisite_malformed";
        }
        Deque<String> frontier = new ArrayDeque<>();
        for (String id : SpellRequirementText.prerequisiteIds(requirement)) {
            Spell prerequisite = Spells.byId(id);
            if (prerequisite == null) {
                return "admin.wizards_and_beasts.conflict.prerequisite_unknown";
            }
            frontier.add(prerequisite.getId());
        }
        Set<String> seen = new HashSet<>();
        int steps = 0;
        while (!frontier.isEmpty()) {
            String id = frontier.poll();
            if (id.equals(spell.getId())) {
                return "admin.wizards_and_beasts.conflict.prerequisite_cycle";
            }
            if (!seen.add(id) || ++steps > MAX_REQUIREMENT_DEPTH * 8) {
                continue;
            }
            Spell next = Spells.byId(id);
            if (next != null) {
                for (String further : SpellRequirementText.prerequisiteIds(next.getRequirement())) {
                    Spell resolved = Spells.byId(further);
                    frontier.add(resolved != null ? resolved.getId() : further);
                }
            }
        }
        return null;
    }

    /** A float as the shortest double that prints like it: 0.1f is 0.1, not 0.10000000149. */
    private static double shortest(float value) {
        return Double.parseDouble(Float.toString(value));
    }

    /**
     * A per-spell value stored as an optional override: absent means "as authored". Setting the authored value
     * removes the override rather than pinning a copy of it, so a later datapack change to the authored value
     * is not silently masked.
     */
    private record OverrideBinding<T>(String spellId, T authored,
                                      Function<SpellOverride, Optional<T>> read,
                                      BiFunction<SpellOverride, Optional<T>, SpellOverride> write)
            implements SettingBinding<T> {

        @Override
        public T get() {
            return read.apply(SpellTuning.local().override(spellId)).orElse(authored);
        }

        @Override
        public void set(T value) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                throw new IllegalStateException("no server to store a spell override on");
            }
            Optional<T> stored = value.equals(authored) ? Optional.empty() : Optional.of(value);
            SpellTuningService.update(server, spellId, override -> write.apply(override, stored));
        }

        @Override
        public T defaultValue() {
            return authored;
        }

        @Override
        public boolean available() {
            return ServerLifecycleHooks.getCurrentServer() != null && Spells.byId(spellId) != null;
        }
    }

    /** The text form of a prerequisite, canonicalised on parse so typed and authored forms compare equal. */
    enum RequirementTextType implements SettingType<String> {
        INSTANCE;

        @Override
        public SettingKind kind() {
            return SettingKind.STRING;
        }

        @Override
        public @Nullable String parse(String raw) {
            SpellRequirement requirement = SpellRequirementText.parse(raw);
            return requirement == null ? null : SpellRequirementText.format(requirement);
        }

        @Override
        public boolean inBounds(String value) {
            return value.length() <= 256;
        }

        @Override
        public String format(String value) {
            return value;
        }

        @Override
        public int maxLength() {
            return 256;
        }
    }
}
