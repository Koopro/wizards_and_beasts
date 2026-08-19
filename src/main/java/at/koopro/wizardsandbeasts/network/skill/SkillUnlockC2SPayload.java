package at.koopro.wizardsandbeasts.network.skill;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import net.minecraft.network.chat.Component;
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

    /**
     * Every branch here reports through a toast, never chat.
     *
     * <p>This payload only ever fires from a click inside the skill web screen, and chat renders
     * <em>underneath</em> an open screen. The refusals used to go to chat, so clicking a locked node
     * looked like the game had simply ignored the click — the reason was being sent, just somewhere
     * the player could not see it.
     *
     * <p>The toast title is the skill name, which makes the dedup token per-node: clicking the same
     * locked node repeatedly replaces one toast, while trying three different nodes still says three
     * different things.
     */
    public static void handle(SkillUnlockC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            String safeSkillId = PacketCodecUtils.normalizeIdentifier(pkt.skillId);
            Skill skill = safeSkillId.isBlank() ? null : SkillTrees.byId(safeSkillId);
            if (skill == null) {
                PlayerFeedback.refuse(player,
                        Component.translatable(L + "refused"),
                        Component.translatable(L + "reason.unknown_skill"));
                return;
            }

            SkillSystemAPI.UnlockCheck check = SkillSystemAPI.evaluateUnlock(player, skill);
            if (!check.allowed()) {
                PlayerFeedback.refuse(player, skillName(skill), reasonOf(check.reason()));
                return;
            }

            if (SkillSystemAPI.tryUnlock(player, safeSkillId)) {
                PlayerFeedback.unlocked(player, skillName(skill),
                        Component.translatable(L + "reason.unlocked"));

                // Skill unlock no longer grants spells directly; skill sync is sufficient.
                SkillDataSyncS2CPayload.syncToPlayer(player);
            }
        });
    }

    private static final String L = "skill.wizards_and_beasts.unlock.";

    /**
     * Reason code to a body line. The codes come from {@code SkillSystemAPI.evaluateUnlock}; an
     * unrecognised one falls through to the generic refusal rather than showing a bare code.
     */
    private static Component reasonOf(String reason) {
        String key = switch (reason) {
            case "maxed", "not_enough_points", "not_adjacent", "tree_unavailable" -> reason;
            case "requirement_unmet" -> "sealed";
            default -> "denied";
        };
        return Component.translatable(L + "reason." + key);
    }

    /**
     * A skill's name as a Component the client can translate.
     *
     * <p>Node names are a mix: the filler nodes carry a lang key, the hand-authored ones
     * carry literal English. `translatable` on a literal renders the literal unchanged, so
     * one call covers both and the mix can be migrated without touching this again.
     */
    private static Component skillName(at.koopro.wizardsandbeasts.skill.Skill skill) {
        return Component.translatable(skill.getDisplayName());
    }
}
