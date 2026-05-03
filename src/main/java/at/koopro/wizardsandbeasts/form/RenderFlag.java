package at.koopro.wizardsandbeasts.form;

/**
 * Visual effect flags applied to a player form's rendering.
 * Each flag adds a distinct visual layer or effect.
 */
public enum RenderFlag {
    GLOWING_EYES("Glowing Eyes"),
    PARTICLE_AURA("Particle Aura"),
    SHADOW_OVERLAY("Shadow Overlay"),
    TRANSLUCENT("Translucent"),
    WING_LAYER("Wing Layer"),
    TAIL_LAYER("Tail Layer");

    private final String displayName;

    RenderFlag(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Encodes an EnumSet of RenderFlags as a bitmask integer. */
    public static int toBitmask(java.util.EnumSet<RenderFlag> flags) {
        int mask = 0;
        for (RenderFlag flag : flags) {
            mask |= (1 << flag.ordinal());
        }
        return mask;
    }

    /** Decodes a bitmask integer into an EnumSet of RenderFlags. */
    public static java.util.EnumSet<RenderFlag> fromBitmask(int mask) {
        java.util.EnumSet<RenderFlag> flags = java.util.EnumSet.noneOf(RenderFlag.class);
        for (RenderFlag flag : values()) {
            if ((mask & (1 << flag.ordinal())) != 0) {
                flags.add(flag);
            }
        }
        return flags;
    }
}
