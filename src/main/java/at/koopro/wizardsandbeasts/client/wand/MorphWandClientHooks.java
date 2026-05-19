package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.client.gui.MorphDebugScreen;
import net.minecraft.client.Minecraft;

public final class MorphWandClientHooks {
    private MorphWandClientHooks() {
    }

    public static void openDebugScreen() {
        Minecraft.getInstance().setScreen(new MorphDebugScreen());
    }
}
