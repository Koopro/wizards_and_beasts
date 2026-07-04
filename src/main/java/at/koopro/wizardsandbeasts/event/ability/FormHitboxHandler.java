package at.koopro.wizardsandbeasts.event.ability;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.AnimagusForms;
import at.koopro.wizardsandbeasts.client.form.state.ClientFormDataState;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.form.SizeProfile;
import at.koopro.wizardsandbeasts.form.SizeProfileRegistry;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;

/**
 * Gives Animagus beast forms an accurate collision box.
 * <p>
 * The size system drives the player box purely through {@link net.minecraft.world.entity.ai.attributes.Attributes#SCALE},
 * which scales the {@code 0.6 × 1.8} player box <em>uniformly</em> — so a cat would still
 * collide as a tall, narrow sliver. Real beasts are wider than they are tall, so for Animagus
 * forms we replace the computed size with the form's explicit {@link SizeProfile} hitbox.
 * <p>
 * Runs on both sides: the server box is authoritative for hit-detection and collisions; the
 * client box keeps the local player's movement prediction in sync ({@link ClientFormDataState}
 * only depends on common types, so it is safe to reference from this common handler).
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class FormHitboxHandler {

    private FormHitboxHandler() {}

    @SubscribeEvent
    public static void onEntitySize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player player)) return;

        String formId = resolveFormId(player);
        if (!AnimagusForms.isAnimagusForm(formId)) return;

        SizeProfile profile = SizeProfileRegistry.get(formId);
        if (profile == null) return;

        // Preserve the event's eye-height scaling convention via EntityDimensions.scalable so the
        // first-person camera sits at a believable height for the (short) beast box.
        event.setNewSize(EntityDimensions.scalable(profile.hitboxWidth(), profile.hitboxHeight()));
    }

    private static String resolveFormId(Player player) {
        if (player.level().isClientSide()) {
            ClientFormDataState.FormData data = ClientFormDataState.get(player.getUUID());
            return data != null ? data.formId() : null;
        }
        return player.getData(ModAttachments.HERITAGE_DATA.get()).getActiveFormId();
    }
}
