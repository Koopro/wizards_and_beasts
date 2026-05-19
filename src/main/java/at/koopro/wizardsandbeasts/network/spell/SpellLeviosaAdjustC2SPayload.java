package at.koopro.wizardsandbeasts.network.spell;
import at.koopro.wizardsandbeasts.network.SpellNetworkGuards;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.beam.WandBeamChannelLogic;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SpellLeviosaAdjustC2SPayload(float distanceDelta) implements CustomPacketPayload {

    public static final Type<SpellLeviosaAdjustC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_leviosa_adjust"));

    public static final StreamCodec<ByteBuf, SpellLeviosaAdjustC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpellLeviosaAdjustC2SPayload decode(ByteBuf buf) {
            return new SpellLeviosaAdjustC2SPayload(buf.readFloat());
        }

        @Override
        public void encode(ByteBuf buf, SpellLeviosaAdjustC2SPayload pkt) {
            buf.writeFloat(pkt.distanceDelta);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpellLeviosaAdjustC2SPayload pkt, IPayloadContext ctx) {
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
