package at.koopro.wizardsandbeasts.client.spell;

import at.koopro.wizardsandbeasts.client.gui.character.CharacterSheetScreen;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class CharacterSheetKeyHandler {

    private CharacterSheetKeyHandler() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ModuleManager.isEnabled(Module.CHARACTER_SHEET)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (SpellKeyBindings.CHARACTER_SHEET.consumeClick()) {
            mc.setScreen(new CharacterSheetScreen());
        }
    }
}
