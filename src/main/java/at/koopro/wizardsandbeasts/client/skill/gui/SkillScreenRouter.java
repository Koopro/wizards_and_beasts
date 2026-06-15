package at.koopro.wizardsandbeasts.client.skill.gui;

import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.Heritage;
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
            PlayerHeritageData typeData = ClientHeritageDataState.get();
            Heritage type = typeData.getSelectedHeritage();
            HeritageVariant subtype = typeData.getSelectedHeritageVariant();

            if (type == null) {
                mc.setScreen(new SkillAccessDeniedScreen(
                        Component.translatable("screen.wizards_and_beasts.skill_access_denied.no_type")));
                return;
            }

            // Goblins and house-elves have their own heritage trees (gated by audience inside the
            // screen); route them to the real tree screen before the wand/muggle gate below.
            if (type == Heritage.GOBLIN || type == Heritage.HOUSE_ELF) {
                mc.setScreen(new SkillTreeScreen());
                return;
            }

            // Muggle-like branch: non-magical/no-wand profiles are not allowed.
            if (!type.canUseWand() || (subtype != null && subtype.hasTag("no_wand"))) {
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
