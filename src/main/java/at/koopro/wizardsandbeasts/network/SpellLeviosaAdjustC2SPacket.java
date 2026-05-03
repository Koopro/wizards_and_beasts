package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.Spell;
import at.koopro.wizardsandbeasts.spell.Spells;
import at.koopro.wizardsandbeasts.spell.WandBeamChannelLogic;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SpellLeviosaAdjustC2SPacket(float distanceDelta) implements CustomPacketPayload {

    public static final Type<SpellLeviosaAdjustC2SPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_leviosa_adjust"));

    public static final StreamCodec<ByteBuf, SpellLeviosaAdjustC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpellLeviosaAdjustC2SPacket decode(ByteBuf buf) {
            return new SpellLeviosaAdjustC2SPacket(buf.readFloat());
        }

        @Override
        public void encode(ByteBuf buf, SpellLeviosaAdjustC2SPacket pkt) {
            buf.writeFloat(pkt.distanceDelta);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpellLeviosaAdjustC2SPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return;

            PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
            if (!SpellNetworkGuards.canUseWand(player, data, "leviosa_adjust")) return;
            String spellId = data.getActiveSpellId();
            if (spellId == null) return;
            Spell spell = Spells.byId(spellId);
            if (spell == null) return;
            String id = spell.getId();
            if (!"wingardium_leviosa".equals(id) && !(WizardsAndBeastsMod.MODID + ":wingardium_leviosa").equals(id)) return;
            if (data.isOnCooldown(spellId, level.getGameTime())) return;

            WandBeamChannelLogic.adjustLeviosaDistance(player, pkt.distanceDelta);
        });
    }
}
