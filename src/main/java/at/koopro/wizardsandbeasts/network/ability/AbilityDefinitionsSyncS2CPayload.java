package at.koopro.wizardsandbeasts.network.ability;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import at.koopro.wizardsandbeasts.ability.def.AbilityDefinitionRegistry;
import at.koopro.wizardsandbeasts.ability.def.AbilityInput;
import at.koopro.wizardsandbeasts.ability.def.AbilityTargeting;
import at.koopro.wizardsandbeasts.ability.def.AbilityType;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pushes the full {@link AbilityDefinition} registry to a client so the ability wheel can read metadata
 * (type/icon/name/cooldown/sortOrder) without a server round-trip. Sent on datapack sync (login + {@code
 * /reload}). Client rebuilds and swaps its {@link AbilityDefinitionRegistry} mirror.
 */
@NullMarked
public record AbilityDefinitionsSyncS2CPayload(List<AbilityDefinition> definitions) implements CustomPacketPayload {

    private static final int MAX_DEFS = 512;

    public static final Type<AbilityDefinitionsSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "ability_definitions_sync"));

    public static final StreamCodec<ByteBuf, AbilityDefinitionsSyncS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AbilityDefinitionsSyncS2CPayload decode(ByteBuf buf) {
            int count = PacketCodecUtils.readBoundedCount(buf, MAX_DEFS, "ability-definitions");
            List<AbilityDefinition> defs = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Identifier id = Identifier.parse(PacketCodecUtils.readString(buf));
                AbilityType type = typeByName(PacketCodecUtils.readString(buf));
                Identifier icon = Identifier.parse(PacketCodecUtils.readString(buf));
                String nameKey = PacketCodecUtils.readString(buf);
                String descriptionKey = PacketCodecUtils.readString(buf);
                String moduleRaw = PacketCodecUtils.readString(buf);
                Identifier module = moduleRaw.isBlank() ? null : Identifier.parse(moduleRaw);
                int cooldownTicks = PacketCodecUtils.clampNonNegative(buf.readInt());
                int sortOrder = buf.readInt();
                AbilityInput input = new AbilityInput(
                        targetingByName(PacketCodecUtils.readString(buf)),
                        PacketCodecUtils.clampNonNegative(buf.readInt()),
                        buf.readDouble());
                defs.add(new AbilityDefinition(id, type, icon, nameKey, descriptionKey, module, cooldownTicks,
                        sortOrder, input));
            }
            return new AbilityDefinitionsSyncS2CPayload(defs);
        }

        @Override
        public void encode(ByteBuf buf, AbilityDefinitionsSyncS2CPayload payload) {
            int count = Math.min(payload.definitions.size(), MAX_DEFS);
            buf.writeInt(count);
            for (int i = 0; i < count; i++) {
                AbilityDefinition def = payload.definitions.get(i);
                PacketCodecUtils.writeString(buf, def.id().toString());
                PacketCodecUtils.writeString(buf, def.type().getSerializedName());
                PacketCodecUtils.writeString(buf, def.icon().toString());
                PacketCodecUtils.writeString(buf, def.nameKey());
                PacketCodecUtils.writeString(buf, def.descriptionKey());
                Identifier module = def.module();
                PacketCodecUtils.writeString(buf, module == null ? "" : module.toString());
                buf.writeInt(Math.max(0, def.cooldownTicks()));
                buf.writeInt(def.sortOrder());
                AbilityInput input = def.input();
                PacketCodecUtils.writeString(buf, input.targeting().getSerializedName());
                buf.writeInt(Math.max(0, input.chargeTicks()));
                buf.writeDouble(input.range());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Rebuilds the registry on the client from the synced list. Called from the client payload handler. */
    public void applyToClientRegistry() {
        Map<Identifier, AbilityDefinition> map = new HashMap<>(definitions.size());
        for (AbilityDefinition def : definitions) {
            map.put(def.id(), def);
        }
        AbilityDefinitionRegistry.replaceAll(map);
    }

    public static void syncToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new AbilityDefinitionsSyncS2CPayload(new ArrayList<>(AbilityDefinitionRegistry.getAll())));
    }

    private static AbilityType typeByName(String name) {
        for (AbilityType type : AbilityType.values()) {
            if (type.getSerializedName().equals(name)) {
                return type;
            }
        }
        return AbilityType.PASSIVE;
    }

    private static AbilityTargeting targetingByName(String name) {
        for (AbilityTargeting targeting : AbilityTargeting.values()) {
            if (targeting.getSerializedName().equals(name)) {
                return targeting;
            }
        }
        return AbilityTargeting.NONE;
    }
}
