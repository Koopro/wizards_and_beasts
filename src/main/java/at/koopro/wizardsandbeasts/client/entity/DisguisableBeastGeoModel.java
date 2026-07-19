package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Swaps model + texture (a genuine geometry swap, not just a texture flip) based on the beast's synced
 * disguise flag — the Kelpie's tame-horse guise vs its true form. {@code getModelResource}/
 * {@code getTextureResource} both take the render state in this GeckoLib version (confirmed by reading
 * {@code DefaultedGeoModel}'s source directly), so branching here needs no live entity read at render
 * time, keeping the GeckoLib-5 render-state contract {@link ScaledBeastRenderer} already follows.
 */
public class DisguisableBeastGeoModel extends DefaultedEntityGeoModel<GenericBeastEntity> {

    public static final DataTicket<Boolean> TICKET_DISGUISED = DataTicket.create("beast_disguised", Boolean.class);

    private final Identifier disguiseModelPath;
    private final Identifier disguiseTexturePath;

    public DisguisableBeastGeoModel(Identifier trueFormAssetSubpath, Identifier disguiseAssetSubpath) {
        super(trueFormAssetSubpath);
        this.disguiseModelPath = buildFormattedModelPath(disguiseAssetSubpath);
        this.disguiseTexturePath = buildFormattedTexturePath(disguiseAssetSubpath);
    }

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return renderState.getOrDefaultGeckolibData(TICKET_DISGUISED, Boolean.FALSE)
                ? disguiseModelPath
                : super.getModelResource(renderState);
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return renderState.getOrDefaultGeckolibData(TICKET_DISGUISED, Boolean.FALSE)
                ? disguiseTexturePath
                : super.getTextureResource(renderState);
    }
}
