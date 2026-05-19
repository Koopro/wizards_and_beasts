package at.koopro.wizardsandbeasts.client.form;

import at.koopro.wizardsandbeasts.client.form.state.ClientFormDataState;
import at.koopro.wizardsandbeasts.form.FormRegistry;
import at.koopro.wizardsandbeasts.form.ModelType;
import at.koopro.wizardsandbeasts.form.PlayerForm;
import at.koopro.wizardsandbeasts.form.RenderFlag;
import at.koopro.wizardsandbeasts.form.SizeProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Attaches form metadata to player render states, following the same pattern
 * as {@link BroomRiderRenderer}. This data is consumed by {@link FormRenderHandler}.
 */
public class FormRenderStateModifier {

    private static final Map<Object, FormRenderData> FORM_DATA_MAP = new IdentityHashMap<>();

    public static FormRenderData getFormData(LivingEntityRenderState state) {
        return FORM_DATA_MAP.get(state);
    }

    public static void removeFormData(LivingEntityRenderState state) {
        FORM_DATA_MAP.remove(state);
    }

    @SuppressWarnings("unchecked")
    public static void registerModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(
                (Class) LivingEntityRenderer.class,
                (entity, renderState) -> {
                    if (entity instanceof Player player
                            && renderState instanceof LivingEntityRenderState livingState) {
                        ClientFormDataState.FormData data =
                                ClientFormDataState.get(player.getUUID());
                        if (data == null) return;

                        PlayerForm form = FormRegistry.get(data.formId());
                        if (form == null) return;

                        FORM_DATA_MAP.put(renderState, new FormRenderData(
                                player.getUUID(),
                                data.formId(),
                                form.modelType(),
                                form.texturePath(),
                                data.sizeProfile(),
                                data.renderFlags()));

                        // For HUMANOID forms: override state.scale with lerped value
                        // so vanilla applies smooth scale transitions.
                        // Non-HUMANOID forms are handled entirely by the Mixin.
                        if (form.modelType() == ModelType.HUMANOID) {
                            float pt = Minecraft.getInstance()
                                    .getDeltaTracker().getGameTimeDeltaPartialTick(false);
                            float lerpedScale = SizeLerpTracker.getVisualScale(
                                    player.getUUID(), data.sizeProfile().modelScale(), pt);
                            if (lerpedScale > 0) {
                                livingState.scale = lerpedScale;
                            }
                        }
                    }
                });
    }

    public record FormRenderData(
            java.util.UUID playerUUID,
            String formId,
            ModelType modelType,
            net.minecraft.resources.Identifier texturePath,
            SizeProfile sizeProfile,
            EnumSet<RenderFlag> renderFlags
    ) {}
}
