package at.koopro.wizardsandbeasts.client.armor;

import at.koopro.wizardsandbeasts.item.armor.RobeCutItem;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * A wardrobe cut with a hood ({@code RitualRobeItem}): {@link HoodedArmorRenderer}'s hood up/down and head bone, for any
 * cut named by its asset, so a new hooded cut needs a model and no renderer of its own.
 */
public class HoodedRobeCutRenderer<R extends HumanoidRenderState & GeoRenderState>
        extends HoodedArmorRenderer<RobeCutItem, R> {

    public HoodedRobeCutRenderer(String assetName) {
        super(assetName);
    }
}
