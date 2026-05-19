package at.koopro.wizardsandbeasts.client.bestiary.niffler;

import at.koopro.wizardsandbeasts.entity.niffler.CarriedNifflerAttachment;
import at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;

/**
 * Placeholder layer — renders nothing until niffler_peek.geo.json is created.
 * Wired into AvatarRenderer via EntityRenderersEvent.AddLayers.
 */
public class NifflerPocketLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public NifflerPocketLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState renderState, float yRot, float xRot) {
        if (!ModuleManager.isEnabled(Module.CREATURES)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.player.getId() != renderState.id) return;

        CarriedNifflerAttachment att = mc.player.getData(ModAttachments.CARRIED_NIFFLER.get());
        if (!att.isCarrying()) return;

        Entity entity = mc.level.getEntities(null, mc.player.getBoundingBox().inflate(4))
                .stream()
                .filter(e -> e instanceof NifflerEntity n && n.getUUID().equals(att.nifflerUUID()))
                .findFirst().orElse(null);

        if (!(entity instanceof NifflerEntity niffler)) return;
        if (niffler.getBondLevel() < 100) return;
        if (niffler.getDataPeekTick() <= 0) return;

        // Placeholder — full GeckoLib peek model render goes here once assets exist
    }
}
