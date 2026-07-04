package at.koopro.wizardsandbeasts.client.network;

import at.koopro.wizardsandbeasts.client.floo.gui.FlooNetworkScreen;
import at.koopro.wizardsandbeasts.client.spell.gui.BeamDebugScreen;
import at.koopro.wizardsandbeasts.client.bestiary.gui.BestiaryScreen;
import at.koopro.wizardsandbeasts.client.handbook.HandbookScreen;
import at.koopro.wizardsandbeasts.client.gui.character.CharacterSheetScreen;
import at.koopro.wizardsandbeasts.client.currency.gui.GringottsScreen;
import at.koopro.wizardsandbeasts.client.skill.gui.SkillScreenRouter;
import at.koopro.wizardsandbeasts.client.spell.gui.SpellTeacherScreen;
import at.koopro.wizardsandbeasts.client.heritage.gui.HeritageSelectionScreen;
import at.koopro.wizardsandbeasts.client.trinket.gui.DiaryPossessionScreen;
import at.koopro.wizardsandbeasts.client.trinket.gui.DiaryWriteScreen;
import at.koopro.wizardsandbeasts.client.trinket.gui.MirrorCallScreen;
import at.koopro.wizardsandbeasts.client.trinket.gui.MirrorViewScreen;
import at.koopro.wizardsandbeasts.client.trinket.gui.PensieveScreen;
import at.koopro.wizardsandbeasts.floo.FlooDestinationDto;
import at.koopro.wizardsandbeasts.memory.MemoryEntry;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.UUID;

public final class ClientScreenHooks {
    private ClientScreenHooks() {
    }

    /** Opens the Floo network screen. Reflectively invoked from common code via
     *  {@link at.koopro.wizardsandbeasts.network.ClientScreenHooksInvoker}, so the
     *  {@code FlooNetworkScreen} reference never loads server-side. */
    public static void openFlooNetworkScreen(List<FlooDestinationDto> destinations, boolean callMode) {
        Minecraft.getInstance().setScreen(new FlooNetworkScreen(destinations, callMode));
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

    public static void openHandbookScreen() {
        Minecraft.getInstance().setScreen(new HandbookScreen());
    }

    public static void openCharacterSheetScreen() {
        Minecraft.getInstance().setScreen(new CharacterSheetScreen());
    }

    public static void openPensieveScreen(List<MemoryEntry> memories) {
        Minecraft.getInstance().setScreen(new PensieveScreen(memories));
    }

    public static void openMirrorCallScreen(UUID pairId) {
        Minecraft.getInstance().setScreen(new MirrorCallScreen(pairId));
    }

    public static void openMirrorViewScreen(UUID otherUuid, String otherName) {
        Minecraft.getInstance().setScreen(new MirrorViewScreen(otherUuid, otherName));
    }

    public static void updateMirrorPresence(float yaw, float pitch) {
        MirrorViewScreen.updatePresence(yaw, pitch);
    }

    public static void closeMirror(String reason) {
        MirrorViewScreen.closeFromServer(reason);
    }

    public static void openDiaryWriteScreen() {
        Minecraft.getInstance().setScreen(new DiaryWriteScreen());
    }

    public static void appendDiaryReply(String line, int tier) {
        DiaryWriteScreen.appendReply(line, tier);
    }

    public static void openDiaryPossession(String line) {
        Minecraft.getInstance().setScreen(new DiaryPossessionScreen(line));
    }
}
