package at.koopro.wizardsandbeasts.client.owl;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.block.ExaminationDeskBlock;
import at.koopro.wizardsandbeasts.client.owl.screen.OWLExamScreen;
import at.koopro.wizardsandbeasts.client.owl.screen.OWLResultsReadOnlyScreen;
import at.koopro.wizardsandbeasts.owl.Profession;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class ExaminationDeskClientHandler {

    private ExaminationDeskClientHandler() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ExaminationDeskBlock.onClientInteract = ExaminationDeskClientHandler::openDeskScreen;
    }

    public static void openDeskScreen() {
        Minecraft mc = Minecraft.getInstance();
        Profession profession = ClientOWLCache.getProfession();
        boolean examTaken = ClientOWLCache.isExamTaken();

        if (profession != null || examTaken) {
            mc.setScreen(new OWLResultsReadOnlyScreen(
                    ClientOWLCache.getGrades(),
                    profession));
        } else {
            mc.setScreen(new OWLExamScreen());
        }
    }
}
