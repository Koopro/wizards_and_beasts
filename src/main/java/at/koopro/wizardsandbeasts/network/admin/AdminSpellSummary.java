package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.NullMarked;

/**
 * One row of the spell browser: enough to list, search, filter and badge a spell, and nothing editable.
 *
 * @param id              the spell's registered id
 * @param displayName     a lang key or literal, as the spell authors it
 * @param category        {@code SpellCategory} name — data-driven, filtered on as-is
 * @param castType        {@code CastType} name
 * @param legalClass      {@code LegalClass} name from the datapack spell law
 * @param castAllowed     the server would let it be cast (enabled, and not a refused Unforgivable)
 * @param enabled         its own enabled switch
 * @param dark            shown in the Dark Arts section
 * @param unforgivable    classed Unforgivable
 * @param implemented     its behaviour is written ({@code SpellImplementationState})
 * @param overridden      an administrator has changed at least one of its values
 * @param requirement     the effective prerequisite in {@code SpellRequirementText} form
 * @param color           the spell's authored colour, for the browser's swatch
 * @param summary         a one-line description composed from the spell's real values
 */
@NullMarked
public record AdminSpellSummary(String id, String displayName, String category, String castType, String legalClass,
                                boolean castAllowed, boolean enabled, boolean dark, boolean unforgivable,
                                boolean implemented, boolean overridden, String requirement, int color,
                                String summary) {

    public static void write(ByteBuf buf, AdminSpellSummary s) {
        PacketCodecUtils.writeString(buf, s.id);
        PacketCodecUtils.writeString(buf, s.displayName);
        PacketCodecUtils.writeString(buf, s.category);
        PacketCodecUtils.writeString(buf, s.castType);
        PacketCodecUtils.writeString(buf, s.legalClass);
        int flags = (s.castAllowed ? 1 : 0) | (s.enabled ? 2 : 0) | (s.dark ? 4 : 0) | (s.unforgivable ? 8 : 0)
                | (s.implemented ? 16 : 0) | (s.overridden ? 32 : 0);
        buf.writeByte(flags);
        PacketCodecUtils.writeString(buf, s.requirement);
        buf.writeInt(s.color);
        PacketCodecUtils.writeString(buf, s.summary);
    }

    public static AdminSpellSummary read(ByteBuf buf) {
        String id = PacketCodecUtils.readString(buf);
        String name = PacketCodecUtils.readString(buf);
        String category = PacketCodecUtils.readString(buf);
        String castType = PacketCodecUtils.readString(buf);
        String legal = PacketCodecUtils.readString(buf);
        int flags = buf.readUnsignedByte();
        String requirement = PacketCodecUtils.readString(buf);
        int color = buf.readInt();
        String summary = PacketCodecUtils.readString(buf);
        return new AdminSpellSummary(id, name, category, castType, legal, (flags & 1) != 0, (flags & 2) != 0,
                (flags & 4) != 0, (flags & 8) != 0, (flags & 16) != 0, (flags & 32) != 0, requirement, color, summary);
    }
}
