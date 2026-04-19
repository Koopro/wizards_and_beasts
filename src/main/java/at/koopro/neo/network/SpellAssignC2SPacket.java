package at.koopro.neo.network;

import at.koopro.neo.Neo;
import at.koopro.neo.data.PlayerSpellData;
import at.koopro.neo.registry.ModAttachments;
import at.koopro.neo.spell.Spell;
import at.koopro.neo.spell.Spells;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SpellAssignC2SPacket(int slotIndex, String spellId) implements CustomPacketPayload {

    public static final Type<SpellAssignC2SPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Neo.MODID, "spell_assign"));

    public static final StreamCodec<ByteBuf, SpellAssignC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpellAssignC2SPacket decode(ByteBuf buf) {
            int slot = buf.readInt();
            int len = buf.readInt();
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            return new SpellAssignC2SPacket(slot, new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public void encode(ByteBuf buf, SpellAssignC2SPacket pkt) {
            buf.writeInt(pkt.slotIndex);
            byte[] bytes = pkt.spellId.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            buf.writeInt(bytes.length);
            buf.writeBytes(bytes);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpellAssignC2SPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (pkt.slotIndex < 0 || pkt.slotIndex > 3) return;

            PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());

            if (pkt.spellId.isEmpty()) {
                data.setLoadoutSpell(pkt.slotIndex, null);
            } else {
                Spell spell = Spells.byId(pkt.spellId);
                if (spell == null || !data.knowsSpell(pkt.spellId)) return;
                data.setLoadoutSpell(pkt.slotIndex, pkt.spellId);
            }

            SpellDataSyncS2CPacket.syncToPlayer(player);
        });
    }
}
