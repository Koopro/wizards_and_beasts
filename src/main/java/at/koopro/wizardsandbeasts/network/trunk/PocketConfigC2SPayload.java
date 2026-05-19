package at.koopro.wizardsandbeasts.network.trunk;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.block.ExpansionFocusBlockEntity;
import at.koopro.wizardsandbeasts.trunk.PocketBiomes;
import at.koopro.wizardsandbeasts.trunk.ExtensionCharmService;
import at.koopro.wizardsandbeasts.trunk.TrunkRecord;
import at.koopro.wizardsandbeasts.trunk.TrunkRegistryData;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record PocketConfigC2SPayload(BlockPos configuratorPos, int radius, String biomeKey)
        implements CustomPacketPayload {

    public static final Type<PocketConfigC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "pocket_config_c2s"));

    public static final StreamCodec<ByteBuf, PocketConfigC2SPayload> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeLong(pkt.configuratorPos().asLong());
                ByteBufCodecs.VAR_INT.encode(buf, pkt.radius());
                ByteBufCodecs.STRING_UTF8.encode(buf, pkt.biomeKey());
            },
            buf -> new PocketConfigC2SPayload(
                    BlockPos.of(buf.readLong()),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(PocketConfigC2SPayload packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.level();
            if (!(level.getBlockEntity(packet.configuratorPos()) instanceof ExpansionFocusBlockEntity be)) return;
            UUID pocketId = be.getPocketId();
            if (pocketId == null) return;
            TrunkRegistryData data = TrunkRegistryData.get(level);
            TrunkRecord record = data.getPocket(pocketId).orElse(null);
            if (record == null || !record.owner().equals(player.getUUID())) return;

            int clampedRadius = Math.max(8, Math.min(32, packet.radius()));
            String newPrimary = PocketBiomes.SELECTABLE.contains(packet.biomeKey())
                    ? packet.biomeKey() : PocketBiomes.SELECTABLE.get(0);

            List<String> currentZones = record.biomeZones().isEmpty()
                    ? record.archetype().defaultBiomeZones() : record.biomeZones();
            String oldPrimary = currentZones.get(0);
            boolean biomeChanged = !oldPrimary.equals(newPrimary);

            List<String> newZones = new ArrayList<>(currentZones);
            newZones.set(0, newPrimary);

            data.updateRecord(pocketId, r -> r.withRadius(clampedRadius).withBiomeZones(List.copyOf(newZones)));

            if (biomeChanged) {
                data.getPocket(pocketId).ifPresent(r -> ExtensionCharmService.applyBiome(level, r));
            }
            PocketStatusS2CPayload.send(player, "Pocket updated: size=" + clampedRadius + ", biome=" + newPrimary);
        });
    }
}
