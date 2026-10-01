package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.spell.network.SpellTuningClient;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.spell.tuning.SpellOverride;
import at.koopro.wizardsandbeasts.spell.tuning.SpellTuningSnapshot;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Server → every client: the whole spell administration state (per-spell overrides and global rules).
 *
 * <p>Sent on join and after any change — to everyone, not only administrators, because every client reads
 * it: the HUD's cooldown sweep and the spell menu show the server's cooldowns, and a disabled spell is shown
 * as disabled. A full snapshot rather than a delta; it is a few dozen entries on a busy server and cannot
 * drift out of order.
 */
@NullMarked
public record SpellTuningSyncS2CPayload(SpellTuningSnapshot snapshot) implements CustomPacketPayload {

    private static final int MAX_OVERRIDES = 4096;
    private static final int ENABLED = 1;
    private static final int COOLDOWN = 1 << 1;
    private static final int DAMAGE = 1 << 2;
    private static final int RANGE = 1 << 3;
    private static final int REQUIREMENT = 1 << 4;
    private static final int SKILL = 1 << 5;

    public static final Type<SpellTuningSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_tuning_sync"));

    public static final StreamCodec<ByteBuf, SpellTuningSyncS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpellTuningSyncS2CPayload decode(ByteBuf buf) {
            SpellTuningSnapshot.Globals globals = new SpellTuningSnapshot.Globals(
                    finite(buf.readFloat()), finite(buf.readFloat()), finite(buf.readFloat()),
                    buf.readBoolean(), buf.readBoolean());
            int count = PacketCodecUtils.readBoundedCount(buf, MAX_OVERRIDES, "spell-overrides");
            Map<String, SpellOverride> overrides = new HashMap<>(count);
            for (int i = 0; i < count; i++) {
                String id = PacketCodecUtils.readString(buf);
                int flags = buf.readUnsignedByte();
                overrides.put(id, new SpellOverride(
                        (flags & ENABLED) != 0 ? Optional.of(buf.readBoolean()) : Optional.empty(),
                        (flags & COOLDOWN) != 0 ? Optional.of(buf.readInt()) : Optional.empty(),
                        (flags & DAMAGE) != 0 ? Optional.of(finite(buf.readFloat())) : Optional.empty(),
                        (flags & RANGE) != 0 ? Optional.of(finite(buf.readFloat())) : Optional.empty(),
                        (flags & REQUIREMENT) != 0 ? Optional.of(PacketCodecUtils.readString(buf)) : Optional.empty(),
                        (flags & SKILL) != 0 ? Optional.of(PacketCodecUtils.readString(buf)) : Optional.empty()));
            }
            return new SpellTuningSyncS2CPayload(new SpellTuningSnapshot(overrides, globals));
        }

        @Override
        public void encode(ByteBuf buf, SpellTuningSyncS2CPayload payload) {
            SpellTuningSnapshot.Globals globals = payload.snapshot.globals();
            buf.writeFloat(globals.damageMultiplier());
            buf.writeFloat(globals.cooldownMultiplier());
            buf.writeFloat(globals.rangeMultiplier());
            buf.writeBoolean(globals.allowUnforgivables());
            buf.writeBoolean(globals.blockDamage());
            Map<String, SpellOverride> overrides = payload.snapshot.overrides();
            int count = Math.min(overrides.size(), MAX_OVERRIDES);
            buf.writeInt(count);
            int written = 0;
            for (Map.Entry<String, SpellOverride> entry : overrides.entrySet()) {
                if (written++ >= count) {
                    break;
                }
                SpellOverride o = entry.getValue();
                PacketCodecUtils.writeString(buf, entry.getKey());
                int flags = (o.enabled().isPresent() ? ENABLED : 0) | (o.cooldownTicks().isPresent() ? COOLDOWN : 0)
                        | (o.damage().isPresent() ? DAMAGE : 0) | (o.range().isPresent() ? RANGE : 0)
                        | (o.requirement().isPresent() ? REQUIREMENT : 0) | (o.requiredSkill().isPresent() ? SKILL : 0);
                buf.writeByte(flags);
                o.enabled().ifPresent(buf::writeBoolean);
                o.cooldownTicks().ifPresent(buf::writeInt);
                o.damage().ifPresent(buf::writeFloat);
                o.range().ifPresent(buf::writeFloat);
                o.requirement().ifPresent(text -> PacketCodecUtils.writeString(buf, text));
                o.requiredSkill().ifPresent(text -> PacketCodecUtils.writeString(buf, text));
            }
        }
    };

    /** A non-finite multiplier from a hostile server would poison every formula; treat it as neutral. */
    private static float finite(float value) {
        return Float.isFinite(value) ? value : 1.0f;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SpellTuningSyncS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SpellTuningClient.accept(payload.snapshot()));
    }
}
