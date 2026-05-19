package at.koopro.wizardsandbeasts.client.debug;

import com.google.gson.JsonObject;

/**
 * Stores 9 transform floats for one model part.
 * Scale defaults to 1.0; offset and rotation default to 0.0.
 */
public class DebugTransformData {
    public float scaleX = 1.0f, scaleY = 1.0f, scaleZ = 1.0f;
    public float offX   = 0.0f, offY   = 0.0f, offZ   = 0.0f;
    public float rotX   = 0.0f, rotY   = 0.0f, rotZ   = 0.0f;

    public void reset() {
        scaleX = scaleY = scaleZ = 1.0f;
        offX = offY = offZ = 0.0f;
        rotX = rotY = rotZ = 0.0f;
    }

    public boolean isDefault() {
        return scaleX == 1 && scaleY == 1 && scaleZ == 1
            && offX == 0 && offY == 0 && offZ == 0
            && rotX == 0 && rotY == 0 && rotZ == 0;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        if (scaleX != 1) obj.addProperty("scaleX", scaleX);
        if (scaleY != 1) obj.addProperty("scaleY", scaleY);
        if (scaleZ != 1) obj.addProperty("scaleZ", scaleZ);
        if (offX != 0) obj.addProperty("offsetX", offX);
        if (offY != 0) obj.addProperty("offsetY", offY);
        if (offZ != 0) obj.addProperty("offsetZ", offZ);
        if (rotX != 0) obj.addProperty("rotationX", rotX);
        if (rotY != 0) obj.addProperty("rotationY", rotY);
        if (rotZ != 0) obj.addProperty("rotationZ", rotZ);
        return obj;
    }

    public static DebugTransformData fromJson(JsonObject obj) {
        DebugTransformData d = new DebugTransformData();
        if (obj.has("scaleX"))    d.scaleX = obj.get("scaleX").getAsFloat();
        if (obj.has("scaleY"))    d.scaleY = obj.get("scaleY").getAsFloat();
        if (obj.has("scaleZ"))    d.scaleZ = obj.get("scaleZ").getAsFloat();
        if (obj.has("offsetX"))   d.offX   = obj.get("offsetX").getAsFloat();
        if (obj.has("offsetY"))   d.offY   = obj.get("offsetY").getAsFloat();
        if (obj.has("offsetZ"))   d.offZ   = obj.get("offsetZ").getAsFloat();
        if (obj.has("rotationX")) d.rotX   = obj.get("rotationX").getAsFloat();
        if (obj.has("rotationY")) d.rotY   = obj.get("rotationY").getAsFloat();
        if (obj.has("rotationZ")) d.rotZ   = obj.get("rotationZ").getAsFloat();
        return d;
    }
}
