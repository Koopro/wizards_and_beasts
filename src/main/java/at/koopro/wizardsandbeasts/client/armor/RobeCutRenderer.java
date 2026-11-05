package at.koopro.wizardsandbeasts.client.armor;

import at.koopro.wizardsandbeasts.item.armor.RobeCutItem;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Every body-only robe cut added since the wardrobe ({@link RobeCutItem}): one model per cut, sheets
 * from {@code tools/wardrobe_model.py}; skirt motion, ranks and livery come from {@link WizardArmorRenderer}.
 */
public class RobeCutRenderer<R extends HumanoidRenderState & GeoRenderState>
        extends WizardArmorRenderer<RobeCutItem, R> {

    public RobeCutRenderer(String assetName) {
        super(assetName);
    }
}
