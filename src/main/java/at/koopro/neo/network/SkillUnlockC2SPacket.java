package at.koopro.neo.network;

import at.koopro.neo.Neo;
import at.koopro.neo.skill.SkillSystemAPI;
import at.koopro.neo.util.ChatHelper;
import at.koopro.neo.skill.Skill;
import at.koopro.neo.skill.SkillTrees;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SkillUnlockC2SPacket(String skillId) implements CustomPacketPayload {

    public static final Type<SkillUnlockC2SPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Neo.MODID, "skill_unlock"));

    public static final StreamCodec<ByteBuf, SkillUnlockC2SPacket> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(SkillUnlockC2SPacket::new, SkillUnlockC2SPacket::skillId);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SkillUnlockC2SPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            Skill skill = SkillTrees.byId(pkt.skillId);
            if (skill == null) {
                ChatHelper.sendError(player, "Unknown skill: " + pkt.skillId);
                return;
            }

            if (SkillSystemAPI.tryUnlock(player, pkt.skillId)) {
                ChatHelper.sendSuccess(player, "Unlocked " + skill.getDisplayName() + "!");

                // Sync both skill data and spell data (in case a spell was learned)
                SkillDataSyncS2CPacket.syncToPlayer(player);
                SpellDataSyncS2CPacket.syncToPlayer(player);
            } else {
                ChatHelper.sendError(player, "Cannot unlock " + skill.getDisplayName() + ".");
            }
        });
    }
}
