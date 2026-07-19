package at.koopro.wizardsandbeasts.network.module;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.module.ClientModuleState;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleIds;
import at.koopro.wizardsandbeasts.module.ModuleState;
import at.koopro.wizardsandbeasts.module.data.ModuleStateData;
import at.koopro.wizardsandbeasts.module.settings.ModuleSettingsValues;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server → client snapshot of every module's state and settings. Sent on join and after any accepted
 * change. A full snapshot rather than a delta because the map is one entry per module — a couple of dozen
 * short strings — and a snapshot cannot drift out of order the way a stream of deltas can.
 */
@NullMarked
public record ModuleStateSyncPayload(Map<Identifier, ModuleState> states,
                                     Map<Identifier, Map<Identifier, JsonElement>> settings)
        implements CustomPacketPayload {

    private static final int MAX_ENTRIES = 256;

    public static final Type<ModuleStateSyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "module_state_sync"));

    public static final StreamCodec<ByteBuf, ModuleStateSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ModuleStateSyncPayload decode(ByteBuf buf) {
            int stateCount = PacketCodecUtils.readBoundedCount(buf, MAX_ENTRIES, "module-states");
            Map<Identifier, ModuleState> states = new LinkedHashMap<>();
            for (int i = 0; i < stateCount; i++) {
                Identifier id = Identifier.parse(PacketCodecUtils.readString(buf));
                ModuleState state = ModuleState.parse(PacketCodecUtils.readString(buf));
                states.put(id, state == null ? ModuleState.DISABLED : state);
            }
            int moduleCount = PacketCodecUtils.readBoundedCount(buf, MAX_ENTRIES, "module-settings");
            Map<Identifier, Map<Identifier, JsonElement>> settings = new LinkedHashMap<>();
            for (int i = 0; i < moduleCount; i++) {
                Identifier moduleId = Identifier.parse(PacketCodecUtils.readString(buf));
                int valueCount = PacketCodecUtils.readBoundedCount(buf, MAX_ENTRIES, "module-setting-values");
                Map<Identifier, JsonElement> values = new LinkedHashMap<>();
                for (int v = 0; v < valueCount; v++) {
                    Identifier key = Identifier.parse(PacketCodecUtils.readString(buf));
                    values.put(key, JsonParser.parseString(PacketCodecUtils.readString(buf)));
                }
                settings.put(moduleId, values);
            }
            return new ModuleStateSyncPayload(states, settings);
        }

        @Override
        public void encode(ByteBuf buf, ModuleStateSyncPayload payload) {
            buf.writeInt(Math.min(payload.states.size(), MAX_ENTRIES));
            payload.states.forEach((id, state) -> {
                PacketCodecUtils.writeString(buf, id.toString());
                PacketCodecUtils.writeString(buf, state.getSerializedName());
            });
            buf.writeInt(Math.min(payload.settings.size(), MAX_ENTRIES));
            payload.settings.forEach((moduleId, values) -> {
                PacketCodecUtils.writeString(buf, moduleId.toString());
                buf.writeInt(Math.min(values.size(), MAX_ENTRIES));
                values.forEach((key, json) -> {
                    PacketCodecUtils.writeString(buf, key.toString());
                    PacketCodecUtils.writeString(buf, json.toString());
                });
            });
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ModuleStateSyncPayload payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> ClientModuleState.accept(payload));
    }

    // ── construction ──

    private static ModuleStateSyncPayload snapshot(MinecraftServer server) {
        ModuleStateData data = ModuleStateData.get(server.overworld());
        Map<Identifier, ModuleState> states = new LinkedHashMap<>();
        data.allStates().forEach((module, state) -> states.put(ModuleIds.of(module), state));

        Map<Identifier, Map<Identifier, JsonElement>> settings = new LinkedHashMap<>();
        data.allSettings().forEach((module, values) -> {
            if (!values.isEmpty()) {
                settings.put(ModuleIds.of(module), new LinkedHashMap<>(values.raw()));
            }
        });
        return new ModuleStateSyncPayload(states, settings);
    }

    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, snapshot(player.level().getServer()));
    }

    public static void broadcast(MinecraftServer server) {
        ModuleStateSyncPayload payload = snapshot(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** Decoded states keyed by {@link Module}, dropping ids this build no longer knows. */
    public Map<Module, ModuleState> resolvedStates() {
        Map<Module, ModuleState> resolved = new LinkedHashMap<>();
        states.forEach((id, state) -> {
            Module module = ModuleIds.byId(id);
            if (module != null) {
                resolved.put(module, state);
            }
        });
        return resolved;
    }

    public Map<Module, ModuleSettingsValues> resolvedSettings() {
        Map<Module, ModuleSettingsValues> resolved = new LinkedHashMap<>();
        settings.forEach((moduleId, values) -> {
            Module module = ModuleIds.byId(moduleId);
            if (module != null) {
                resolved.put(module, ModuleSettingsValues.ofRaw(values));
            }
        });
        return resolved;
    }
}
