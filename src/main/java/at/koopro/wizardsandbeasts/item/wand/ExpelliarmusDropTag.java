package at.koopro.wizardsandbeasts.item.wand;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** Marks a wand {@link net.minecraft.world.entity.item.ItemEntity} stack dropped by Expelliarmus. */
public record ExpelliarmusDropTag(UUID victimUuid, UUID disarmerUuid, long disarmGameTime) {
    public static final Codec<ExpelliarmusDropTag> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            UUIDUtil.CODEC.fieldOf("victim").forGetter(ExpelliarmusDropTag::victimUuid),
            UUIDUtil.CODEC.fieldOf("disarmer").forGetter(ExpelliarmusDropTag::disarmerUuid),
            Codec.LONG.fieldOf("time").forGetter(ExpelliarmusDropTag::disarmGameTime)
    ).apply(inst, ExpelliarmusDropTag::new));

    public static final StreamCodec<ByteBuf, ExpelliarmusDropTag> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ExpelliarmusDropTag::victimUuid,
            UUIDUtil.STREAM_CODEC, ExpelliarmusDropTag::disarmerUuid,
            net.minecraft.network.codec.ByteBufCodecs.VAR_LONG, ExpelliarmusDropTag::disarmGameTime,
            ExpelliarmusDropTag::new);
}
