package at.koopro.wizardsandbeasts.client.form;

import at.koopro.wizardsandbeasts.ability.AnimagusForms;
import at.koopro.wizardsandbeasts.client.form.state.ClientFormDataState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

/**
 * Client-side view tweaks for Animagus beast forms:
 * <ul>
 *   <li>{@link #onRenderHand} — a beast has no hands, so the first-person held-item / arm
 *       render makes no sense; it is suppressed while the local player is in a beast form.</li>
 *   <li>{@link #onRenderNameTag} — an Animagus is in disguise: the floating player name is
 *       hidden for every viewer (form data is synced to all tracking clients, so this works
 *       in multiplayer too).</li>
 * </ul>
 */
public final class AnimagusClientViewHandler {

    private AnimagusClientViewHandler() {}

    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (isBeastForm(mc.player)) {
            event.setCanceled(true);
        }
    }

    public static void onRenderNameTag(RenderNameTagEvent.CanRender event) {
        if (event.getEntity() instanceof Player player && isBeastForm(player)) {
            event.setCanRender(TriState.FALSE);
        }
    }

    /** True when the given player is currently in an Animagus beast form (per synced form data). */
    private static boolean isBeastForm(Player player) {
        ClientFormDataState.FormData data = ClientFormDataState.get(player.getUUID());
        return data != null && AnimagusForms.isAnimagusForm(data.formId());
    }
}
