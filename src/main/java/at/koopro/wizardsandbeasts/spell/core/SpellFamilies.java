package at.koopro.wizardsandbeasts.spell.core;

/**
 * Maps spells to {@link SpellFamily} for particles and beam-adjacent visuals.
 */
public final class SpellFamilies {

    private SpellFamilies() {}

    public static SpellFamily of(Spell spell) {
        if (spell instanceof JsonSpell js) {
            return js.definition().spellFamily().orElse(SpellFamily.ARCANE);
        }
        String id = spell.getId();
        if (SpellIds.matches(id, "incendio") || SpellIds.matches(id, "bombarda") || SpellIds.matches(id, "confringo")) {
            return SpellFamily.FIRE;
        }
        if (SpellIds.matches(id, "glacius")) {
            return SpellFamily.ICE;
        }
        if (SpellIds.matches(id, "stupefy")) {
            return SpellFamily.ELECTRIC;
        }
        if (SpellIds.matches(id, "lumos") || SpellIds.matches(id, "expecto_patronum")) {
            return SpellFamily.LIGHT;
        }
        if (SpellIds.matches(id, "nox")
                || SpellIds.matches(id, "avada_kedavra")
                || SpellIds.matches(id, "crucio")
                || SpellIds.matches(id, "imperio")
                || SpellIds.matches(id, "obscurus_surge")
                || SpellIds.matches(id, "obscurus_grasp")) {
            return SpellFamily.DARK;
        }
        if (SpellIds.matches(id, "aguamenti")) {
            return SpellFamily.WATER;
        }
        return SpellFamily.ARCANE;
    }
}
