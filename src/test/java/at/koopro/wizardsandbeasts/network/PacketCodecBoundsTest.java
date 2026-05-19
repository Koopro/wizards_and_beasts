package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.network.form.FormChangeRequestC2SPayload;
import at.koopro.wizardsandbeasts.network.form.TransitionEndS2CPayload;
import at.koopro.wizardsandbeasts.network.form.TransitionStartS2CPayload;
import at.koopro.wizardsandbeasts.network.heritage.HeritageDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.heritage.HeritageSelectC2SPayload;
import at.koopro.wizardsandbeasts.network.map.MapSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.skill.SkillDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.skill.SkillUnlockC2SPayload;
import at.koopro.wizardsandbeasts.network.spell.ObscurialAbilityUseC2SPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellAssignC2SPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellDataSyncS2CPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PacketCodecBoundsTest {

    @Test
    void spellDataSync_decode_rejectsOversizedKnownSpellCount() {
        ByteBuf buf = Unpooled.buffer();
        try {
            buf.writeInt(1); // sync version
            buf.writeInt(PacketCodecUtils.MAX_KNOWN_SPELLS + 1);
            assertThrows(IllegalArgumentException.class, () -> SpellDataSyncS2CPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void mapSync_decode_rejectsOversizedEntryCount() {
        ByteBuf buf = Unpooled.buffer();
        try {
            buf.writeInt(PacketCodecUtils.MAX_MAP_ENTRIES + 1);
            assertThrows(IllegalArgumentException.class, () -> MapSyncS2CPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void skillSync_decode_rejectsOversizedUnlockedSkillCount() {
        ByteBuf buf = Unpooled.buffer();
        try {
            buf.writeInt(1); // sync version
            buf.writeInt(0); // skill points
            buf.writeInt(0); // total points
            buf.writeInt(PacketCodecUtils.MAX_UNLOCKED_SKILLS + 1);
            assertThrows(IllegalArgumentException.class, () -> SkillDataSyncS2CPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void typeSync_decode_rejectsOversizedCustomFlagCount() {
        ByteBuf buf = Unpooled.buffer();
        try {
            buf.writeInt(1); // sync version
            PacketCodecUtils.writeString(buf, "wizard");
            PacketCodecUtils.writeString(buf, "pure_blood");
            buf.writeBoolean(true);
            PacketCodecUtils.writeString(buf, "NORMAL");
            PacketCodecUtils.writeString(buf, "human_default");
            buf.writeBoolean(false);
            buf.writeInt(PacketCodecUtils.MAX_CUSTOM_FLAGS + 1);
            buf.writeInt(0);
            buf.writeInt(0);
            buf.writeInt(0);
            PacketCodecUtils.writeString(buf, "");
            buf.writeBoolean(false);
            assertThrows(IllegalArgumentException.class, () -> HeritageDataSyncS2CPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void typeSync_decode_rejectsOversizedUnlockedProfessionCount() {
        ByteBuf buf = Unpooled.buffer();
        try {
            buf.writeInt(1); // sync version
            PacketCodecUtils.writeString(buf, "wizardkind");
            PacketCodecUtils.writeString(buf, "pure_blood");
            buf.writeBoolean(true);
            PacketCodecUtils.writeString(buf, "NORMAL");
            PacketCodecUtils.writeString(buf, "");
            buf.writeBoolean(false);
            buf.writeInt(0); // flags
            buf.writeInt(0); // profession points
            buf.writeInt(0); // total profession points
            buf.writeInt(PacketCodecUtils.MAX_UNLOCKED_PROFESSIONS + 1);
            assertThrows(IllegalArgumentException.class, () -> HeritageDataSyncS2CPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void spellAssign_decode_rejectsOversizedSpellId() {
        ByteBuf buf = Unpooled.buffer();
        try {
            buf.writeInt(0);
            writeOversizedString(buf);
            assertThrows(IllegalArgumentException.class, () -> SpellAssignC2SPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void skillUnlock_decode_rejectsOversizedSkillId() {
        ByteBuf buf = Unpooled.buffer();
        try {
            writeOversizedString(buf);
            assertThrows(IllegalArgumentException.class, () -> SkillUnlockC2SPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void typeSelect_decode_rejectsOversizedTypeId() {
        ByteBuf buf = Unpooled.buffer();
        try {
            writeOversizedString(buf);
            PacketCodecUtils.writeString(buf, "wizard");
            assertThrows(IllegalArgumentException.class, () -> HeritageSelectC2SPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void formChange_decode_rejectsOversizedFormId() {
        ByteBuf buf = Unpooled.buffer();
        try {
            PacketCodecUtils.writeUUID(buf, java.util.UUID.randomUUID());
            writeOversizedString(buf);
            assertThrows(IllegalArgumentException.class, () -> FormChangeRequestC2SPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void obscurialAbilityUse_decode_rejectsOversizedAbilityId() {
        ByteBuf buf = Unpooled.buffer();
        try {
            writeOversizedString(buf);
            assertThrows(IllegalArgumentException.class, () -> ObscurialAbilityUseC2SPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void transitionStart_decode_rejectsOversizedFromFormId() {
        ByteBuf buf = Unpooled.buffer();
        try {
            PacketCodecUtils.writeUUID(buf, java.util.UUID.randomUUID());
            writeOversizedString(buf);
            PacketCodecUtils.writeString(buf, "obscurial_dark");
            buf.writeInt(40);
            buf.writeInt(0);
            assertThrows(IllegalArgumentException.class, () -> TransitionStartS2CPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void transitionEnd_decode_rejectsOversizedFormId() {
        ByteBuf buf = Unpooled.buffer();
        try {
            PacketCodecUtils.writeUUID(buf, java.util.UUID.randomUUID());
            writeOversizedString(buf);
            assertThrows(IllegalArgumentException.class, () -> TransitionEndS2CPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }

    private static void writeOversizedString(ByteBuf buf) {
        buf.writeInt(PacketCodecUtils.MAX_STRING_BYTES + 1);
    }
}
