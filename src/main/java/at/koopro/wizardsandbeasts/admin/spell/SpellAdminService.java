package at.koopro.wizardsandbeasts.admin.spell;

import at.koopro.wizardsandbeasts.admin.AdminResult;
import at.koopro.wizardsandbeasts.admin.AdminSettingService;
import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellSummary;
import at.koopro.wizardsandbeasts.spell.core.CastType;
import at.koopro.wizardsandbeasts.spell.core.JsonSpell;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.SpellFamilies;
import at.koopro.wizardsandbeasts.spell.core.SpellProperties;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.def.SpellDefinition;
import at.koopro.wizardsandbeasts.spell.effect.SpellEffectEntry;
import at.koopro.wizardsandbeasts.spell.tuning.SpellAvailability;
import at.koopro.wizardsandbeasts.spell.tuning.SpellRequirementText;
import at.koopro.wizardsandbeasts.spell.tuning.SpellTuning;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The Magic section's server side: what the spell browser lists, what a spell's detail page shows, and the
 * batch resets. Reads only; every write is a call into {@link AdminSettingService} with a
 * {@link SpellSettingIds spell setting id}, so there is no second path by which a spell value changes.
 *
 * <p>Detail facts are read off the live spell and its definition — the same objects the cast pipeline reads —
 * and never restated. A value the architecture does not have (a mana cost, a per-spell heritage list) is not
 * shown as if it did.
 */
@NullMarked
public final class SpellAdminService {

    private static final String FACT = "admin.wizards_and_beasts.spell_fact.";

    private SpellAdminService() {}

    // ── browser ──

    public static List<AdminSpellSummary> list() {
        List<AdminSpellSummary> rows = new ArrayList<>();
        for (Spell spell : Spells.all()) {
            rows.add(summary(spell));
        }
        return rows;
    }

    public static AdminSpellSummary summary(Spell spell) {
        String id = spell.getId();
        SpellProperties props = spell.getProperties();
        CastType castType = props == null ? CastType.SELF : props.getCastType();
        return new AdminSpellSummary(
                id,
                spell.getDisplayName(),
                spell.getCategory().name(),
                castType.name(),
                SpellAvailability.legalClass(spell).name(),
                SpellAvailability.castAllowed(spell),
                SpellTuning.enabled(id),
                SpellAvailability.isDark(spell),
                SpellAvailability.isUnforgivable(spell),
                spell.isImplemented(),
                SpellTuning.local().overrides().containsKey(id),
                SpellRequirementText.format(spell.getRequirement()),
                spell.getColor(),
                oneLine(spell, castType));
    }

    /**
     * The browser's short description, composed from values the spell really has. The mod ships no prose
     * description for spells, and inventing 150 of them for an admin list would be the wrong place to start.
     */
    private static String oneLine(Spell spell, CastType castType) {
        StringBuilder out = new StringBuilder(castType.name().toLowerCase(Locale.ROOT).replace('_', ' '));
        if (spell.getBaseDamage() > 0.0f) {
            out.append(" · ").append(number(spell.getBaseDamage())).append(" dmg");
        }
        out.append(" · ").append(number(spell.getBaseCooldownTicks() / 20.0f)).append("s cd");
        SpellProperties props = spell.getProperties();
        if (props != null && props.getRange() > 0.0f) {
            out.append(" · ").append(number(props.getRange())).append(" range");
        }
        return out.toString();
    }

    // ── detail ──

    /** Read-only facts about the spell, in display order. */
    public static List<AdminSpellFact> facts(Spell spell) {
        List<AdminSpellFact> facts = new ArrayList<>();
        SpellProperties props = spell.getProperties();
        SpellDefinition def = spell instanceof JsonSpell json ? json.definition() : null;

        fact(facts, "id", spell.getId());
        fact(facts, "category", spell.getCategory().name());
        fact(facts, "cast_mode", props == null ? "—" : props.getCastType().name());
        fact(facts, "family", SpellFamilies.of(spell).name());
        fact(facts, "legal_class", SpellAvailability.legalClass(spell).name());
        fact(facts, "implemented", spell.isImplemented() ? "yes" : "no");
        fact(facts, "cooldown", ticks(spell.getBaseCooldownTicks()) + authoredNote(spell.getBaseCooldownTicks(), spell.getAuthoredCooldownTicks()));
        fact(facts, "cost", "none");
        fact(facts, "damage", number(spell.getBaseDamage()) + authoredNote(spell.getBaseDamage(), spell.getAuthoredDamage()));
        if (props != null && props.getRange() > 0.0f) {
            SpellProperties authored = spell.getAuthoredProperties();
            fact(facts, "range", number(props.getRange()) + (authored == null ? "" : authoredNote(props.getRange(), authored.getRange())));
        }
        int duration = def != null ? def.baseEffectDurationTicks() : spell.getBaseEffectDurationTicks();
        if (duration > 0) {
            fact(facts, "duration", ticks(duration));
        }
        if (props != null && props.getKnockbackStrength() != 0.0f) {
            fact(facts, "knockback", number(props.getKnockbackStrength()));
        }
        fact(facts, "delivery", delivery(spell, props, def));
        if (props != null && props.explodes()) {
            fact(facts, "explosion", number(props.getExplosionPower()) + (props.explosionBreaksBlocks() ? ", breaks blocks" : ""));
        }
        if (def != null && !def.targetEffects().isEmpty()) {
            fact(facts, "target_effects", def.targetEffects().stream()
                    .map(e -> e.id().getPath() + " " + ticks(e.duration()) + (e.amplifier() > 0 ? " ×" + (e.amplifier() + 1) : ""))
                    .collect(Collectors.joining(", ")));
        }
        if (def != null && !def.effectComponents().isEmpty()) {
            fact(facts, "components", def.effectComponents().stream()
                    .map(SpellEffectEntry::component).map(c -> c.type().getSerializedName())
                    .collect(Collectors.joining(", ")));
        }
        fact(facts, "prerequisite", SpellRequirementText.format(spell.getRequirement()));
        fact(facts, "required_skill", nonBlank(spell.getEffectiveRequiredSkillId()));
        fact(facts, "required_profession", nonBlank(spell.getRequiredProfessionId()));
        if (spell.getMasterySourceSpellId() != null && spell.getRequiredMasteryTier() != null) {
            fact(facts, "mastery", spell.getRequiredMasteryTier().name().toLowerCase(Locale.ROOT) + " in " + spell.getMasterySourceSpellId());
        }
        translated(facts, "heritage", ObscurialRules.isObscurialAbility(spell)
                ? FACT + "heritage.obscurial_ability"
                : ObscurialRules.isDarkFormOnlySpell(spell) ? FACT + "heritage.dark_form" : FACT + "heritage.any_wand_user");
        translated(facts, "wand", FACT + "wand.bonded");
        translated(facts, "learning", spell.getCategory() == SpellCategory.DARK_ARTS
                ? FACT + "learning.dark_arts_module" : FACT + "learning.normal");
        translated(facts, "authority", FACT + "authority.server");
        if (def != null && def.castTiming().isPresent()) {
            fact(facts, "animation", ticks(def.castTiming().get().ticks()));
        }
        if (def != null && !def.gampDomains().isEmpty()) {
            fact(facts, "gamp", def.gampDomains().stream().map(Enum::name).collect(Collectors.joining(", ")));
        }
        return facts;
    }

    /** The editable values of the spell as descriptors for {@code viewer} — the same type every setting row uses. */
    public static List<AdminSettingDescriptor> settings(Spell spell, AdminContext viewer) {
        List<AdminSettingDescriptor> out = new ArrayList<>();
        for (SpellProperty property : SpellSettingProvider.applicable(spell)) {
            AdminSetting<?> setting = AdminSettings.registry().get(SpellSettingIds.of(spell.getId(), property));
            if (setting != null) {
                out.add(AdminSettingDescriptor.of(setting, viewer));
            }
        }
        return out;
    }

    // ── batch resets ──

    /** Resets every overridden value of one spell. One result per value touched. */
    public static List<AdminResult> resetSpell(AdminContext actor, Spell spell, boolean confirmed) {
        List<AdminResult> results = new ArrayList<>();
        AdminSettingService service = AdminSettings.service();
        for (SpellProperty property : SpellSettingProvider.applicable(spell)) {
            AdminSetting<?> setting = AdminSettings.registry().get(SpellSettingIds.of(spell.getId(), property));
            if (setting != null && !setting.isDefault()) {
                results.add(service.reset(actor, setting.id(), confirmed));
            }
        }
        return results;
    }

    /** Resets every spell of one {@link SpellCategory}. */
    public static List<AdminResult> resetCategory(AdminContext actor, SpellCategory category, boolean confirmed) {
        List<AdminResult> results = new ArrayList<>();
        for (Spell spell : Spells.all()) {
            if (spell.getCategory() == category) {
                results.addAll(resetSpell(actor, spell, confirmed));
            }
        }
        return results;
    }

    // ── helpers ──

    private static String delivery(Spell spell, @Nullable SpellProperties props, @Nullable SpellDefinition def) {
        if (props == null) {
            return "custom";
        }
        return switch (props.getCastType()) {
            case PROJECTILE -> "projectile, speed " + number(def != null ? def.projectileSpeed() : spell.getProjectileSpeed())
                    + (spell.isUnblockable() ? ", unblockable" : "");
            case BEAM_CHANNEL -> "held beam, effect every tick while on target";
            case BEAM_LETHAL -> "held beam, lethal on contact";
            case TARGETED -> "instant, first target in range";
            case CONE -> "instant, everything in a cone";
            case SELF -> "self";
        };
    }

    private static void fact(List<AdminSpellFact> facts, String name, String value) {
        facts.add(new AdminSpellFact(FACT + name, value, false));
    }

    private static void translated(List<AdminSpellFact> facts, String name, String valueKey) {
        facts.add(new AdminSpellFact(FACT + name, valueKey, true));
    }

    private static String authoredNote(float now, float authored) {
        return Float.compare(now, authored) == 0 ? "" : " (authored " + number(authored) + ")";
    }

    private static String authoredNote(int now, int authored) {
        return now == authored ? "" : " (authored " + ticks(authored) + ")";
    }

    private static String ticks(int ticks) {
        return ticks + "t (" + number(ticks / 20.0f) + "s)";
    }

    private static String number(float value) {
        return java.math.BigDecimal.valueOf(Double.parseDouble(Float.toString(value))).stripTrailingZeros().toPlainString();
    }

    private static String nonBlank(@Nullable String value) {
        return value == null || value.isBlank() ? "none" : value;
    }
}
