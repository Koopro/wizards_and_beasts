package at.koopro.wizardsandbeasts.client.armor;

import at.koopro.wizardsandbeasts.item.armor.DeathEaterMaskItem;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Death Eater mask. Head slot only, so the model needs no bone but {@code armorHead}.
 *
 * <p>One sculpt, six skins — the castings differ in chasing, not in shape:
 *
 * <pre>
 *   model      geckolib/models/armor/death_eater_mask.geo.json
 *   animation  geckolib/animations/armor/death_eater_mask.animation.json
 *   texture    textures/armor/death_eater_mask.png          (plain issue, variant 0)
 *              textures/armor/death_eater_mask_1.png        (classic)
 *              textures/armor/death_eater_mask_2.png        (serpentine)
 *              textures/armor/death_eater_mask_3.png        (screaming)
 *              textures/armor/death_eater_mask_4.png        (horned / ridged)
 *              textures/armor/death_eater_mask_5.png        (cracked)
 * </pre>
 *
 * <p>The horned casting is chased into its texture rather than modelled. True protrusions
 * would need that variant to carry its own {@code .geo.json} — a one-line change here, passing
 * a variant-specific asset name instead of the shared one — but it costs the
 * six-skins-one-sculpt arrangement, so it is not done by default.
 *
 * <p>The redirect is {@code withAltTexture} on the shared model rather than a per-frame lookup: the
 * casting is fixed by which item you are wearing, and each item builds its renderer once, so there
 * is nothing left to decide at render time. A skin that varied by <em>wearer</em> could not use this
 * — see {@link StudentRobeRenderer} for that case.
 */
public class DeathEaterMaskRenderer<R extends HumanoidRenderState & GeoRenderState>
        extends WizardArmorRenderer<DeathEaterMaskItem, R> {

    public static final String ASSET_NAME = "death_eater_mask";

    public DeathEaterMaskRenderer(int variant) {
        super(new WizardArmorGeoModel<DeathEaterMaskItem>(ASSET_NAME)
                .withAltTexture(WizardArmorGeoModel.texture(textureName(variant))));
    }

    /**
     * {@code death_eater_mask} for the base casting, {@code death_eater_mask_<n>} for the rest —
     * the same names the items are registered under, so a texture is findable from an item id.
     */
    public static String textureName(int variant) {
        return variant == DeathEaterMaskItem.BASE_VARIANT ? ASSET_NAME : ASSET_NAME + "_" + variant;
    }
}
