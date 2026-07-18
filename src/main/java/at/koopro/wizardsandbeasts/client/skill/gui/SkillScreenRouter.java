package at.koopro.wizardsandbeasts.client.skill.gui;

import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class SkillScreenRouter {

    private SkillScreenRouter() {
    }

    /**
     * Opens the skill chart for the player's resolved audience. There is exactly one gate here — a player
     * with no heritage has no audience and no chart. Everyone with a heritage opens their chart; capability
     * gating (wand/casting) happens <i>inside</i> the chart as sealed regions, keyed off the same synced
     * heritage state the server enforces on — never re-derived from {@code canUseWand} here. The old
     * muggle_like denial path is gone: a squib or obscurial has a wizard audience and must reach the chart
     * to allocate in its open regions.
     */
    public static void openForCurrentPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        try {
            PlayerHeritageData typeData = ClientHeritageDataState.get();
            Heritage type = typeData.getSelectedHeritage();

            if (type == null) {
                mc.setScreen(new SkillAccessDeniedScreen(
                        Component.translatable("screen.wizards_and_beasts.skill_access_denied.no_type")));
                return;
            }

            mc.setScreen(new SkillTreeScreen());
        } catch (Throwable ignored) {
            mc.setScreen(new SkillAccessDeniedScreen(
                    Component.translatable("screen.wizards_and_beasts.skill_access_denied.generic_error")));
        }
    }
}
