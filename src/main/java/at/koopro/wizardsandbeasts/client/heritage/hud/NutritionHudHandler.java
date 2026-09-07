package at.koopro.wizardsandbeasts.client.heritage.hud;

import at.koopro.wizardsandbeasts.client.heritage.state.ClientBloodState;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jspecify.annotations.NullMarked;

/**
 * Takes the vanilla hunger bar off the screen for anyone whose nutrition is not hunger.
 *
 * <p>The same technique {@code ObscurialClientViewHandler} uses to blank the HUD inside an obscurus, and
 * for the same reason: a HUD layer is cancellable by id, which is a far smaller commitment than replacing
 * {@code Gui} or mixing into it.
 *
 * <p><b>Only the drumsticks go.</b> Health, armour, air and experience all stay — a vampire is still a
 * body with hit points, and hiding more of the HUD than the one bar that has been replaced would make
 * the heritage feel broken rather than different.
 *
 * <p>Cancelling the layer also skips vanilla's {@code rightHeight += 10}, so the rows below it would
 * close the gap and the blood meter would draw on top of the air bubbles. {@link BloodBarRenderer}
 * performs that advance itself; the two are a pair and neither is correct alone.
 */
@NullMarked
public final class NutritionHudHandler {

    private NutritionHudHandler() {}

    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        // Every non-vanilla policy loses the bar, not just BLOOD. A policy with no replacement meter
        // (NONE) simply leaves the slot empty and lets the rows below it close up, which is the honest
        // rendering of a body that does not think about food at all.
        if (ClientBloodState.policy().usesVanillaHunger()) {
            return;
        }
        if (VanillaGuiLayers.FOOD_LEVEL.equals(event.getName())) {
            event.setCanceled(true);
        }
    }
}
