package at.koopro.wizardsandbeasts.network;

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

        SkillDataSyncS2CPacket original = new SkillDataSyncS2CPacket(3, 7, 12, skills);
        ByteBuf buf = Unpooled.buffer();
        try {
            SkillDataSyncS2CPacket.STREAM_CODEC.encode(buf, original);
            SkillDataSyncS2CPacket decoded = SkillDataSyncS2CPacket.STREAM_CODEC.decode(buf);
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
        TypeDataSyncS2CPacket original = new TypeDataSyncS2CPacket(
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
            TypeDataSyncS2CPacket.STREAM_CODEC.encode(buf, original);
            TypeDataSyncS2CPacket decoded = TypeDataSyncS2CPacket.STREAM_CODEC.decode(buf);
            assertEquals(original.syncVersion(), decoded.syncVersion());
            assertEquals(original.typeId(), decoded.typeId());
            assertEquals(original.subtypeId(), decoded.subtypeId());
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
