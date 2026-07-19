package at.koopro.wizardsandbeasts.ability.trigger;

import at.koopro.wizardsandbeasts.ability.def.AbilityTargeting;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The client's pick that accompanies a trigger request — the piece the framework was missing before
 * Apparition (a destination) and Legilimency (a mind) could ride the wheel. Always sent (as {@link #NONE}
 * for untargeted abilities) so the wire shape is fixed; the server re-validates kind and distance against
 * the definition's {@code input} before dispatch and never trusts these values as-is.
 *
 * @param kind     which of the payload fields is meaningful
 * @param blockPos block hit, {@code null} unless {@link Kind#BLOCK}
 * @param position precise world position, {@code null} unless {@link Kind#BLOCK}
 * @param entityId picked entity id, meaningful only for {@link Kind#ENTITY}
 */
@NullMarked
public record AbilityTarget(Kind kind,
                            @Nullable BlockPos blockPos,
                            @Nullable Vec3 position,
                            int entityId) {

    public enum Kind { NONE, BLOCK, ENTITY }

    public static final AbilityTarget NONE = new AbilityTarget(Kind.NONE, null, null, 0);

    public static AbilityTarget ofBlock(BlockPos blockPos, Vec3 position) {
        return new AbilityTarget(Kind.BLOCK, blockPos, position, 0);
    }

    public static AbilityTarget ofEntity(int entityId) {
        return new AbilityTarget(Kind.ENTITY, null, null, entityId);
    }

    public static final StreamCodec<ByteBuf, AbilityTarget> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AbilityTarget decode(ByteBuf buf) {
            int ordinal = buf.readByte();
            Kind[] kinds = Kind.values();
            Kind kind = ordinal >= 0 && ordinal < kinds.length ? kinds[ordinal] : Kind.NONE;
            return switch (kind) {
                case BLOCK -> ofBlock(
                        new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()),
                        new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
                case ENTITY -> ofEntity(buf.readInt());
                case NONE -> NONE;
            };
        }

        @Override
        public void encode(ByteBuf buf, AbilityTarget target) {
            buf.writeByte(target.kind.ordinal());
            switch (target.kind) {
                case BLOCK -> {
                    BlockPos pos = target.blockPos == null ? BlockPos.ZERO : target.blockPos;
                    Vec3 vec = target.position == null ? Vec3.ZERO : target.position;
                    buf.writeInt(pos.getX());
                    buf.writeInt(pos.getY());
                    buf.writeInt(pos.getZ());
                    buf.writeDouble(vec.x);
                    buf.writeDouble(vec.y);
                    buf.writeDouble(vec.z);
                }
                case ENTITY -> buf.writeInt(target.entityId);
                case NONE -> { }
            }
        }
    };

    /** True if this pick structurally satisfies {@code targeting} (field presence only — not range). */
    public boolean matches(AbilityTargeting targeting) {
        return switch (targeting) {
            case NONE -> true; // an untargeted ability ignores whatever was sent
            case BLOCK -> kind == Kind.BLOCK && blockPos != null && position != null;
            case ENTITY -> kind == Kind.ENTITY;
        };
    }
}
