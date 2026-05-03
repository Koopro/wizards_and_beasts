package at.koopro.wizardsandbeasts.data;

import at.koopro.wizardsandbeasts.type.TransformationState;
import at.koopro.wizardsandbeasts.type.WizSubtype;
import at.koopro.wizardsandbeasts.type.WizType;
import at.koopro.wizardsandbeasts.util.NbtHelper;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import at.koopro.wizardsandbeasts.registry.ModAttachments;

public class PlayerTypeData implements ModAttachments.NbtSerializable {
    public static final String VERSION_KEY = "DataVersion";
    public static final int CURRENT_VERSION = 1;

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
    private int professionPoints;
    private int totalProfessionPointsEarned;
    private final Set<String> unlockedProfessions = new LinkedHashSet<>();
    @Nullable
    private String selectedProfessionId;

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

    public void setCustomFlags(Map<String, String> flags) {
        customFlags.clear();
        if (flags != null) {
            customFlags.putAll(flags);
        }
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

    public int getProfessionPoints() {
        return professionPoints;
    }

    public void setProfessionPoints(int points) {
        professionPoints = Math.max(0, points);
    }

    public void addProfessionPoints(int points) {
        if (points <= 0) {
            return;
        }
        professionPoints += points;
        totalProfessionPointsEarned += points;
    }

    public boolean spendProfessionPoints(int points) {
        if (points <= 0) {
            return true;
        }
        if (professionPoints < points) {
            return false;
        }
        professionPoints -= points;
        return true;
    }

    public int getTotalProfessionPointsEarned() {
        return totalProfessionPointsEarned;
    }

    public void setTotalProfessionPointsEarned(int points) {
        totalProfessionPointsEarned = Math.max(0, points);
    }

    public Set<String> getUnlockedProfessions() {
        return Set.copyOf(unlockedProfessions);
    }

    public boolean hasUnlockedProfession(String professionId) {
        return unlockedProfessions.contains(professionId);
    }

    public void unlockProfession(String professionId) {
        if (professionId == null || professionId.isBlank()) {
            return;
        }
        unlockedProfessions.add(professionId);
    }

    public void setUnlockedProfessions(Set<String> professions) {
        unlockedProfessions.clear();
        if (professions != null) {
            unlockedProfessions.addAll(professions);
        }
    }

    @Nullable
    public String getSelectedProfessionId() {
        return selectedProfessionId;
    }

    public void setSelectedProfessionId(@Nullable String professionId) {
        selectedProfessionId = (professionId == null || professionId.isBlank()) ? null : professionId;
    }

    public void resetProfessionProgress() {
        professionPoints = 0;
        totalProfessionPointsEarned = 0;
        unlockedProfessions.clear();
        selectedProfessionId = null;
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
        resetProfessionProgress();
    }

    public void applySync(@Nullable WizType type,
                          @Nullable WizSubtype subtype,
                          boolean locked,
                          TransformationState state,
                          @Nullable String formId,
                          boolean debugOverlay,
                          Map<String, String> flags,
                          int professionPoints,
                          int totalProfessionPointsEarned,
                          Set<String> unlockedProfessions,
                          @Nullable String selectedProfessionId) {
        this.selectedType = type;
        this.selectedSubtype = subtype;
        this.locked = locked;
        this.transformationState = state == null ? TransformationState.NORMAL : state;
        this.activeFormId = formId;
        this.debugOverlay = debugOverlay;
        setCustomFlags(flags);
        this.professionPoints = Math.max(0, professionPoints);
        this.totalProfessionPointsEarned = Math.max(0, totalProfessionPointsEarned);
        setUnlockedProfessions(unlockedProfessions);
        this.selectedProfessionId = selectedProfessionId;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION_KEY, CURRENT_VERSION);
        NbtHelper.saveNullableString(tag, "Type", selectedType != null ? selectedType.getId() : null);
        NbtHelper.saveNullableString(tag, "Subtype", selectedSubtype != null ? selectedSubtype.getId() : null);
        tag.putBoolean("Locked", locked);
        NbtHelper.saveEnum(tag, "TransformState", transformationState);
        NbtHelper.saveNullableString(tag, "ActiveForm", activeFormId);
        tag.putBoolean("DebugOverlay", debugOverlay);
        NbtHelper.saveStringStringMap(tag, "FlagKeys", "FlagValues", customFlags);
        tag.putInt("ProfessionPoints", professionPoints);
        tag.putInt("TotalProfessionPointsEarned", totalProfessionPointsEarned);
        NbtHelper.saveStringSet(tag, "UnlockedProfessions", unlockedProfessions);
        NbtHelper.saveNullableString(tag, "SelectedProfessionId", selectedProfessionId);
        return tag;
    }

    public void load(CompoundTag tag) {
        PlayerTypeDataMigrator.migrate(tag);
        selectedType = tag.getString("Type").map(WizType::byId).orElse(null);
        selectedSubtype = tag.getString("Subtype").map(WizSubtype::byId).orElse(null);
        locked = tag.getBoolean("Locked").orElse(false);
        transformationState = NbtHelper.loadEnum(tag, "TransformState",
                TransformationState.class, TransformationState.NORMAL);
        activeFormId = NbtHelper.loadNullableString(tag, "ActiveForm");
        debugOverlay = tag.getBoolean("DebugOverlay").orElse(false);
        customFlags.clear();
        customFlags.putAll(NbtHelper.loadStringStringMap(tag, "FlagKeys", "FlagValues"));
        professionPoints = tag.getInt("ProfessionPoints").orElse(0);
        totalProfessionPointsEarned = tag.getInt("TotalProfessionPointsEarned").orElse(0);
        unlockedProfessions.clear();
        unlockedProfessions.addAll(NbtHelper.loadStringSet(tag, "UnlockedProfessions"));
        selectedProfessionId = NbtHelper.loadNullableString(tag, "SelectedProfessionId");
    }
}
