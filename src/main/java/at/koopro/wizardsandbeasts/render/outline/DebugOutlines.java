package at.koopro.wizardsandbeasts.render.outline;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;

/**
 * Permanent player outlines for {@code /wandb debug glow}. Nothing in gameplay should call this.
 *
 * <p>Untimed and unfaded; an outline lasts until cleared or until the player logs out. It is a separate
 * layer from {@link SpellOutlines}: setting or clearing one here never touches a spell's outline, and a
 * spell's outline on the same player covers this one only for as long as it lasts.
 */
@NullMarked
public final class DebugOutlines {

    private DebugOutlines() {}

    /** @param rgb 24-bit {@code 0xRRGGBB}; always drawn opaque */
    public static void setColor(ServerPlayer target, int rgb) {
        EntityOutlineService.setPermanent(target, EntityOutlineService.pack(rgb));
    }

    /** A stable colour per player, so the same person is the same colour across sessions. */
    public static void setHashColor(ServerPlayer target) {
        setColor(target, hashColor(target.getUUID()));
    }

    /** Removes the debug outline. Always sends, which also repairs a client holding a stale one. */
    public static void clear(ServerPlayer target) {
        EntityOutlineService.clearPermanent(target);
    }

    /**
     * Deterministic hue from the UUID, at full saturation so every result is a distinguishable colour rather
     * than a wash of near-greys.
     */
    public static int hashColor(UUID id) {
        int hash = id.hashCode();
        float hue = (hash & 0xFFFFFF) / (float) 0xFFFFFF;
        return Mth.hsvToRgb(hue, 0.85f, 1.0f) & 0xFFFFFF;
    }
}
