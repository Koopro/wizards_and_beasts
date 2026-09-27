package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.GeoRendererHelper;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NonNull;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * The basilisk: the tinted beast renderer with two differences.
 *
 * <ul>
 *   <li><b>Head tracking on {@code look}</b>, a bone with no cubes between the neck and the head. GeckoLib's head
 *       tracking <em>sets</em> the named bone's rotation, so pointing it at {@code head} would erase every strike,
 *       rear and gaze pose; {@code look} is keyed by no clip and loses nothing.</li>
 *   <li><b>Culled by the whole serpent</b>, not the square hitbox. The body lies in an S well outside a box that is
 *       square in plan; culling by the box made the tail blink out whenever the box left the screen.</li>
 * </ul>
 */
public class BasiliskRenderer<R extends EntityRenderState & GeoRenderState> extends TintedBeastRenderer<R> {

    /** How far the drawn serpent reaches past its hitbox, at scale 1 (the rig's length, generously). */
    private static final double BODY_REACH = 7.0;

    public BasiliskRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "basilisk"), "look"));
        this.shadowRadius = 1.6f;
    }

    @Override
    protected @NonNull AABB getBoundingBoxForCulling(@NonNull GenericBeastEntity basilisk) {
        double reach = BODY_REACH * basilisk.getScale();
        return basilisk.getBoundingBox().inflate(reach, 1.0, reach);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Entity & GeoEntity> EntityRendererProvider<T> provider() {
        return context -> GeoRendererHelper.applyGlowIfPresent(new BasiliskRenderer(context), "basilisk");
    }
}
