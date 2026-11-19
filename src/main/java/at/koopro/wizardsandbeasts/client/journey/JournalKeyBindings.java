package at.koopro.wizardsandbeasts.client.journey;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.journey.gui.QuestJournalScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.jspecify.annotations.NullMarked;
import org.lwjgl.glfw.GLFW;

/**
 * Keys for the book-and-board screens: the field journal ({@code J}), the brewing log ({@code O}) and the companions
 * board ({@code U}). All free in vanilla and in this mod's other binds.
 */
@NullMarked
public final class JournalKeyBindings {

    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "journal"));

    public static final KeyMapping OPEN_JOURNAL = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".open_journal",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    public static final KeyMapping OPEN_BREWING_LOG = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".open_brewing_log",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY);

    private static final KeyMapping.Category COMPANIONS =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "companions"));

    public static final KeyMapping OPEN_MENAGERIE = new KeyMapping(
            "key." + WizardsAndBeastsMod.MODID + ".open_menagerie",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, COMPANIONS);

    private JournalKeyBindings() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_JOURNAL);
        event.register(OPEN_MENAGERIE);
        event.register(OPEN_BREWING_LOG);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean pressed = false;
        while (OPEN_JOURNAL.consumeClick()) {
            pressed = true;
        }
        if (pressed && mc.player != null && mc.screen == null) {
            mc.setScreen(new QuestJournalScreen());
        }
        boolean brewing = false;
        while (OPEN_BREWING_LOG.consumeClick()) {
            brewing = true;
        }
        if (brewing && mc.player != null && mc.screen == null) {
            mc.setScreen(new at.koopro.wizardsandbeasts.client.brew.gui.BrewingLogScreen());
        }
        boolean menagerie = false;
        while (OPEN_MENAGERIE.consumeClick()) {
            menagerie = true;
        }
        if (menagerie && mc.player != null && mc.screen == null) {
            mc.setScreen(new at.koopro.wizardsandbeasts.client.companion.gui.MenagerieScreen());
        }
    }
}
