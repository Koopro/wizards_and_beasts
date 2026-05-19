package at.koopro.wizardsandbeasts.client.spell.input;

import at.koopro.wizardsandbeasts.client.skill.gui.SkillScreenRouter;
import at.koopro.wizardsandbeasts.client.spell.gui.SpellMenuScreen;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellDataState;
import at.koopro.wizardsandbeasts.client.spell.SpellKeyBindings;
import at.koopro.wizardsandbeasts.network.spell.SpellSelectC2SPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class SpellInputController {
    private SpellInputController() {}

    public static void handleGameplayBindings(Minecraft mc, boolean canUseWandMagic) {
        if (canUseWandMagic && SpellKeyBindings.SPELL_UP.consumeClick()) {
            selectSlot(0);
        }
        if (canUseWandMagic && SpellKeyBindings.SPELL_RIGHT.consumeClick()) {
            selectSlot(1);
        }
        if (canUseWandMagic && SpellKeyBindings.SPELL_DOWN.consumeClick()) {
            selectSlot(2);
        }
        if (canUseWandMagic && SpellKeyBindings.SPELL_LEFT.consumeClick()) {
            selectSlot(3);
        }
        if (canUseWandMagic && SpellKeyBindings.SPELL_MENU.consumeClick()) {
            mc.setScreen(new SpellMenuScreen());
        }
        if (SpellKeyBindings.SKILL_MENU.consumeClick()) {
            SkillScreenRouter.openForCurrentPlayer();
        }
    }

    private static void selectSlot(int slot) {
        if (ClientSpellDataState.get().getActiveSlot() == slot) {
            return;
        }
        ClientSpellDataState.get().setActiveSlot(slot);
        ClientPacketDistributor.sendToServer(new SpellSelectC2SPayload(slot));
    }
}
