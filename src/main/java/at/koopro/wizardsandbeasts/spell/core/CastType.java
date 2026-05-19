package at.koopro.wizardsandbeasts.spell.core;

import net.minecraft.util.StringRepresentable;

public enum CastType implements StringRepresentable {
    PROJECTILE,
    SELF,
    CONE,
    TARGETED,
    /** Held beam: lethal when the crosshair ray is on a living target (server-side, {@link WandBeamChannelLogic}). */
    BEAM_LETHAL,
    /** Held beam: target effects each tick while the ray stays on a target ({@link WandBeamChannelLogic}). */
    BEAM_CHANNEL;

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }
}
