package at.koopro.wizardsandbeasts.client.debug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton state manager for the Model Debug Editor.
 * Tracks active creature type, selected part, edit mode, axis,
 * and per-creature per-part transform data.
 */
public class ModelDebugEditor {
    private static final ModelDebugEditor INSTANCE = new ModelDebugEditor();
    public static ModelDebugEditor get() { return INSTANCE; }

    private boolean active = false;
    private int selectedPartIndex = 0;
    private EditMode mode = EditMode.SCALE;
    private Axis axis = Axis.Y;
    private String activeCreatureType = "werewolf";
    private ModelMode modelMode = ModelMode.VANILLA;

    /** Vanilla player model part names. "root" is PoseStack-level, not a real ModelPart. */
    public static final String[] VANILLA_PARTS = {
        "root",
        "head", "hat", "body",
        "left_arm", "right_arm",
        "left_leg", "right_leg",
        "jacket",
        "left_sleeve", "right_sleeve",
        "left_pants", "right_pants"
    };

    /** GeckoLib bone names, populated dynamically from the model. */
    private final List<String> geckoBoneNames = new ArrayList<>();

    /** Per-creature, per-part transform data. */
    private final Map<String, Map<String, DebugTransformData>> creatureData = new HashMap<>();

    private ModelDebugEditor() {}

    public DebugTransformData getData(String part) {
        return creatureData
            .computeIfAbsent(activeCreatureType, k -> new HashMap<>())
            .computeIfAbsent(part, k -> new DebugTransformData());
    }

    public String[] getCurrentPartNames() {
        return modelMode == ModelMode.GECKOLIB
            ? geckoBoneNames.toArray(new String[0])
            : VANILLA_PARTS;
    }

    public void populateGeckoBones(List<String> boneNames) {
        geckoBoneNames.clear();
        geckoBoneNames.add("root");
        geckoBoneNames.addAll(boneNames);
    }

    public void adjustValue(float delta) {
        String[] parts = getCurrentPartNames();
        if (parts.length == 0 || selectedPartIndex >= parts.length) return;
        DebugTransformData data = getData(parts[selectedPartIndex]);
        switch (mode) {
            case SCALE -> {
                switch (axis) {
                    case X -> data.scaleX += delta;
                    case Y -> data.scaleY += delta;
                    case Z -> data.scaleZ += delta;
                }
            }
            case OFFSET -> {
                switch (axis) {
                    case X -> data.offX += delta;
                    case Y -> data.offY += delta;
                    case Z -> data.offZ += delta;
                }
            }
            case ROTATION -> {
                switch (axis) {
                    case X -> data.rotX += delta;
                    case Y -> data.rotY += delta;
                    case Z -> data.rotZ += delta;
                }
            }
        }
    }

    // --- Toggling & cycling ---

    public void toggle() { active = !active; }
    public boolean isActive() { return active; }

    public void cyclePart(int direction) {
        String[] parts = getCurrentPartNames();
        if (parts.length == 0) return;
        selectedPartIndex = Math.floorMod(selectedPartIndex + direction, parts.length);
    }

    public void cycleMode() {
        EditMode[] modes = EditMode.values();
        mode = modes[(mode.ordinal() + 1) % modes.length];
    }

    public void setAxis(Axis a) { axis = a; }

    public void resetCurrentPart() {
        String[] parts = getCurrentPartNames();
        if (parts.length == 0 || selectedPartIndex >= parts.length) return;
        getData(parts[selectedPartIndex]).reset();
    }

    public void toggleModelMode() {
        modelMode = modelMode == ModelMode.VANILLA ? ModelMode.GECKOLIB : ModelMode.VANILLA;
        selectedPartIndex = 0;
    }

    // --- Getters ---

    public EditMode getMode() { return mode; }
    public Axis getAxis() { return axis; }
    public int getSelectedIndex() { return selectedPartIndex; }
    public String getCreatureType() { return activeCreatureType; }
    public ModelMode getModelMode() { return modelMode; }
    public Map<String, Map<String, DebugTransformData>> getAllData() { return creatureData; }

    public void setCreatureType(String type) {
        activeCreatureType = type;
        selectedPartIndex = 0;
    }

    /** Loads previously exported data (e.g. from JSON config). */
    public void loadData(Map<String, Map<String, DebugTransformData>> data) {
        creatureData.clear();
        creatureData.putAll(data);
    }

    public enum EditMode { SCALE, OFFSET, ROTATION }
    public enum Axis { X, Y, Z }
    public enum ModelMode { VANILLA, GECKOLIB }
}
