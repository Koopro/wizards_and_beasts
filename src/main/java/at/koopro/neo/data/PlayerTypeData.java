package at.koopro.neo.data;

import at.koopro.neo.type.TransformationState;
import at.koopro.neo.type.WizSubtype;
import at.koopro.neo.type.WizType;
import at.koopro.neo.util.NbtHelper;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

import at.koopro.neo.registry.ModAttachments;

public class PlayerTypeData implements ModAttachments.NbtSerializable {

    @Nullable
    private WizType selectedType;
    @Nullable
    private WizSubtype selectedSubtype;
    private boolean locked;
    private TransformationState transformationState = TransformationState.NORMAL;
    @Nullable
    private String activeFormId;
    private boolean debugOverlay;
    private final Map<String, String> customFlags = new HashMap<>();

    @Nullable
    public WizType getSelectedType() {
        return selectedType;
    }

    public void setSelectedType(@Nullable WizType type) {
        this.selectedType = type;
    }

    @Nullable
    public WizSubtype getSelectedSubtype() {
        return selectedSubtype;
    }

    public void setSelectedSubtype(@Nullable WizSubtype subtype) {
        this.selectedSubtype = subtype;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public TransformationState getTransformationState() {
        return transformationState;
    }

    public void setTransformationState(TransformationState state) {
        this.transformationState = state;
    }

    @Nullable
    public String getActiveFormId() {
        return activeFormId;
    }

    public void setActiveFormId(@Nullable String formId) {
        this.activeFormId = formId;
    }

    public boolean isDebugOverlay() {
        return debugOverlay;
    }

    public void setDebugOverlay(boolean debugOverlay) {
        this.debugOverlay = debugOverlay;
    }

    public Map<String, String> getCustomFlags() {
        return customFlags;
    }

    public void setFlag(String key, String value) {
        customFlags.put(key, value);
    }

    @Nullable
    public String getFlag(String key) {
        return customFlags.get(key);
    }

    public void removeFlag(String key) {
        customFlags.remove(key);
    }

    public boolean hasTypeSelected() {
        return selectedType != null && selectedSubtype != null;
    }

    public void reset() {
        selectedType = null;
        selectedSubtype = null;
        locked = false;
        transformationState = TransformationState.NORMAL;
        activeFormId = null;
        debugOverlay = false;
        customFlags.clear();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        NbtHelper.saveNullableString(tag, "Type", selectedType != null ? selectedType.getId() : null);
        NbtHelper.saveNullableString(tag, "Subtype", selectedSubtype != null ? selectedSubtype.getId() : null);
        tag.putBoolean("Locked", locked);
        NbtHelper.saveEnum(tag, "TransformState", transformationState);
        NbtHelper.saveNullableString(tag, "ActiveForm", activeFormId);
        tag.putBoolean("DebugOverlay", debugOverlay);
        NbtHelper.saveStringStringMap(tag, "FlagKeys", "FlagValues", customFlags);
        return tag;
    }

    public void load(CompoundTag tag) {
        selectedType = tag.getString("Type").map(WizType::byId).orElse(null);
        selectedSubtype = tag.getString("Subtype").map(WizSubtype::byId).orElse(null);
        locked = tag.getBoolean("Locked").orElse(false);
        transformationState = NbtHelper.loadEnum(tag, "TransformState",
                TransformationState.class, TransformationState.NORMAL);
        activeFormId = NbtHelper.loadNullableString(tag, "ActiveForm");
        debugOverlay = tag.getBoolean("DebugOverlay").orElse(false);
        customFlags.clear();
        customFlags.putAll(NbtHelper.loadStringStringMap(tag, "FlagKeys", "FlagValues"));
    }
}
