package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.spell.ProtegoShieldEntity;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoTier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;

public class ProtegoShieldRenderer<R extends EntityRenderState & GeoRenderState> extends GeoEntityRenderer<ProtegoShieldEntity, R> {
    public static final DataTicket<Integer> TICKET_TIER = DataTicket.create("protego_tier", Integer.class);
    public static final DataTicket<Float> TICKET_INTEGRITY = DataTicket.create("protego_integrity", Float.class);
    public static final DataTicket<Boolean> TICKET_COLLAPSING = DataTicket.create("protego_collapsing", Boolean.class);

    /** Red the ward's colour bleeds towards as its pool empties. */
    private static final int FAILING_COLOUR = 0xFFFF6A5A;

    public ProtegoShieldRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "protego_shield")));
    }

    // The shield is a magic barrier, not a solid mob: render it as a full-bright translucent
    // surface so the hex/rune texture glows and layers over the world, instead of GeckoLib's
    // default opaque entityCutoutNoCull (which drew the geo as a grey box). Emissive is what
    // makes it read as a ward at night; the texture bakes the shimmer into vertex alpha.
    @Override
    public RenderType getRenderType(R renderState, Identifier texture) {
        return RenderTypes.entityTranslucentEmissive(texture);
    }

    @Override
    public void addRenderData(ProtegoShieldEntity shield, Void unused, R renderState, float partialTick) {
        super.addRenderData(shield, unused, renderState, partialTick);
        renderState.addGeckolibData(TICKET_TIER, shield.getTier());
        renderState.addGeckolibData(TICKET_INTEGRITY, shield.integrityFraction());
        renderState.addGeckolibData(TICKET_COLLAPSING, shield.isCollapsing());
    }

    /**
     * The ward's colour says which shape it is and how much it has left.
     *
     * <p>Each tier has its own hue; as the pool empties the whole surface slides towards a failing
     * red and thins out, so "this shield is about to go" is readable from across a duel without a
     * bar anywhere on the screen.
     */
    @Override
    public int getRenderColor(@NonNull ProtegoShieldEntity shield, Void unused, float partialTick) {
        ProtegoTier tier = shield.tier();
        float integrity = shield.integrityFraction();
        // Only the last half of the pool shifts hue — above that the tier colour should stay itself.
        float failing = Mth.clamp((0.5f - integrity) * 2.0f, 0.0f, 1.0f);
        int colour = ARGB.srgbLerp(failing, tier.colour(), FAILING_COLOUR);
        float alpha = 0.45f + 0.55f * integrity;
        if (shield.isCollapsing()) {
            alpha *= Math.max(0.0f, 1.0f - shield.collapseTicks() / 20.0f);
        }
        return ARGB.multiplyAlpha(ARGB.multiply(super.getRenderColor(shield, unused, partialTick), colour), alpha);
    }

    /**
     * Drops dome tiers to stand on the ground.
     *
     * <p>The geo's pivot sits 1.5 blocks up (24px), which is where the disc belongs — chest height,
     * held out in front. A dome has to start at the caster's feet instead, so the whole model is
     * lowered by that pivot height before it is drawn. Done here rather than by editing the geo so
     * the disc keeps its original placement.
     */
    @Override
    public void preRenderPass(RenderPassInfo<R> info, SubmitNodeCollector renderTasks) {
        super.preRenderPass(info, renderTasks);
        if (ProtegoTier.byIndex(info.getOrDefaultGeckolibData(TICKET_TIER, 0)).frontalOnly()) {
            return;
        }
        info.poseStack().translate(0.0f, -1.5f, 0.0f);
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> info, BoneSnapshots bones) {
        super.adjustModelBonesForRender(info, bones);
        ProtegoTier tier = ProtegoTier.byIndex(info.getOrDefaultGeckolibData(TICKET_TIER, 0));
        boolean disc = tier.frontalOnly();

        // What you see is what the ward covers: the geo's dome box is one block wide at scale 1, so
        // scaling it to twice the radius makes the drawn shell the same size as the sphere the
        // entity actually tests against. The disc is a personal parry and is drawn a little wider
        // than its radius so its edge is visible from behind it.
        float scale = disc ? (float) (tier.radius() * 1.1) : (float) (tier.radius() * 2.0);
        bones.ifPresent("root", b -> {
            b.setScaleX(scale);
            b.setScaleY(scale);
            b.setScaleZ(scale);
        });

        // The geo always contains BOTH a flat disc and a dome; nothing in the animations hides the
        // unused one, so pick a single silhouette per tier here. Hidden bones are collapsed to zero
        // scale. The dome box hangs below its pivot in the file, so it is turned over to sit above
        // the ground the preRenderPass translate just put it on.
        bones.ifPresent("disc_core", b -> {
            if (disc) {
                b.setRotX((float) (Math.PI / 2.0)); // stand the disc upright so it faces the aim
            } else {
                b.setScaleX(0f);
                b.setScaleY(0f);
                b.setScaleZ(0f);
            }
        });
        bones.ifPresent("dome_hemisphere", b -> {
            if (disc) {
                b.setScaleX(0f);
                b.setScaleY(0f);
                b.setScaleZ(0f);
            } else {
                b.setRotX((float) Math.PI);
            }
        });
    }
}
