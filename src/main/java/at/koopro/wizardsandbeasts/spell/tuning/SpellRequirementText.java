package at.koopro.wizardsandbeasts.spell.tuning;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.core.SpellRequirement;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The text form of a {@link SpellRequirement}, so a prerequisite can be stored, synced, typed in a command
 * and edited in a text field without inventing a second requirement model.
 *
 * <pre>
 *   none                                   no prerequisite
 *   wizards_and_beasts:lumos               must know Lumos
 *   wizards_and_beasts:stupefy@proficient  must know Stupefy at Proficient or better
 *   a+b@mastered                           both clauses (the AND composite {@code SpellRequirement.allOf} builds)
 * </pre>
 *
 * Ids are written in the form the spell is registered under ({@link #canonical}), so an authored requirement and
 * the same requirement typed by hand compare equal.
 */
@NullMarked
public final class SpellRequirementText {

    public static final String NONE = "none";
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+(:[a-z0-9_./-]+)?");
    private static final int MAX_CLAUSES = 8;

    private SpellRequirementText() {}

    public static String format(SpellRequirement requirement) {
        if (requirement == SpellRequirement.NONE) {
            return NONE;
        }
        List<SpellRequirement> clauses = requirement.clauses().isEmpty() ? List.of(requirement) : requirement.clauses();
        List<String> parts = new ArrayList<>(clauses.size());
        for (SpellRequirement clause : clauses) {
            String id = clause.getPrerequisiteId();
            if (id == null) {
                continue;
            }
            Proficiency proficiency = clause.getMinProficiency();
            parts.add(canonical(id) + (proficiency == null ? "" : "@" + proficiency.name().toLowerCase(Locale.ROOT)));
        }
        return parts.isEmpty() ? NONE : String.join("+", parts);
    }

    /** Parses the text form, or returns null when it is malformed. Does not check that the spells exist. */
    public static @Nullable SpellRequirement parse(String text) {
        String trimmed = text.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty() || trimmed.equals(NONE)) {
            return SpellRequirement.NONE;
        }
        String[] parts = trimmed.split("\\+", -1);
        if (parts.length > MAX_CLAUSES) {
            return null;
        }
        List<SpellRequirement> clauses = new ArrayList<>(parts.length);
        for (String part : parts) {
            int at = part.indexOf('@');
            String id = at < 0 ? part : part.substring(0, at);
            if (!ID.matcher(id).matches()) {
                return null;
            }
            if (at < 0) {
                clauses.add(SpellRequirement.knows(canonical(id)));
                continue;
            }
            Proficiency proficiency = proficiency(part.substring(at + 1));
            if (proficiency == null) {
                return null;
            }
            clauses.add(SpellRequirement.proficiency(canonical(id), proficiency));
        }
        return SpellRequirement.allOf(clauses.toArray(SpellRequirement[]::new));
    }

    /** Every spell id a requirement names, namespaced. */
    public static List<String> prerequisiteIds(SpellRequirement requirement) {
        List<String> ids = new ArrayList<>();
        List<SpellRequirement> clauses = requirement.clauses().isEmpty() ? List.of(requirement) : requirement.clauses();
        for (SpellRequirement clause : clauses) {
            if (clause.getPrerequisiteId() != null) {
                ids.add(canonical(clause.getPrerequisiteId()));
            }
        }
        return ids;
    }

    /**
     * The id a spell is registered under, when it is registered: JSON spells are namespaced, but Java spells
     * register bare ({@code protego}), and the known-spell sets are keyed by the registered form. Unregistered ids
     * are namespaced, so a requirement naming a spell a datapack will add later still reads consistently.
     */
    public static String canonical(String id) {
        String full = namespaced(id);
        at.koopro.wizardsandbeasts.spell.core.Spell spell = at.koopro.wizardsandbeasts.spell.core.Spells.byId(full);
        if (spell == null && full.startsWith(WizardsAndBeastsMod.MODID + ":")) {
            spell = at.koopro.wizardsandbeasts.spell.core.Spells.byId(full.substring(WizardsAndBeastsMod.MODID.length() + 1));
        }
        return spell != null ? spell.getId() : full;
    }

    public static String namespaced(String id) {
        return id.indexOf(':') >= 0 ? id : WizardsAndBeastsMod.MODID + ":" + id;
    }

    private static @Nullable Proficiency proficiency(String name) {
        for (Proficiency proficiency : Proficiency.values()) {
            if (proficiency.name().equalsIgnoreCase(name)) {
                return proficiency;
            }
        }
        return null;
    }
}
