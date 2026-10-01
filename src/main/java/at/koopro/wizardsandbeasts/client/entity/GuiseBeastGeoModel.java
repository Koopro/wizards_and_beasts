package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.entity.creature.Guise;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws a creature in the shape it is wearing ({@link GenericBeastEntity#getGuise}) — another creature's model, skin
 * and animation file — or in its own when it wears none. The Boggart's model.
 *
 * <p>Model and texture branch on a render-state ticket (the GeckoLib 5 contract: no live entity at render time); the
 * animation file branches on the animatable, which is what GeckoLib hands that method. Both read the same synced
 * string, so the model and the clips playing on it always belong to the same creature.
 */
public class GuiseBeastGeoModel extends DefaultedEntityGeoModel<GenericBeastEntity> {

    public static final DataTicket<String> TICKET_GUISE = DataTicket.create("beast_guise", String.class);

    private final Map<String, Identifier> models = new ConcurrentHashMap<>();
    private final Map<String, Identifier> textures = new ConcurrentHashMap<>();
    private final Map<String, Identifier> animations = new ConcurrentHashMap<>();

    public GuiseBeastGeoModel(Identifier ownAssetSubpath) {
        super(ownAssetSubpath);
    }

    private static Identifier asset(String form) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, form);
    }

    private static Guise guise(GeoRenderState renderState) {
        return Guise.decode(renderState.getOrDefaultGeckolibData(TICKET_GUISE, ""));
    }

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        Guise guise = guise(renderState);
        return guise == null ? super.getModelResource(renderState)
                : models.computeIfAbsent(guise.form(), form -> buildFormattedModelPath(asset(form)));
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        Guise guise = guise(renderState);
        return guise == null ? super.getTextureResource(renderState)
                : textures.computeIfAbsent(guise.form(), form -> buildFormattedTexturePath(asset(form)));
    }

    @Override
    public Identifier getAnimationResource(GenericBeastEntity animatable) {
        Guise guise = Guise.decode(animatable.getGuise());
        return guise == null ? super.getAnimationResource(animatable)
                : animations.computeIfAbsent(guise.form(), form -> buildFormattedAnimationPath(asset(form)));
    }
}
