package at.koopro.wizardsandbeasts.network.floo;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.floo.hearth.HearthPattern;
import at.koopro.wizardsandbeasts.floo.hearth.HearthPatterns;
import at.koopro.wizardsandbeasts.network.PayloadBroadcast;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

/**
 * Server to client: the hearth styles (floo_hearth/*.json), so the handbook's build guide draws exactly the build the
 * server will accept. Sent on join and on {@code /reload}, like the handbook chapters themselves.
 */
@NullMarked
public record SyncHearthPatternsPayload(Map<Identifier, HearthPattern> patterns) implements CustomPacketPayload {

    public static final Type<SyncHearthPatternsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "sync_hearth_patterns"));

    public static final StreamCodec<ByteBuf, SyncHearthPatternsPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodec(Codec.unboundedMap(Identifier.CODEC, HearthPattern.CODEC))
                    .map(SyncHearthPatternsPayload::new, SyncHearthPatternsPayload::patterns);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
    public static final class SyncOnDatapack {
        @SubscribeEvent
        public static void onDatapackSync(OnDatapackSyncEvent event) {
            PayloadBroadcast.toSyncTarget(event, new SyncHearthPatternsPayload(HearthPatterns.all()));
        }
    }
}
