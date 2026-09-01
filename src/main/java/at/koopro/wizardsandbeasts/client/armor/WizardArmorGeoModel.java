package at.koopro.wizardsandbeasts.client.armor;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.model.DefaultedGeoModel;

/**
 * The one place the {@code armor/} asset layout is decided.
 *
 * <p>{@link DefaultedGeoModel} derives all three paths from the subtype string, so an asset name of
 * {@code student_robe} resolves to:
 *
 * <pre>
 *   assets/wizards_and_beasts/geckolib/models/armor/student_robe.geo.json
 *   assets/wizards_and_beasts/geckolib/animations/armor/student_robe.animation.json
 *   assets/wizards_and_beasts/textures/armor/student_robe.png
 * </pre>
 *
 * <p>An asset name is a <em>set</em>, not a piece: chest, legs and boots are three items drawn from
 * one model file, and {@code GeoArmorRenderer} picks which bones each contributes from the slot it
 * occupies. The bones it looks for are fixed — {@code armorHead}, {@code armorBody},
 * {@code armorLeftArm}, {@code armorRightArm}, {@code armorLeftLeg}, {@code armorRightLeg},
 * {@code armorLeftBoot}, {@code armorRightBoot} — and a model missing one simply contributes
 * nothing for that slot rather than failing, so a misnamed bone is silent.
 *
 * <p>Variants that differ only in skin ride {@link DefaultedGeoModel#withAltTexture} off the same
 * model, which is how the five Death Eater mask castings share one sculpt. A variant that has to be
 * chosen from the <em>wearer</em> rather than the item — house-coloured student robes — cannot use
 * that, because the path is fixed at construction; it needs {@code getTextureResource(GeoRenderState)}
 * overridden instead, off a house value put into the render state.
 */
public class WizardArmorGeoModel<T extends Item & GeoItem> extends DefaultedGeoModel<T> {

    public WizardArmorGeoModel(String assetName) {
        super(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, assetName));
    }

    @Override
    protected String subtype() {
        return "armor";
    }

    /** {@code wizards_and_beasts:textures/armor/<name>.png} — the form {@code withAltTexture} wants. */
    public static Identifier texture(String assetName) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, assetName);
    }
}
