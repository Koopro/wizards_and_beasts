package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.GeoRendererHelper;
import at.koopro.wizardsandbeasts.client.ability.state.ClientAbilityCache;
import at.koopro.wizardsandbeasts.entity.beast.ThestralEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Draws a thestral for the players who can see one, and draws nothing for everyone else.
 *
 * <p>Seeing a thestral is decided per viewer, on the viewer's own client, from their synced
 * {@link ThestralEntity#WITNESSED_DEATH_FLAG}. It is purely a question of drawing, so it lives in
 * {@link #shouldRender}: the entity, its hitbox, its AI, its riders and its tracking are the same for every player,
 * and a player who cannot see it can still walk into it, feed it, be carried by it or strike it — as Ron and Hermione
 * rode thestrals they could not see. Refusing to render also drops the shadow, the name tag and the eye glow with it.
 *
 * <p>This replaces the entity's old {@code isInvisible()}/{@code isInvisibleTo()} overrides. In vanilla an entity that
 * is invisible but not invisible <em>to you</em> is drawn translucent, the way a spectator sees an invisible player —
 * so every witness saw a ghost instead of a thestral. Spectators see it regardless, as they see everything.
 */
public class ThestralRenderer<R extends EntityRenderState & GeoRenderState> extends GeoEntityRenderer<ThestralEntity, R> {

    public ThestralRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "thestral"), "head"));
        GeoRendererHelper.applyGlowIfPresent(this, "thestral");
    }

    @Override
    public boolean shouldRender(@NonNull ThestralEntity thestral, @NonNull Frustum frustum, double x, double y, double z) {
        return canSeeThestrals() && super.shouldRender(thestral, frustum, x, y, z);
    }

    /** Whether the local player can see thestrals: they have witnessed death, or are spectating. */
    public static boolean canSeeThestrals() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isSpectator()) {
            return true;
        }
        return ClientAbilityCache.get().abilityFlags().contains(ThestralEntity.WITNESSED_DEATH_FLAG);
    }
}
