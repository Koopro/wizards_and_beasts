package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.spell.ProtegoShieldEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

public class ProtegoShieldRenderer<R extends EntityRenderState & GeoRenderState> extends GeoEntityRenderer<ProtegoShieldEntity, R> {
    public static final DataTicket<Integer> TICKET_TIER = DataTicket.create("protego_tier", Integer.class);
    public static final DataTicket<Boolean> TICKET_SHATTERING = DataTicket.create("protego_shattering", Boolean.class);

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
        renderState.addGeckolibData(TICKET_SHATTERING, shield.isShattering());
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> info, BoneSnapshots bones) {
        super.adjustModelBonesForRender(info, bones);
        int tier = info.getOrDefaultGeckolibData(TICKET_TIER, 0);
        boolean discTier = tier == 0;

        // Overall size: basic shield covers the caster's torso; domes grow with tier.
        float scale = switch (tier) {
            case 1 -> 2.0f;
            case 2, 3 -> 5.5f;
            default -> 1.4f;
        };
        bones.ifPresent("root", b -> {
            b.setScaleX(scale);
            b.setScaleY(scale);
            b.setScaleZ(scale);
        });

        // The geo always contains BOTH a flat disc and a dome; nothing in the animations hides the
        // unused one, so pick a single silhouette per tier here: aimed disc for basic Protego, dome
        // for the upgrades. Hidden bones are collapsed to zero scale.
        bones.ifPresent("disc_core", b -> {
            if (discTier) {
                b.setRotX((float) (Math.PI / 2.0)); // stand the disc upright so it faces the aim
            } else {
                b.setScaleX(0f);
                b.setScaleY(0f);
                b.setScaleZ(0f);
            }
        });
        bones.ifPresent("dome_hemisphere", b -> {
            if (discTier) {
                b.setScaleX(0f);
                b.setScaleY(0f);
                b.setScaleZ(0f);
            }
        });
    }
}
