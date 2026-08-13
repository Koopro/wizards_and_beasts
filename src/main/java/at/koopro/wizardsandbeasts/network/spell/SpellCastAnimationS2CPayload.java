package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.spell.def.SpellDefinition;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

/**
 * Tells every client watching a caster that a cast animation should play, and how long it runs.
 *
 * <p>This exists because the client cannot work the timing out for itself. Casting resolves in a
 * single tick — {@code SpellExecutor} fires the effect the moment the gates pass — and
 * {@code SpellCastC2SPayload} is an empty record carrying no timing at all, so any duration a client
 * used would be invented. {@code WAND_CAST_POSE_SCHEMA} §4 forbids exactly that.
 *
 * <p>Sent to trackers <em>and</em> to the caster, matching {@code PoseOverrideService.sync}:
 * {@code sendToPlayersTrackingEntity} excludes the entity's own player, so without the self-send the
 * one client guaranteed to care would be the only one not told.
 *
 * <p>Presentation only. Nothing on the server reads it back and the spell has already resolved by
 * the time it goes out.
 */
@NullMarked
public record SpellCastAnimationS2CPayload(
        int entityId,
        String spellId,
        int ticks,
        float windupEnd,
        float releaseEnd
) implements CustomPacketPayload {

    public static final Type<SpellCastAnimationS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_cast_animation"));

    public static final StreamCodec<ByteBuf, SpellCastAnimationS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpellCastAnimationS2CPayload decode(ByteBuf buf) {
            return new SpellCastAnimationS2CPayload(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    buf.readFloat(),
                    buf.readFloat());
        }

        @Override
        public void encode(ByteBuf buf, SpellCastAnimationS2CPayload pkt) {
            ByteBufCodecs.VAR_INT.encode(buf, pkt.entityId());
            ByteBufCodecs.STRING_UTF8.encode(buf, pkt.spellId());
            ByteBufCodecs.VAR_INT.encode(buf, pkt.ticks());
            buf.writeFloat(pkt.windupEnd());
            buf.writeFloat(pkt.releaseEnd());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Broadcasts a cast animation, or does nothing when the spell declares no timing.
     *
     * <p>Absent timing means instant, which is every spell authored before the field existed. Silence
     * is the correct wire behaviour for those: sending a zero-length animation would make every
     * client start and immediately abandon a clip on every cast.
     */
    public static void broadcast(ServerPlayer caster, String spellId, SpellDefinition definition) {
        definition.castTiming().ifPresent(timing -> {
            SpellCastAnimationS2CPayload payload = new SpellCastAnimationS2CPayload(
                    caster.getId(), spellId, timing.ticks(), timing.windupEnd(), timing.releaseEnd());
            PacketDistributor.sendToPlayersTrackingEntity(caster, payload);
            PacketDistributor.sendToPlayer(caster, payload);
        });
    }
}
