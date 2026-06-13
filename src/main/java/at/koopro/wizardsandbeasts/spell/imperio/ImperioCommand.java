package at.koopro.wizardsandbeasts.spell.imperio;


public enum ImperioCommand {
    FOLLOW_CASTER,
    ATTACK_NEAREST,
    STAND_STILL,
    DROP_HELD_ITEM,
    RETREAT;

    public static ImperioCommand byOrdinal(int o) {
        ImperioCommand[] v = values();
        if (o < 0 || o >= v.length) {
            return FOLLOW_CASTER;
        }
        return v[o];
    }

    public int ordinalSafe() {
        return ordinal();
    }
}
