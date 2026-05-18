package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.core.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable cast-scoped multiplier accumulator with explicit hard bounds.
 */
public final class ModifierStack {
    public static final float HARD_CAP = 3.0f;
    public static final float HARD_FLOOR = 0.25f;

    private float damage = 1.0f;
    private float cooldown = 1.0f;
    private float misfireChance = 0.0f;
    private float power = 1.0f;

    private final List<String> provenance = new ArrayList<>();

    public void multiplyDamage(float factor, String source) {
        damage *= factor;
        provenance.add(source + ":damage*x" + factor);
    }

    public void multiplyCooldown(float factor, String source) {
        cooldown *= factor;
        provenance.add(source + ":cooldown*x" + factor);
    }

    public void multiplyPower(float factor, String source) {
        power *= factor;
        provenance.add(source + ":power*x" + factor);
    }

    public void addMisfireChance(float chance, String source) {
        misfireChance += chance;
        provenance.add(source + ":misfire+" + chance);
    }

    public float finalDamage() {
        return clamp(damage);
    }

    public float finalCooldown() {
        return clamp(cooldown);
    }

    public float finalPower() {
        return clamp(power);
    }

    public float finalMisfireChance() {
        return clampUnit(misfireChance);
    }

    public List<String> provenance() {
        return Collections.unmodifiableList(provenance);
    }

    private static float clamp(float value) {
        return Math.max(HARD_FLOOR, Math.min(HARD_CAP, value));
    }

    private static float clampUnit(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
