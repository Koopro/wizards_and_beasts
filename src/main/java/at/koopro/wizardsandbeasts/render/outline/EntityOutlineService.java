package at.koopro.wizardsandbeasts.render.outline;

import at.koopro.wizardsandbeasts.network.debug.EntityOutlineS2CPayload;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative record of which players carry a coloured outline.
 *
 * <p>Held in {@link PlayerScopedState} so the entry disappears when the outlined player logs out —
 * an outline should not survive its subject, and that is the one lifetime rule this state has.
 *
 * <p>Every change is broadcast to all players, and a joining player is sent the full snapshot. Both are
 * necessary: the outline is for other people to see, so a client that only ever learned about itself
 * would show nothing worth showing.
 */
@NullMarked
public final class EntityOutlineService {

    /** Packed ARGB per outlined player. Never holds {@code 0} — that is stored as absence. */
    private static final PlayerScopedState<Integer> OUTLINES =
            PlayerScopedState.create("entity-outlines");

    private EntityOutlineService() {}

    /**
     * @param rgb 24-bit {@code 0xRRGGBB}. Packed opaque here so callers cannot pass a bare RGB value and
     *            get alpha 0, which the renderer reads back as "no outline" and drops without complaint.
     */
    public static void setColor(ServerPlayer target, int rgb) {
        int argb = pack(rgb);
        OUTLINES.put(target.getUUID(), argb);
        EntityOutlineS2CPayload.broadcast(target.getUUID(), argb);
    }

    /**
     * 24-bit {@code 0xRRGGBB} to the packed opaque ARGB the renderer wants.
     *
     * <p>The alpha byte is load-bearing, not cosmetic. {@code EntityRenderState.appearsGlowing()} is
     * literally {@code outlineColor != 0}, so a colour left at alpha 0 is indistinguishable from "no
     * outline" — it would be accepted, stored, sent over the wire, and then silently drop out at the
     * last step with nothing to debug. Forcing every colour through here is what makes pure black
     * ({@code 0x000000} to {@code 0xFF000000}) a usable outline instead of an accidental no-op.
     */
    public static int pack(int rgb) {
        return ARGB.opaque(rgb & 0xFFFFFF);
    }

    /** A stable colour per player, so the same person is the same colour across sessions. */
    public static void setHashColor(ServerPlayer target) {
        setColor(target, hashColor(target.getUUID()));
    }

    public static void clear(ServerPlayer target) {
        OUTLINES.remove(target.getUUID());
        EntityOutlineS2CPayload.broadcast(target.getUUID(), 0);
    }

    /** Sends the whole table to a player who has just joined and has an empty client map. */
    public static void syncToPlayer(ServerPlayer player) {
        EntityOutlineS2CPayload.sendSnapshot(player, snapshot());
    }

    public static Map<UUID, Integer> snapshot() {
        return new HashMap<>(OUTLINES.view());
    }

    /**
     * Deterministic hue from the UUID, at full saturation so every result is a distinguishable colour
     * rather than a wash of near-greys.
     */
    public static int hashColor(UUID id) {
        int hash = id.hashCode();
        float hue = (hash & 0xFFFFFF) / (float) 0xFFFFFF;
        return Mth.hsvToRgb(hue, 0.85f, 1.0f) & 0xFFFFFF;
    }
}
