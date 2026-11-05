package at.koopro.wizardsandbeasts.client.armor;

import at.koopro.wizardsandbeasts.item.armor.AccessoryItem;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Every accessory cut (belts, gloves, scarves, hats): one model per cut, sheets from
 * {@code tools/wardrobe_model.py}, livery and placement handled by {@link WizardArmorRenderer}.
 */
public class AccessoryRenderer<R extends HumanoidRenderState & GeoRenderState>
        extends WizardArmorRenderer<AccessoryItem, R> {

    public AccessoryRenderer(String assetName) {
        super(assetName);
    }
}
