package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.beast.BowtruckleEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.NonNull;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * The Bowtruckle, and its camouflage.
 *
 * <p>When it has gone still against bark ({@link BowtruckleEntity#isCamouflaged}, decided and synced by the server) it
 * is drawn part-transparent, so the bark behind it shows through, and casts no shadow. It is never fully invisible —
 * a careful look still finds it, as canon says a careful look is what it takes — and it is the same for every viewer.
 * The moment it moves the flag drops and it is solid again.
 *
 * <p>Head tracking turns the cube-less {@code look} bone, leaving the head free for its clips.
 */
public class BowtruckleRenderer<R extends EntityRenderState & GeoRenderState> extends GeoEntityRenderer<BowtruckleEntity, R> {

    /** Alpha while camouflaged: faint, not gone. */
    public static final int CAMOUFLAGE_ALPHA = 0x70;

    static final DataTicket<Boolean> TICKET_CAMOUFLAGED = DataTicket.create("bowtruckle_camouflaged", Boolean.class);

    public BowtruckleRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "bowtruckle"), "look"));
        this.shadowRadius = 0.15f;
    }

    @Override
    public void addRenderData(@NonNull BowtruckleEntity bowtruckle, Void unused, @NonNull R renderState, float partialTick) {
        super.addRenderData(bowtruckle, unused, renderState, partialTick);
        renderState.addGeckolibData(TICKET_CAMOUFLAGED, bowtruckle.isCamouflaged());
        if (bowtruckle.isCamouflaged()) {
            renderState.shadowRadius = 0f;
        }
    }

    @Override
    public int getRenderColor(@NonNull BowtruckleEntity bowtruckle, Void unused, float partialTick) {
        int base = super.getRenderColor(bowtruckle, unused, partialTick);
        return bowtruckle.isCamouflaged() ? ARGB.color(CAMOUFLAGE_ALPHA, base) : base;
    }

    @Override
    public RenderType getRenderType(@NonNull R renderState, @NonNull Identifier texture) {
        return Boolean.TRUE.equals(renderState.getOrDefaultGeckolibData(TICKET_CAMOUFLAGED, false))
                ? RenderTypes.entityTranslucent(texture)
                : super.getRenderType(renderState, texture);
    }
}
