package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
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
 * Drop-in replacement for {@code GeoRendererHelper.simple} that honours a creature's synced render-scale
 * ({@link GenericBeastEntity#getRenderScale()}) — the Occamy's choranaptyxic size-shift. The scale is copied
 * into render-state in {@link #addRenderData} and applied to the {@code root} bone in
 * {@link #adjustModelBonesForRender}; the live entity is never read at render time (GeckoLib-5 contract).
 * Default scale {@code 1.0} = identical to the simple renderer, so every creature can use it harmlessly.
 */
public class ScaledBeastRenderer<R extends EntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<GenericBeastEntity, R> {

    public static final DataTicket<Float> TICKET_SCALE = DataTicket.create("beast_scale", Float.class);

    public ScaledBeastRenderer(EntityRendererProvider.Context context, String modelName) {
        super(context, new DefaultedEntityGeoModel<>(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, modelName)));
    }

    /** For subclasses (e.g. {@code DisguisableBeastRenderer}) that need a custom model, not the default one built from a name. */
    protected ScaledBeastRenderer(EntityRendererProvider.Context context, DefaultedEntityGeoModel<GenericBeastEntity> model) {
        super(context, model);
    }

    @Override
    public void addRenderData(@NonNull GenericBeastEntity beast, Void unused,
                             @NonNull R renderState, float partialTick) {
        super.addRenderData(beast, unused, renderState, partialTick);
        renderState.addGeckolibData(TICKET_SCALE, beast.getRenderScale());
    }

    @Override
    public int getRenderColor(@NonNull GenericBeastEntity beast, Void unused, float partialTick) {
        int base = super.getRenderColor(beast, unused, partialTick);
        int tint = beast.getTint();
        // 0xFFFFFFFF (opaque white) = no tint; otherwise multiply it into the base/invisibility colour.
        return tint == 0xFFFFFFFF ? base : ARGB.multiply(base, tint);
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

    /** Provider mirroring {@code GeoRendererHelper.simple}/{@code DragonRenderer.provider} for the manifest loop. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Entity & GeoEntity> EntityRendererProvider<T> provider(String modelName) {
        return context -> new ScaledBeastRenderer(context, modelName);
    }
}
