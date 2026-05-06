package at.koopro.wizardsandbeasts.client.network;

import at.koopro.wizardsandbeasts.client.gui.BeamDebugScreen;
import at.koopro.wizardsandbeasts.client.gui.BestiaryScreen;
import at.koopro.wizardsandbeasts.client.gui.GringottsScreen;
import at.koopro.wizardsandbeasts.client.gui.SkillScreenRouter;
import at.koopro.wizardsandbeasts.client.gui.SpellTeacherScreen;
import at.koopro.wizardsandbeasts.client.gui.HeritageSelectionScreen;
import net.minecraft.client.Minecraft;

public final class ClientScreenHooks {
    private ClientScreenHooks() {
    }

    public static void openGringottsScreen() {
        Minecraft.getInstance().setScreen(new GringottsScreen());
    }

    public static void openBeamDebugScreen() {
        Minecraft.getInstance().setScreen(new BeamDebugScreen());
    }

    public static void openHeritageSelectionScreen() {
        Minecraft.getInstance().setScreen(new HeritageSelectionScreen());
    }

    public static void openSkillTreeScreen() {
        SkillScreenRouter.openForCurrentPlayer();
    }

    public static void openSpellTeacherScreen() {
        Minecraft.getInstance().setScreen(new SpellTeacherScreen());
    }

    public static void openBestiaryScreen() {
        Minecraft.getInstance().setScreen(new BestiaryScreen());
    }
}
