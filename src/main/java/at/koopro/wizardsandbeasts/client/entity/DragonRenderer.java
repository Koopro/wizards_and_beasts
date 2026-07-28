package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.DragonTraits;
import at.koopro.wizardsandbeasts.entity.creature.DragonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

/**
 * GeckoLib renderer for {@link DragonEntity}. One renderer drives all ten breeds; per-breed model is the
 * id-named {@code DefaultedEntityGeoModel} (placeholder rig, real Blockbench art drops in by file swap).
 *
 * <p>Breed differentiation (render-only scale + flame tint) flows through {@link DataTicket}s copied in
 * {@link #addRenderData}; the bone-adjust pass reads only render-state — never the live entity, per the
 * GeckoLib-5 "no entity access at render time" contract. Flame particles themselves are server-spawned
 * (see {@code DragonBreathGoal}); the {@link #TICKET_FIRE_COLOR} is carried here so the model can be
 * tinted by the same canon colour without re-reading the entity.
 */
public class DragonRenderer<R extends EntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<DragonEntity, R> {

    /** Render-only model scale (hitbox is registry-frozen; this never touches collision). */
    public static final DataTicket<Float> TICKET_SCALE =
            DataTicket.create("dragon_scale", Float.class);
    /** {@code 0xRRGGBB} flame tint, for model/flame tinting without live-entity access. */
    public static final DataTicket<Integer> TICKET_FIRE_COLOR =
            DataTicket.create("dragon_fire_color", Integer.class);

    public DragonRenderer(EntityRendererProvider.Context context, String modelName) {
        super(context, new DefaultedEntityGeoModel<>(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, modelName)));
    }

    @Override
    public void addRenderData(@NonNull DragonEntity dragon, Void unused,
                             @NonNull R renderState, float partialTick) {
        super.addRenderData(dragon, unused, renderState, partialTick);
        DragonTraits traits = dragon.dragonTraits();
        float scale = traits != null ? traits.scale() : 1.0f;
        int color = traits != null ? traits.fireColor() : 0xF97316;
        renderState.addGeckolibData(TICKET_SCALE, scale);
        renderState.addGeckolibData(TICKET_FIRE_COLOR, color);
    }

    @Override
    public void adjustModelBonesForRender(@NonNull RenderPassInfo<R> info, @NonNull BoneSnapshots bones) {
        super.adjustModelBonesForRender(info, bones);
        float scale = info.getOrDefaultGeckolibData(TICKET_SCALE, 1.0f);
        if (scale != 1.0f) {
            bones.ifPresent("root", b -> {
                b.setScaleX(scale);
                b.setScaleY(scale);
                b.setScaleZ(scale);
            });
        }
    }

    /**
     * Raw provider mirroring {@code GeoRendererHelper.simple}: lets {@code ClientSetup} register this
     * renderer against the manifest's {@code EntityType<GenericBeastEntity>} holders (DragonEntity is a
     * subclass) without fighting the generic bounds.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Entity & GeoEntity> EntityRendererProvider<T> provider(String modelName) {
        return context -> at.koopro.wizardsandbeasts.client.GeoRendererHelper.applyGlowIfPresent(
                new DragonRenderer(context, modelName), modelName);
    }
}
