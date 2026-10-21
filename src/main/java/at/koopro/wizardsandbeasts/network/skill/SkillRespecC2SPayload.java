package at.koopro.wizardsandbeasts.network.skill;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import at.koopro.wizardsandbeasts.skill.build.SkillRespecService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The skill screen's two respec doors: unlearn one level of a node (right-click), or start over (the confirm
 * screen). The server re-quotes and re-checks everything — the client only ever asks.
 */
public record SkillRespecC2SPayload(boolean fullRespec, String skillId) implements CustomPacketPayload {

    public static final Type<SkillRespecC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "skill_respec"));

    public static final StreamCodec<ByteBuf, SkillRespecC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SkillRespecC2SPayload decode(ByteBuf buf) {
            return new SkillRespecC2SPayload(buf.readBoolean(), PacketCodecUtils.readString(buf));
        }

        @Override
        public void encode(ByteBuf buf, SkillRespecC2SPayload pkt) {
            buf.writeBoolean(pkt.fullRespec);
            PacketCodecUtils.writeString(buf, pkt.skillId);
        }
    };

    public static SkillRespecC2SPayload unlearn(String skillId) {
        return new SkillRespecC2SPayload(false, skillId);
    }

    public static SkillRespecC2SPayload startOver() {
        return new SkillRespecC2SPayload(true, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static final String L = "skill.wizards_and_beasts.respec.";

    /** Toasts, never chat: this only fires from inside the skill screen, which chat draws underneath. */
    public static void handle(SkillRespecC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (pkt.fullRespec) {
                SkillRespecService.Outcome outcome = SkillRespecService.respec(player);
                if (outcome.done()) {
                    PlayerFeedback.toast(player, NoticeKind.SUCCESS, Component.translatable(L + "done.title"),
                            Component.translatable(L + "done.body", outcome.quote().refundPoints(), fee(outcome.quote())));
                } else {
                    PlayerFeedback.refuse(player, Component.translatable(L + "refused"), reason(outcome));
                }
                return;
            }
            String safeId = PacketCodecUtils.normalizeIdentifier(pkt.skillId);
            Skill skill = safeId.isBlank() ? null : SkillTrees.byId(safeId);
            Component name = skill == null ? Component.translatable(L + "refused")
                    : Component.translatable(skill.getDisplayName());
            SkillRespecService.Outcome outcome = SkillRespecService.unlearn(player, safeId);
            if (outcome.done()) {
                PlayerFeedback.toast(player, NoticeKind.SUCCESS, name,
                        Component.translatable(L + "unlearned", outcome.quote().refundPoints(), fee(outcome.quote())));
            } else {
                PlayerFeedback.refuse(player, name, reason(outcome));
            }
        });
    }

    private static Component fee(SkillRespecService.Quote quote) {
        return quote.feeKnuts() <= 0
                ? Component.translatable(quote.free() ? L + "fee.first_free" : L + "fee.free")
                : Component.literal(CurrencyHelper.formatFromKnuts(quote.feeKnuts()));
    }

    private static Component reason(SkillRespecService.Outcome outcome) {
        return switch (outcome.result()) {
            case "would_strand" -> Component.translatable(L + "reason.would_strand",
                    outcome.quote().stranded().size());
            case "cannot_afford" -> Component.translatable(L + "reason.cannot_afford", fee(outcome.quote()));
            case "module_disabled", "unknown_skill", "not_allocated", "nothing_to_respec" ->
                    Component.translatable(L + "reason." + outcome.result());
            default -> Component.translatable(L + "reason.denied");
        };
    }
}
