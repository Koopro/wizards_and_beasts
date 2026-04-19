package at.koopro.neo.client.spell;

import at.koopro.neo.client.gui.SpellMenuScreen;
import at.koopro.neo.network.ClientSpellDataHolder;
import at.koopro.neo.network.SpellSelectC2SPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class SpellClientInputHandler {

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        if (SpellKeyBindings.SPELL_UP.consumeClick()) {
            selectSlot(0);
        }
        if (SpellKeyBindings.SPELL_RIGHT.consumeClick()) {
            selectSlot(1);
        }
        if (SpellKeyBindings.SPELL_DOWN.consumeClick()) {
            selectSlot(2);
        }
        if (SpellKeyBindings.SPELL_LEFT.consumeClick()) {
            selectSlot(3);
        }
        if (SpellKeyBindings.SPELL_MENU.consumeClick()) {
            mc.setScreen(new SpellMenuScreen());
        }
    }

    private static void selectSlot(int slot) {
        ClientSpellDataHolder.get().setActiveSlot(slot);
        ClientPacketDistributor.sendToServer(new SpellSelectC2SPacket(slot));
    }
}
