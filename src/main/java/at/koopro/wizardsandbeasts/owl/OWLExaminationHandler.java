package at.koopro.wizardsandbeasts.owl;

import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import at.koopro.wizardsandbeasts.heritage.data.PlayerProfessionData;
import at.koopro.wizardsandbeasts.network.owl.OWLDataSyncPayload;
import at.koopro.wizardsandbeasts.network.owl.ProfessionSyncPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NonNull;

import java.util.Map;

public final class OWLExaminationHandler {

    private OWLExaminationHandler() {}

    public static @NonNull OWLExamResult conductExam(@NonNull ServerPlayer player) {
        Map<OWLSubject, OWLGrade> grades = OWLGradeCalculator.calculateAll(player);
        player.setData(ModAttachments.OWL_DATA.get(),
                new PlayerOWLData(grades, true));
        PacketDistributor.sendToPlayer(player, new OWLDataSyncPayload(grades, true));
        return new OWLExamResult(grades, ProfessionEligibility.getEligible(player, grades));
    }

    public static void chooseProfession(@NonNull ServerPlayer player, @NonNull Profession profession) {
        PlayerOWLData owlData = player.getData(ModAttachments.OWL_DATA.get());
        if (!owlData.examTaken()) return;
        PlayerProfessionData existing = player.getData(ModAttachments.PROFESSION_DATA.get());
        if (existing.profession() != null) return;
        if (!ProfessionEligibility.isEligible(player, profession, owlData.grades())) return;

        player.setData(ModAttachments.PROFESSION_DATA.get(), new PlayerProfessionData(profession));
        PacketDistributor.sendToPlayer(player, new ProfessionSyncPayload(profession));
    }

    public static void resetExam(@NonNull ServerPlayer player) {
        player.setData(ModAttachments.OWL_DATA.get(), PlayerOWLData.DEFAULT);
        player.setData(ModAttachments.PROFESSION_DATA.get(), PlayerProfessionData.DEFAULT);
        PacketDistributor.sendToPlayer(player, new OWLDataSyncPayload(Map.of(), false));
        PacketDistributor.sendToPlayer(player, new ProfessionSyncPayload(null));
    }

    public static void syncToPlayer(@NonNull ServerPlayer player) {
        PlayerOWLData owlData = player.getData(ModAttachments.OWL_DATA.get());
        PlayerProfessionData profData = player.getData(ModAttachments.PROFESSION_DATA.get());
        PacketDistributor.sendToPlayer(player, new OWLDataSyncPayload(owlData.grades(), owlData.examTaken()));
        PacketDistributor.sendToPlayer(player, new ProfessionSyncPayload(profData.profession()));
    }
}
