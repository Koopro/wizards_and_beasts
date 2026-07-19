package at.koopro.wizardsandbeasts.network.module;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleIds;
import at.koopro.wizardsandbeasts.module.ModuleState;
import at.koopro.wizardsandbeasts.module.ModuleStateService;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

/**
 * Client → server request to change a module's state or one of its settings.
 *
 * <p>The permission check in {@link #handle} is the real boundary — the admin screen not showing a control
 * is a courtesy, not security. A request from a player without operator permission is dropped silently
 * (no feedback that would help probe the surface) and logged at WARN with the player's name.
 */
@NullMarked
public record ModuleUpdateRequestPayload(Kind kind,
                                         Identifier moduleId,
                                         String state,
                                         Identifier settingKey,
                                         String encodedValue) implements CustomPacketPayload {

    public enum Kind { STATE, SETTING }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Identifier NO_KEY =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "none");

    public static final Type<ModuleUpdateRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "module_update_request"));

    public static final StreamCodec<ByteBuf, ModuleUpdateRequestPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ModuleUpdateRequestPayload decode(ByteBuf buf) {
            int ordinal = buf.readByte();
            Kind[] kinds = Kind.values();
            Kind kind = ordinal >= 0 && ordinal < kinds.length ? kinds[ordinal] : Kind.STATE;
            return new ModuleUpdateRequestPayload(
                    kind,
                    Identifier.parse(PacketCodecUtils.readString(buf)),
                    PacketCodecUtils.readString(buf),
                    Identifier.parse(PacketCodecUtils.readString(buf)),
                    PacketCodecUtils.readString(buf));
        }

        @Override
        public void encode(ByteBuf buf, ModuleUpdateRequestPayload payload) {
            buf.writeByte(payload.kind.ordinal());
            PacketCodecUtils.writeString(buf, payload.moduleId.toString());
            PacketCodecUtils.writeString(buf, payload.state);
            PacketCodecUtils.writeString(buf, payload.settingKey.toString());
            PacketCodecUtils.writeString(buf, payload.encodedValue);
        }
    };

    private static net.minecraft.server.MinecraftServer requireServer(ServerPlayer player) {
        return player.level().getServer();
    }

    public static ModuleUpdateRequestPayload ofState(Module module, ModuleState state) {
        return new ModuleUpdateRequestPayload(Kind.STATE, ModuleIds.of(module),
                state.getSerializedName(), NO_KEY, "");
    }

    public static ModuleUpdateRequestPayload ofSetting(Module module, Identifier settingKey, String encodedValue) {
        return new ModuleUpdateRequestPayload(Kind.SETTING, ModuleIds.of(module), "", settingKey, encodedValue);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ModuleUpdateRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            // Same permission node the /wandb command tree uses — one definition of "operator".
            if (!player.createCommandSourceStack().permissions()
                    .hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                LOGGER.warn("[Modules] Dropped module update from non-operator {}",
                        player.getName().getString());
                return;
            }
            Module module = ModuleIds.byId(payload.moduleId);
            if (module == null) {
                LOGGER.warn("[Modules] Dropped module update from {}: unknown module {}",
                        player.getName().getString(), payload.moduleId);
                return;
            }
            if (payload.kind == Kind.STATE) {
                ModuleState target = ModuleState.parse(payload.state);
                if (target == null) {
                    LOGGER.warn("[Modules] Dropped module update from {}: unknown state '{}'",
                            player.getName().getString(), payload.state);
                    return;
                }
                ModuleStateService.setState(requireServer(player), module, target);
            } else {
                ModuleStateService.setSetting(requireServer(player), module, payload.settingKey, payload.encodedValue);
            }
        });
    }
}
