package at.koopro.wizardsandbeasts.heritage.data;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.TransformationState;
import at.koopro.wizardsandbeasts.util.NbtHelper;
import net.minecraft.nbt.CompoundTag;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import at.koopro.wizardsandbeasts.registry.ModAttachments;

public class PlayerHeritageData implements ModAttachments.NbtSerializable {
    public static final String VERSION_KEY = "DataVersion";
    public static final int CURRENT_VERSION = 1;

    @Nullable
    private Heritage selectedHeritage;
    @Nullable
    private HeritageVariant selectedHeritageVariant;
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
    public Heritage getSelectedHeritage() {
        return selectedHeritage;
    }

    public void setSelectedHeritage(@Nullable Heritage heritage) {
        this.selectedHeritage = heritage;
    }

    @Nullable
    public HeritageVariant getSelectedHeritageVariant() {
        return selectedHeritageVariant;
    }

    public void setSelectedHeritageVariant(@Nullable HeritageVariant variant) {
        this.selectedHeritageVariant = variant;
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

    public boolean hasHeritageSelected() {
        return selectedHeritage != null && selectedHeritageVariant != null;
    }

    public void reset() {
        selectedHeritage = null;
        selectedHeritageVariant = null;
        locked = false;
        transformationState = TransformationState.NORMAL;
        activeFormId = null;
        debugOverlay = false;
        customFlags.clear();
        resetProfessionProgress();
    }

    public void applySync(@Nullable Heritage heritage,
                          @Nullable HeritageVariant variant,
                          boolean locked,
                          TransformationState state,
                          @Nullable String formId,
                          boolean debugOverlay,
                          Map<String, String> flags,
                          int professionPoints,
                          int totalProfessionPointsEarned,
                          Set<String> unlockedProfessions,
                          @Nullable String selectedProfessionId) {
        this.selectedHeritage = heritage;
        this.selectedHeritageVariant = variant;
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
        NbtHelper.saveNullableString(tag, "Type", selectedHeritage != null ? selectedHeritage.getId() : null);
        NbtHelper.saveNullableString(tag, "Subtype", selectedHeritageVariant != null ? selectedHeritageVariant.getId() : null);
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
        PlayerHeritageDataMigrator.migrate(tag);
        selectedHeritage = tag.getString("Type").map(Heritage::byId).orElse(null);
        selectedHeritageVariant = tag.getString("Subtype").map(HeritageVariant::byId).orElse(null);
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
