package at.koopro.wizardsandbeasts.network.skill;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.util.ChatHelper;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SkillUnlockC2SPayload(String skillId) implements CustomPacketPayload {

    public static final Type<SkillUnlockC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "skill_unlock"));

    public static final StreamCodec<ByteBuf, SkillUnlockC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SkillUnlockC2SPayload decode(ByteBuf buf) {
            return new SkillUnlockC2SPayload(PacketCodecUtils.readString(buf));
        }

        @Override
        public void encode(ByteBuf buf, SkillUnlockC2SPayload pkt) {
            PacketCodecUtils.writeString(buf, pkt.skillId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SkillUnlockC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            String safeSkillId = PacketCodecUtils.normalizeIdentifier(pkt.skillId);
            if (safeSkillId.isBlank()) {
                ChatHelper.sendError(player, "Invalid skill id.");
                return;
            }

            Skill skill = SkillTrees.byId(safeSkillId);
            if (skill == null) {
                ChatHelper.sendError(player, "Unknown skill: " + safeSkillId);
                return;
            }

            SkillSystemAPI.UnlockCheck check = SkillSystemAPI.evaluateUnlock(player, skill);
            if (!check.allowed()) {
                if ("maxed".equals(check.reason())) {
                    ChatHelper.sendError(player, skill.getDisplayName() + " is already maxed.");
                } else if ("not_enough_points".equals(check.reason())) {
                    ChatHelper.sendError(player, "Not enough skill points for " + skill.getDisplayName() + ".");
                } else if (check.reason().startsWith("missing_prerequisite:")) {
                    String prereq = check.reason().substring("missing_prerequisite:".length());
                    ChatHelper.sendError(player, "Missing prerequisite: " + prereq + ".");
                } else if ("tree_unavailable".equals(check.reason())) {
                    ChatHelper.sendError(player, "Your heritage cannot unlock this tree.");
                } else {
                    ChatHelper.sendError(player, "Cannot unlock " + skill.getDisplayName() + ".");
                }
                return;
            }

            if (SkillSystemAPI.tryUnlock(player, safeSkillId)) {
                ChatHelper.sendSuccess(player, "Unlocked " + skill.getDisplayName() + "!");

                // Skill unlock no longer grants spells directly; skill sync is sufficient.
                SkillDataSyncS2CPayload.syncToPlayer(player);
            }
        });
    }
}
