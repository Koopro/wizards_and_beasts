package at.koopro.wizardsandbeasts.client.gui;

import at.koopro.wizardsandbeasts.client.state.ClientTypeDataState;
import at.koopro.wizardsandbeasts.data.PlayerTypeData;
import at.koopro.wizardsandbeasts.type.WizSubtype;
import at.koopro.wizardsandbeasts.type.WizType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class SkillScreenRouter {

    private SkillScreenRouter() {
    }

    public static void openForCurrentPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        try {
            PlayerTypeData typeData = ClientTypeDataState.get();
            WizType type = typeData.getSelectedType();
            WizSubtype subtype = typeData.getSelectedSubtype();

            if (type == null) {
                mc.setScreen(new SkillAccessDeniedScreen(
                        Component.translatable("screen.wizards_and_beasts.skill_access_denied.no_type")));
                return;
            }

            if (type == WizType.GOBLIN) {
                mc.setScreen(new GoblinSkillScreen());
                return;
            }

            if (type == WizType.HOUSE_ELF) {
                mc.setScreen(new ElfSkillScreen());
                return;
            }

            // Muggle-like branch: non-magical/no-wand profiles are not allowed.
            if (!type.canUseWand() || (subtype != null && subtype == WizSubtype.SQUIB)) {
                mc.setScreen(new SkillAccessDeniedScreen(
                        Component.translatable("screen.wizards_and_beasts.skill_access_denied.muggle_like")));
                return;
            }

            mc.setScreen(new SkillTreeScreen());
        } catch (Throwable ignored) {
            mc.setScreen(new SkillAccessDeniedScreen(
                    Component.translatable("screen.wizards_and_beasts.skill_access_denied.generic_error")));
        }
    }
}
