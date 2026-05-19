package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.network.heritage.HeritageDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.skill.SkillDataSyncS2CPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillTypeSyncCodecTest {

    @Test
    void skillSync_roundTrip_preservesTypedFields() {
        Map<String, Integer> skills = new HashMap<>();
        skills.put("spell_mastery.basic", 2);
        skills.put("dark_arts.shadow", 1);

        SkillDataSyncS2CPayload original = new SkillDataSyncS2CPayload(3, 7, 12, skills);
        ByteBuf buf = Unpooled.buffer();
        try {
            SkillDataSyncS2CPayload.STREAM_CODEC.encode(buf, original);
            SkillDataSyncS2CPayload decoded = SkillDataSyncS2CPayload.STREAM_CODEC.decode(buf);
            assertEquals(original.syncVersion(), decoded.syncVersion());
            assertEquals(original.skillPoints(), decoded.skillPoints());
            assertEquals(original.totalPointsEarned(), decoded.totalPointsEarned());
            assertEquals(original.unlockedSkills(), decoded.unlockedSkills());
        } finally {
            buf.release();
        }
    }

    @Test
    void typeSync_roundTrip_preservesTypedFields() {
        Map<String, String> flags = new HashMap<>();
        flags.put("example", "true");
        Set<String> unlockedProfessions = new LinkedHashSet<>();
        unlockedProfessions.add("wizard_apprentice");
        HeritageDataSyncS2CPayload original = new HeritageDataSyncS2CPayload(
                11,
                "wizardkind",
                "pure_blood",
                true,
                "NORMAL",
                "wolf_form",
                false,
                flags,
                5,
                9,
                unlockedProfessions,
                "wizard_apprentice",
                true);

        ByteBuf buf = Unpooled.buffer();
        try {
            HeritageDataSyncS2CPayload.STREAM_CODEC.encode(buf, original);
            HeritageDataSyncS2CPayload decoded = HeritageDataSyncS2CPayload.STREAM_CODEC.decode(buf);
            assertEquals(original.syncVersion(), decoded.syncVersion());
            assertEquals(original.heritageId(), decoded.heritageId());
            assertEquals(original.variantId(), decoded.variantId());
            assertEquals(original.locked(), decoded.locked());
            assertEquals(original.transformationState(), decoded.transformationState());
            assertEquals(original.activeFormId(), decoded.activeFormId());
            assertEquals(original.debugOverlay(), decoded.debugOverlay());
            assertEquals(original.customFlags(), decoded.customFlags());
            assertEquals(original.professionPoints(), decoded.professionPoints());
            assertEquals(original.totalProfessionPointsEarned(), decoded.totalProfessionPointsEarned());
            assertEquals(original.unlockedProfessions(), decoded.unlockedProfessions());
            assertEquals(original.selectedProfessionId(), decoded.selectedProfessionId());
            assertEquals(original.openSelector(), decoded.openSelector());
        } finally {
            buf.release();
        }
    }
}
