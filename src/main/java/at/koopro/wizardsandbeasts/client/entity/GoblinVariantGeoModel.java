package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.goblin.GoblinTellerEntity;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Picks a goblin's geometry and texture from its synced Gringotts role.
 *
 * <p>Only the model and the texture branch. The animation path deliberately stays on the base
 * asset, because {@code DefaultedGeoModel} resolves all three independently and the four roles
 * share one skeleton — so one {@code goblin_teller.animation.json} drives every variant, and the
 * bone names cannot drift apart the way a per-variant clip file would let them. Same shape as
 * {@link DisguisableBeastGeoModel}, which does this for the Kelpie's guise.
 *
 * <p>The base asset is also the plain, prop-free rig: it is what the {@code goblin_default} player
 * heritage form draws through {@code PlayerFormRig}, and a player who chose goblin heritage should
 * not arrive wearing a Gringotts uniform.
 *
 * <p>Constructed with the {@code "head"} head-bone so GeckoLib turns the skull for the entity's
 * {@code LookAtPlayerGoal} instead of the goblin staring dead ahead.
 */
public class GoblinVariantGeoModel extends DefaultedEntityGeoModel<GoblinTellerEntity> {

    public static final DataTicket<GoblinTellerEntity.Variant> TICKET_VARIANT =
            DataTicket.create("goblin_variant", GoblinTellerEntity.Variant.class);

    public GoblinVariantGeoModel(String baseAsset) {
        super(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, baseAsset), "head");
    }

    private Identifier subpath(GeoRenderState renderState) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
                renderState.getOrDefaultGeckolibData(TICKET_VARIANT, GoblinTellerEntity.Variant.CLERK)
                        .assetSubpath());
    }

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return buildFormattedModelPath(subpath(renderState));
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return buildFormattedTexturePath(subpath(renderState));
    }
}
