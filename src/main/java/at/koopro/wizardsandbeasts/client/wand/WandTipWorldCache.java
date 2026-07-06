package at.koopro.wizardsandbeasts.client.wand;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Written when {@link WandRenderer} receives GeckoLib {@code BonePositionListener} callbacks for a wand-tip
 * anchor bone, read by {@link WandBeamRenderer} the same or following frame (hand draw can follow world geometry).
 *
 * <p>Entries expire after {@link #MAX_AGE_MILLIS}: if the listener stops firing (wand switched away,
 * perspective mismatch, model without the bone), readers must fall back to the hand-math approximation
 * instead of anchoring beams at a stale world position.
 */
public final class WandTipWorldCache {

    /** Master wand model tip anchor (pivot Y=23, always present in wand.geo.json). */
    public static final String FX_TIP_ANCHOR_BONE = "fx_tip_anchor";
    /** Elder-wand skin tip bone (elder_wand.geo.json only). */
    public static final String WAND_TIP_BONE = "wand_tip";

    private static final long MAX_AGE_MILLIS = 250L;

    private static volatile @Nullable Vec3 lastWorldTip = null;
    private static volatile long lastWriteMillis;

    private WandTipWorldCache() {}

    public static void setWorldTip(Vec3 worldTip) {
        lastWorldTip = worldTip;
        lastWriteMillis = System.currentTimeMillis();
    }

    public static @Nullable Vec3 getWorldTipOrNull() {
        Vec3 tip = lastWorldTip;
        if (tip == null || System.currentTimeMillis() - lastWriteMillis > MAX_AGE_MILLIS) {
            return null;
        }
        return tip;
    }

    public static void clear() {
        lastWorldTip = null;
    }
}
