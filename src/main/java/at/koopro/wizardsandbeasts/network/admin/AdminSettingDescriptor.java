package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.admin.config.ApplyMode;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.AdminLangKeys;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.config.SettingKind;
import at.koopro.wizardsandbeasts.admin.config.SettingScope;
import at.koopro.wizardsandbeasts.admin.config.SettingType;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything a client needs to draw one setting, and nothing it could use to change one.
 *
 * <p>Built on the server from the live {@link AdminSetting} for one viewer: {@link #editable()} already
 * accounts for that viewer's capabilities and the setting's scope. The client caches descriptors by id and
 * replaces only {@link #value()} as results arrive, so metadata crosses the wire once per panel session.
 *
 * <p>Bounds and options are presentation hints. The server re-validates every request against the
 * definition, never against what it once told a client.
 */
@NullMarked
public record AdminSettingDescriptor(Identifier id,
                                     AdminCategory category,
                                     SettingKind kind,
                                     SettingScope scope,
                                     ApplyMode applyMode,
                                     boolean dangerous,
                                     boolean editable,
                                     double min,
                                     double max,
                                     double step,
                                     List<String> options,
                                     int maxLength,
                                     String defaultValue,
                                     String value) {

    private static final int MAX_OPTIONS = 64;
    private static final Identifier FALLBACK_ID = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "none");

    public static AdminSettingDescriptor of(AdminSetting<?> setting, AdminContext viewer) {
        SettingType<?> type = setting.type();
        boolean available = setting.binding().available();
        return new AdminSettingDescriptor(
                setting.id(),
                setting.category(),
                type.kind(),
                setting.scope(),
                setting.applyMode(),
                setting.dangerous(),
                available && setting.writable() && viewer.canModify(setting.capability()),
                type.min(),
                type.max(),
                type.step(),
                type.options(),
                type.maxLength(),
                available ? setting.defaultText() : "",
                available ? setting.currentText() : "");
    }

    public AdminSettingDescriptor withValue(String newValue) {
        return new AdminSettingDescriptor(id, category, kind, scope, applyMode, dangerous, editable,
                min, max, step, options, maxLength, defaultValue, newValue);
    }

    public boolean restartRequired() {
        return applyMode == ApplyMode.RESTART;
    }

    public boolean isDefault() {
        return value.equals(defaultValue);
    }

    public String nameKey() {
        return AdminLangKeys.settingName(id.getPath());
    }

    public String descriptionKey() {
        return AdminLangKeys.settingDescription(id.getPath());
    }

    public String warningKey() {
        return AdminLangKeys.settingWarning(id.getPath());
    }

    // ── codec ──

    public static void write(ByteBuf buf, AdminSettingDescriptor d) {
        PacketCodecUtils.writeIdentifier(buf, d.id);
        PacketCodecUtils.writeString(buf, d.category.id());
        PacketCodecUtils.writeString(buf, d.kind.name());
        buf.writeBoolean(d.scope == SettingScope.CLIENT);
        buf.writeByte(d.applyMode.ordinal());
        buf.writeBoolean(d.dangerous);
        buf.writeBoolean(d.editable);
        buf.writeDouble(d.min);
        buf.writeDouble(d.max);
        buf.writeDouble(d.step);
        int optionCount = Math.min(d.options.size(), MAX_OPTIONS);
        buf.writeInt(optionCount);
        for (int i = 0; i < optionCount; i++) {
            PacketCodecUtils.writeString(buf, d.options.get(i));
        }
        buf.writeInt(d.maxLength);
        PacketCodecUtils.writeString(buf, d.defaultValue);
        PacketCodecUtils.writeString(buf, d.value);
    }

    public static AdminSettingDescriptor read(ByteBuf buf) {
        Identifier id = PacketCodecUtils.readIdentifier(buf, FALLBACK_ID);
        AdminCategory category = AdminCategory.byId(PacketCodecUtils.readString(buf));
        SettingKind kind = SettingKind.byName(PacketCodecUtils.readString(buf));
        SettingScope scope = buf.readBoolean() ? SettingScope.CLIENT : SettingScope.SERVER;
        ApplyMode applyMode = ApplyMode.byOrdinal(buf.readByte());
        boolean dangerous = buf.readBoolean();
        boolean editable = buf.readBoolean();
        double min = buf.readDouble();
        double max = buf.readDouble();
        double step = buf.readDouble();
        int optionCount = PacketCodecUtils.readBoundedCount(buf, MAX_OPTIONS, "admin-setting-options");
        List<String> options = new ArrayList<>(optionCount);
        for (int i = 0; i < optionCount; i++) {
            options.add(PacketCodecUtils.readString(buf));
        }
        int maxLength = buf.readInt();
        String defaultValue = PacketCodecUtils.readString(buf);
        String value = PacketCodecUtils.readString(buf);
        return new AdminSettingDescriptor(id, category == null ? AdminCategory.ADVANCED : category, kind, scope,
                applyMode, dangerous, editable, min, max, step, List.copyOf(options), maxLength, defaultValue, value);
    }
}
