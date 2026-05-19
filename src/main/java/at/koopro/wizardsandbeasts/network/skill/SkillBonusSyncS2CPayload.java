package at.koopro.wizardsandbeasts.network.skill;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.skill.state.ClientSkillBonusCache;
import at.koopro.wizardsandbeasts.skill.PlayerSkillBonusData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SkillBonusSyncS2CPayload(PlayerSkillBonusData bonusData) implements CustomPacketPayload {
    public static final Type<SkillBonusSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "skill_bonus_sync"));

    public static final StreamCodec<ByteBuf, SkillBonusSyncS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SkillBonusSyncS2CPayload decode(ByteBuf buf) {
            String serialized = PacketCodecUtils.readString(buf);
            PlayerSkillBonusData decoded = PlayerSkillBonusData.CODEC.parse(
                    com.mojang.serialization.JsonOps.INSTANCE,
                    com.google.gson.JsonParser.parseString(serialized)
            ).result().orElse(PlayerSkillBonusData.DEFAULT);
            return new SkillBonusSyncS2CPayload(decoded);
        }

        @Override
        public void encode(ByteBuf buf, SkillBonusSyncS2CPayload payload) {
            var encoded = PlayerSkillBonusData.CODEC.encodeStart(
                    com.mojang.serialization.JsonOps.INSTANCE,
                    payload.bonusData
            ).result().orElseGet(() -> new com.google.gson.JsonObject());
            PacketCodecUtils.writeString(buf, encoded.toString());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SkillBonusSyncS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientSkillBonusCache.set(payload.bonusData));
    }
}
