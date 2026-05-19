package at.koopro.wizardsandbeasts.client.wand;

import net.minecraft.world.phys.Vec3;

/**
 * Written when {@link WandRenderer} receives GeckoLib {@code BonePositionListener} callbacks for {@code wand_tip},
 * read by {@link WandBeamRenderer} the same or following frame (hand draw can follow world geometry).
 */
public final class WandTipWorldCache {

    public static final String WAND_TIP_BONE = "wand_tip";

    private static volatile Vec3 lastWorldTip = null;

    private WandTipWorldCache() {}

    public static void setWorldTip(Vec3 worldTip) {
        lastWorldTip = worldTip;
    }

    public static Vec3 getWorldTipOrNull() {
        return lastWorldTip;
    }

    public static void clear() {
        lastWorldTip = null;
    }
}
