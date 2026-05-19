package at.koopro.wizardsandbeasts.client.form;

import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

public final class ObscurialClientViewHandler {

    private ObscurialClientViewHandler() {}

    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.options.getCameraType().isFirstPerson() && isObscurialDark()) {
            event.setCanceled(true);
        }
    }

    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!isObscurialDark()) return;

        Identifier layer = event.getName();
        if (layer != null && shouldHideVanillaHudLayer(layer.getPath())) {
            event.setCanceled(true);
        }
    }

    private static boolean shouldHideVanillaHudLayer(String path) {
        // Keep chat/menus/debug overlays intact, but hide gameplay bars and slots.
        return switch (path) {
            case "hotbar",
                    "crosshair",
                    "player_health",
                    "armor_level",
                    "food_level",
                    "air_level",
                    "experience_bar",
                    "jump_meter",
                    "vehicle_health" -> true;
            default -> false;
        };
    }

    private static boolean isObscurialDark() {
        return "obscurial_dark".equals(ClientHeritageDataState.get().getActiveFormId());
    }
}
