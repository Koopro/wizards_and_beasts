package at.koopro.wizardsandbeasts.floo;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public record FlooDestinationDto(
        @NonNull String networkAddress,
        @NonNull String dimensionName,
        boolean isEnabled
) {
    public static final StreamCodec<ByteBuf, FlooDestinationDto> STREAM_CODEC = StreamCodec.of(
            (buf, dto) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, dto.networkAddress);
                ByteBufCodecs.STRING_UTF8.encode(buf, dto.dimensionName);
                ByteBufCodecs.BOOL.encode(buf, dto.isEnabled);
            },
            buf -> new FlooDestinationDto(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf)
            )
    );

    public static final StreamCodec<ByteBuf, List<FlooDestinationDto>> LIST_STREAM_CODEC = StreamCodec.of(
            (buf, list) -> {
                ByteBufCodecs.VAR_INT.encode(buf, list.size());
                for (FlooDestinationDto dto : list) {
                    STREAM_CODEC.encode(buf, dto);
                }
            },
            buf -> {
                int size = ByteBufCodecs.VAR_INT.decode(buf);
                List<FlooDestinationDto> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(STREAM_CODEC.decode(buf));
                }
                return list;
            }
    );
}
