package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

/**
 * "This spell reached this stage, here": the server's half of a spell's visual sequence. The client
 * ({@code client.vfx.SpellVfxDirector}) turns a cue into the spell's own look — a shockwave and a scorch for a
 * blasting curse, runes for a ward, creep for a curse held on someone — under its own budget.
 *
 * <p>The server decides <em>that</em> something happened and where (it alone knows a bolt hit, a ward broke, a
 * curse holds); the client decides what it looks like. A cue is a few dozen bytes, sent once per event, never per
 * particle. Stages the client already learns from other packets (the release, through the cast animation) have no
 * cue.
 *
 * @param normal  facing of the surface or direction involved (a hit block's face, back along a bolt's path)
 * @param scale   stage-specific size (a ward's radius); 1 when unused
 * @param argb    colour override (a Protego tier's colour); 0 = the spell's own colour
 * @param entity  the caster's entity id, or -1
 */
@NullMarked
public record SpellCueS2CPayload(String spellId, Stage stage, Vec3 pos, Vec3 normal, float scale, int argb,
                                 int entity) implements CustomPacketPayload {

    /** Visual reach of a cue: a little past an unforced particle, since a shockwave is seen from further. */
    public static final double RANGE = 48.0;

    public enum Stage {
        /** A bolt struck a creature. */
        HIT_ENTITY,
        /** A bolt struck a block face. */
        HIT_BLOCK,
        /** A held curse or charm is on someone; repeated on a slow beat while it holds. */
        CHANNEL,
        /** A ward went up. */
        RAISE,
        /** A ward was broken. */
        BREAK
    }

    public static final Type<SpellCueS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_cue"));

    public static final StreamCodec<ByteBuf, SpellCueS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpellCueS2CPayload decode(ByteBuf buf) {
            String id = ByteBufCodecs.stringUtf8(128).decode(buf);
            Stage stage = Stage.values()[Math.floorMod(buf.readByte(), Stage.values().length)];
            Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            Vec3 normal = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
            float scale = buf.readFloat();
            int argb = buf.readInt();
            int entity = ByteBufCodecs.VAR_INT.decode(buf);
            return new SpellCueS2CPayload(id, stage, pos, normal, scale, argb, entity);
        }

        @Override
        public void encode(ByteBuf buf, SpellCueS2CPayload p) {
            ByteBufCodecs.stringUtf8(128).encode(buf, p.spellId);
            buf.writeByte(p.stage.ordinal());
            buf.writeDouble(p.pos.x);
            buf.writeDouble(p.pos.y);
            buf.writeDouble(p.pos.z);
            buf.writeFloat((float) p.normal.x);
            buf.writeFloat((float) p.normal.y);
            buf.writeFloat((float) p.normal.z);
            buf.writeFloat(p.scale);
            buf.writeInt(p.argb);
            ByteBufCodecs.VAR_INT.encode(buf, p.entity);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerLevel level, String spellId, Stage stage, Vec3 pos, Vec3 normal, float scale,
                            int argb, int entity) {
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, RANGE,
                new SpellCueS2CPayload(spellId, stage, pos, normal, scale, argb, entity));
    }

    public static void send(ServerLevel level, String spellId, Stage stage, Vec3 pos, Vec3 normal, int entity) {
        send(level, spellId, stage, pos, normal, 1f, 0, entity);
    }
}
