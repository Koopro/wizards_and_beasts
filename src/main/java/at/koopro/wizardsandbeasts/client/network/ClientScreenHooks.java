package at.koopro.wizardsandbeasts.client.network;

import at.koopro.wizardsandbeasts.client.floo.gui.FlooNetworkScreen;
import at.koopro.wizardsandbeasts.client.spell.gui.BeamDebugScreen;
import at.koopro.wizardsandbeasts.client.bestiary.gui.BestiaryScreen;
import at.koopro.wizardsandbeasts.client.gui.character.CharacterSheetScreen;
import at.koopro.wizardsandbeasts.client.currency.gui.GringottsScreen;
import at.koopro.wizardsandbeasts.client.skill.gui.SkillScreenRouter;
import at.koopro.wizardsandbeasts.client.spell.gui.SpellTeacherScreen;
import at.koopro.wizardsandbeasts.client.heritage.gui.HeritageSelectionScreen;
import at.koopro.wizardsandbeasts.floo.FlooDestinationDto;
import net.minecraft.client.Minecraft;

import java.util.List;

public final class ClientScreenHooks {
    private ClientScreenHooks() {
    }

    /** Opens the Floo network screen. Reflectively invoked from common code via
     *  {@link at.koopro.wizardsandbeasts.network.ClientScreenHooksInvoker}, so the
     *  {@code FlooNetworkScreen} reference never loads server-side. */
    public static void openFlooNetworkScreen(List<FlooDestinationDto> destinations) {
        Minecraft.getInstance().setScreen(new FlooNetworkScreen(destinations));
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

    public static void openCharacterSheetScreen() {
        Minecraft.getInstance().setScreen(new CharacterSheetScreen());
    }
}
