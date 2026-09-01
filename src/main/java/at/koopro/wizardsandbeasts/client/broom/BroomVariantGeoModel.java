package at.koopro.wizardsandbeasts.client.broom;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.broom.BroomAssets;
import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Picks a broom's geometry, texture and animation file from its definition.
 *
 * <p>All three branch, and all three default back to the shared {@code broom} asset, so a definition
 * naming none of them is the rig every broom has always drawn. `DefaultedGeoModel` resolves the
 * three paths independently, which is what lets a broom swap its texture without also needing its
 * own geometry — the common case by a wide margin.
 *
 * <p>What the sheet carries is colour, and colour is what survives distance. The slot system gave
 * the brooms different silhouettes, but every one of them drew {@code broom.png}, so past the range
 * where an outline reads they were the same object. Ash, walnut, ebony and aged oak are still
 * telling each other apart when the shape has blurred to a line.
 *
 * <h2>Replacing the geometry is the escape hatch, not the route</h2>
 * {@link BroomRenderer} shows one slot variant by hiding every other variant it knows by name. A
 * replacement geometry carrying none of those bones has nothing hidden and nothing shown, which is
 * harmless but means the slot system no longer applies to it; one carrying some of them gets a
 * partial rig. {@code broom_body} must survive in any case or the broom will not tilt. Prefer
 * assembling a broom out of slot variants and giving it a sheet.
 *
 * <h2>Texture paths take either form</h2>
 * A {@code texture} already rooted at {@code textures/} is used verbatim; anything else is a
 * GeckoLib subpath under {@code textures/entity/}. Both are supported because the full path is what
 * a datapack author expects to write and the subpath is what GeckoLib's own helpers produce.
 */
public class BroomVariantGeoModel extends DefaultedEntityGeoModel<BroomEntity> {

    /**
     * The assets this broom draws with.
     *
     * <p>Carried as render data for the same reason {@link BroomRenderer#MODEL_SLOTS} is: the paths
     * are resolved during the render pass, which has no entity to ask, and
     * {@code BroomDefinitionRegistry} is server-synced data reachable only while the entity is.
     */
    public static final DataTicket<BroomAssets> ASSETS =
            DataTicket.create("broom_assets", BroomAssets.class);

    private final Identifier baseAsset;

    public BroomVariantGeoModel(String baseAsset) {
        super(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, baseAsset));
        this.baseAsset = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, baseAsset);
    }

    private BroomAssets assets(GeoRenderState renderState) {
        return renderState.getOrDefaultGeckolibData(ASSETS, BroomAssets.DEFAULT);
    }

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return buildFormattedModelPath(assets(renderState).model().orElse(baseAsset));
    }

    @Override
    public Identifier getAnimationResource(BroomEntity animatable) {
        return buildFormattedAnimationPath(
                animatable.resolveDefinition().assets().animation().orElse(baseAsset));
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        BroomAssets assets = assets(renderState);
        // A full path is already a resource location and must not be run through the formatter,
        // which would root it a second time at textures/entity/.
        if (assets.textureIsFullPath()) {
            return assets.texture().orElseThrow();
        }
        return buildFormattedTexturePath(assets.texture().orElse(baseAsset));
    }
}
