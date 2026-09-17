package at.koopro.wizardsandbeasts.network.heritage;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.event.heritage.HeritageEvents;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.heritage.ConditionOrigin;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server: the heritage, lineage and — optionally — the condition a new character begins with.
 *
 * <p>{@code conditionId} is empty for almost every character. A player who chooses to start as a werewolf or an
 * Obscurial is choosing a condition their witch or wizard already carries, not a different people to belong to, so
 * it travels alongside the lineage rather than instead of it.
 */
public record HeritageSelectC2SPayload(String typeId, String subtypeId, String conditionId)
        implements CustomPacketPayload {

    public static final Type<HeritageSelectC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "type_select"));

    public static final StreamCodec<ByteBuf, HeritageSelectC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public HeritageSelectC2SPayload decode(ByteBuf buf) {
            String typeId = PacketCodecUtils.readString(buf);
            String subtypeId = PacketCodecUtils.readString(buf);
            String conditionId = PacketCodecUtils.readString(buf);
            return new HeritageSelectC2SPayload(typeId, subtypeId, conditionId);
        }

        @Override
        public void encode(ByteBuf buf, HeritageSelectC2SPayload pkt) {
            PacketCodecUtils.writeString(buf, pkt.typeId);
            PacketCodecUtils.writeString(buf, pkt.subtypeId);
            PacketCodecUtils.writeString(buf, pkt.conditionId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HeritageSelectC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            String safeTypeId = PacketCodecUtils.normalizeIdentifier(pkt.typeId);
            String safeSubtypeId = PacketCodecUtils.normalizeIdentifier(pkt.subtypeId);
            if (safeTypeId.isBlank() || safeSubtypeId.isBlank()) {
                player.displayClientMessage(Component.literal("\u00A7cInvalid heritage selection payload."), true);
                return;
            }

            PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());

            // Prevent re-selection if locked
            if (data.isLocked()) {
                player.displayClientMessage(
                        Component.literal("\u00A7cYour heritage is already locked."), true);
                return;
            }

            Heritage heritage = Heritage.byId(safeTypeId);
            if (heritage == null) {
                player.displayClientMessage(
                        Component.literal("\u00A7cUnknown type: " + safeTypeId), true);
                return;
            }
            if (!heritage.isAlphaAvailable()) {
                player.displayClientMessage(
                        Component.translatable("message.wizards_and_beasts.type_selection.coming_soon"), true);
                return;
            }

            HeritageVariant variant = HeritageVariant.byId(safeSubtypeId);
            if (variant == null || variant.getParentHeritage() != heritage) {
                player.displayClientMessage(
                        Component.literal("\u00A7cInvalid subtype: " + safeSubtypeId), true);
                return;
            }

            // A condition is resolved before anything is committed, so a bad one refuses the whole selection
            // rather than leaving a committed character half-afflicted.
            ConditionOrigin condition = null;
            String safeConditionId = PacketCodecUtils.normalizeIdentifier(pkt.conditionId);
            if (!safeConditionId.isBlank()) {
                for (ConditionOrigin candidate : ConditionOrigin.values()) {
                    if (candidate.getId().equals(safeConditionId)) {
                        condition = candidate;
                        break;
                    }
                }
                if (condition == null) {
                    player.displayClientMessage(
                            Component.literal("\u00A7cUnknown condition: " + safeConditionId), true);
                    return;
                }
                if (!condition.condition().canBeCarriedBy(heritage, variant)) {
                    player.displayClientMessage(
                            Component.translatable("message.wizards_and_beasts.heritage.condition_not_possible",
                                    Component.translatable(condition.condition().getTranslationKey()),
                                    Component.literal(variant.getDisplayName())), true);
                    return;
                }
            }

            data.resetProfessionProgress();
            data.addProfessionPoints(3);

            // Lock, roll, re-body, re-sync. Every step of that used to be written out here, which is how
            // the two admin routes into the same change ended up each doing a different subset of it.
            HeritageAPI.commit(player, heritage, variant);

            // After the commit, never inside it: the roll and the lock belong to the lineage, and the condition
            // is something that happened to the character the commit just created.
            if (condition != null) {
                HeritageAPI.afflict(player, condition);
            }

            // Fire event
            NeoForge.EVENT_BUS.post(new HeritageEvents.PlayerHeritageSelectedEvent(player, heritage, variant));

            player.displayClientMessage(
                    Component.literal("\u00A7aYou are now a " + heritage.getDisplayName()
                            + " (" + variant.getDisplayName() + ")!"), true);
        });
    }
}
