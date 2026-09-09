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
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Drop-in replacement for {@code GeoRendererHelper.simple} that honours a creature's synced
 * {@code Tint} — the obscurus's smoke, the ashwinder's ember-pulse. The tint is multiplied into the
 * base render colour, and an opaque white ({@code 0xFFFFFFFF}) is the no-op, so every creature can
 * use this renderer harmlessly.
 *
 * <h2>Why this no longer scales anything</h2>
 * This class was {@code ScaledBeastRenderer}: it copied a render-only synced float into render-state
 * and applied it to the {@code root} bone, because the Occamy's choranaptyxis had nowhere else to
 * live. That was a second, weaker size mechanism sitting next to a native one —
 * {@code GeoEntityRenderer.scaleModelForRender} already multiplies the model by
 * {@code LivingEntityRenderState.scale}, which is {@code entity.getScale()}, which is
 * {@code Attributes.SCALE}. Driving the attribute (see {@code GenericBeastEntity.applySizeScale})
 * moves the model <b>and</b> the hitbox with one number and needs no renderer support at all, so
 * the bone hack is gone and the class is named for the one thing it still does.
 */
public class TintedBeastRenderer<R extends EntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<GenericBeastEntity, R> {

    public TintedBeastRenderer(EntityRendererProvider.Context context, String modelName) {
        super(context, new DefaultedEntityGeoModel<>(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, modelName)));
    }

    /** For subclasses (e.g. {@link DisguisableBeastRenderer}) that need a custom model, not the default one built from a name. */
    protected TintedBeastRenderer(EntityRendererProvider.Context context, DefaultedEntityGeoModel<GenericBeastEntity> model) {
        super(context, model);
    }

    @Override
    public int getRenderColor(@NonNull GenericBeastEntity beast, Void unused, float partialTick) {
        int base = super.getRenderColor(beast, unused, partialTick);
        int tint = beast.getTint();
        // 0xFFFFFFFF (opaque white) = no tint; otherwise multiply it into the base/invisibility colour.
        return tint == 0xFFFFFFFF ? base : ARGB.multiply(base, tint);
    }

    /** Provider mirroring {@code GeoRendererHelper.simple}/{@code DragonRenderer.provider} for the manifest loop. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Entity & GeoEntity> EntityRendererProvider<T> provider(String modelName) {
        return context -> at.koopro.wizardsandbeasts.client.GeoRendererHelper.applyGlowIfPresent(
                new TintedBeastRenderer(context, modelName), modelName);
    }
}
