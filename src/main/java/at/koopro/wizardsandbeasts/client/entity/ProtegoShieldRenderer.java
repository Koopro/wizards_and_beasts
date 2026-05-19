package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.spell.ProtegoShieldEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
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
        bones.ifPresent("root", b -> {
            float scale = switch (tier) {
                case 1 -> 2.0f;
                case 2, 3 -> 5.5f;
                default -> 1.0f;
            };
            b.setScaleX(scale);
            b.setScaleY(scale);
            b.setScaleZ(scale);
        });
    }
}
