package at.koopro.wizardsandbeasts.network.ability;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.data.PlayerAbilityData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public record AbilityDataSyncPayload(PlayerAbilityData data) implements CustomPacketPayload {
    public static final Type<AbilityDataSyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "ability_data_sync"));

    public static final StreamCodec<ByteBuf, AbilityDataSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AbilityDataSyncPayload decode(ByteBuf buf) {
            boolean apparitionUnlocked = buf.readBoolean();
            boolean apparitionLicensed = buf.readBoolean();
            int apparitionCooldownTicks = PacketCodecUtils.clampNonNegative(buf.readInt());
            int splinchSeverity = Math.max(0, Math.min(2, buf.readInt()));
            int splinchTicksRemaining = PacketCodecUtils.clampNonNegative(buf.readInt());
            float occlumencyLevel = clamp01(buf.readFloat());
            int legilimencyCooldownTicks = PacketCodecUtils.clampNonNegative(buf.readInt());
            boolean animagusUnlocked = buf.readBoolean();
            String animagusFormId = readNullableString(buf);
            boolean animagusRegistered = buf.readBoolean();
            boolean currentlyTransformed = buf.readBoolean();
            boolean wolfsbaneActive = buf.readBoolean();
            boolean parseltongueSpeaker = buf.readBoolean();
            String parseltongueSource = readNullableString(buf);
            boolean metamorphmagus = buf.readBoolean();
            String currentDisguiseFormId = readNullableString(buf);
            float wandlessCastingLevel = clamp01(buf.readFloat());
            int flagCount = PacketCodecUtils.readBoundedCount(buf, 128, "ability-flags");
            java.util.Set<String> abilityFlags = new java.util.LinkedHashSet<>(Math.max(4, flagCount));
            for (int i = 0; i < flagCount; i++) {
                String flag = PacketCodecUtils.readString(buf);
                if (!flag.isBlank()) {
                    abilityFlags.add(flag);
                }
            }
            return new AbilityDataSyncPayload(new PlayerAbilityData(
                    apparitionUnlocked,
                    apparitionLicensed,
                    apparitionCooldownTicks,
                    splinchSeverity,
                    splinchTicksRemaining,
                    occlumencyLevel,
                    legilimencyCooldownTicks,
                    animagusUnlocked,
                    animagusFormId,
                    animagusRegistered,
                    currentlyTransformed,
                    wolfsbaneActive,
                    parseltongueSpeaker,
                    parseltongueSource,
                    metamorphmagus,
                    currentDisguiseFormId,
                    wandlessCastingLevel,
                    java.util.Set.copyOf(abilityFlags)));
        }

        @Override
        public void encode(ByteBuf buf, AbilityDataSyncPayload payload) {
            PlayerAbilityData data = payload.data;
            buf.writeBoolean(data.apparitionUnlocked());
            buf.writeBoolean(data.apparitionLicensed());
            buf.writeInt(Math.max(0, data.apparitionCooldownTicks()));
            buf.writeInt(Math.max(0, Math.min(2, data.splinchSeverity())));
            buf.writeInt(Math.max(0, data.splinchTicksRemaining()));
            buf.writeFloat(clamp01(data.occlumencyLevel()));
            buf.writeInt(Math.max(0, data.legilimencyCooldownTicks()));
            buf.writeBoolean(data.animagusUnlocked());
            writeNullableString(buf, data.animagusFormId());
            buf.writeBoolean(data.animagusRegistered());
            buf.writeBoolean(data.currentlyTransformed());
            buf.writeBoolean(data.wolfsbaneActive());
            buf.writeBoolean(data.parseltongueSpeaker());
            writeNullableString(buf, data.parseltongueSource());
            buf.writeBoolean(data.metamorphmagus());
            writeNullableString(buf, data.currentDisguiseFormId());
            buf.writeFloat(clamp01(data.wandlessCastingLevel()));
            buf.writeInt(data.abilityFlags().size());
            for (String flag : data.abilityFlags()) {
                PacketCodecUtils.writeString(buf, flag);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void syncToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new AbilityDataSyncPayload(player.getData(ModAttachments.PLAYER_ABILITY_DATA.get())));
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static void writeNullableString(ByteBuf buf, String value) {
        PacketCodecUtils.writeString(buf, value == null ? "" : value);
    }

    private static String readNullableString(ByteBuf buf) {
        String value = PacketCodecUtils.readString(buf);
        return value.isBlank() ? null : value;
    }
}
