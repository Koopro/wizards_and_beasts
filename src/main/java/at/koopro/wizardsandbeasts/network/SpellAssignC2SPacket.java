package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.Spell;
import at.koopro.wizardsandbeasts.spell.Spells;
import at.koopro.wizardsandbeasts.spell.cast.SpellRejectCodes;
import at.koopro.wizardsandbeasts.type.ObscurialRules;
import at.koopro.wizardsandbeasts.type.Heritage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.chat.Component;

public record SpellAssignC2SPacket(int slotIndex, String spellId) implements CustomPacketPayload {

    public static final Type<SpellAssignC2SPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_assign"));

    public static final StreamCodec<ByteBuf, SpellAssignC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpellAssignC2SPacket decode(ByteBuf buf) {
            int slot = buf.readInt();
            String spellId = PacketCodecUtils.readString(buf);
            return new SpellAssignC2SPacket(slot, spellId);
        }

        @Override
        public void encode(ByteBuf buf, SpellAssignC2SPacket pkt) {
            buf.writeInt(pkt.slotIndex);
            PacketCodecUtils.writeString(buf, pkt.spellId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpellAssignC2SPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
            if (!SpellNetworkGuards.canUseWand(player, data, "assign")) {
                return;
            }
            if (!SpellNetworkGuards.isValidSlot(player, data, pkt.slotIndex, "assign")) {
                return;
            }
            String safeSpellId = PacketCodecUtils.normalizeIdentifier(pkt.spellId);

            if (safeSpellId.isEmpty()) {
                data.setLoadoutSpell(pkt.slotIndex, null);
            } else {
                Spell spell = Spells.byId(safeSpellId);
                if (spell == null) {
                    data.incrementRejectReason(SpellRejectCodes.ASSIGN_UNKNOWN_SPELL);
                    player.displayClientMessage(Component.literal("\u00A7cUnknown spell id."), true);
                    return;
                }
                if (!data.knowsSpell(safeSpellId)) {
                    data.incrementRejectReason(SpellRejectCodes.ASSIGN_UNLEARNED_SPELL);
                    player.displayClientMessage(Component.literal("\u00A7cYou have not learned that spell."), true);
                    return;
                }
                if (ObscurialRules.isObscurialAbility(spell)) {
                    data.incrementRejectReason(SpellRejectCodes.ASSIGN_OBSCURIAL_ABILITY);
                    player.displayClientMessage(Component.literal("\u00A75Obscurial abilities are not assignable as spells."), true);
                    return;
                }
                Heritage type = player.getData(ModAttachments.HERITAGE_DATA.get()).getSelectedHeritage();
                if (!ObscurialRules.canHeritageUseSpell(type, spell)) {
                    data.incrementRejectReason(SpellRejectCodes.ASSIGN_TYPE_RESTRICTED_SPELL);
                    player.displayClientMessage(Component.literal("\u00A75Only Obscurials can equip this spell."), true);
                    return;
                }
                data.setLoadoutSpell(pkt.slotIndex, safeSpellId);
            }

            SpellDataSyncS2CPacket.syncToPlayer(player);
        });
    }
}
