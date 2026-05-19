package at.koopro.wizardsandbeasts.client.ui;

public record ObscurialUiModel(
        boolean obscurialType,
        String activeFormId,
        float control,
        float pressure,
        float stress,
        long lockoutUntilTick,
        long ventCooldownUntilTick,
        boolean rageActive
) {
    public boolean isDarkForm() {
        return ObscurialUiFlags.FORM_DARK.equals(activeFormId);
    }

    public boolean isHumanForm() {
        return ObscurialUiFlags.FORM_HUMAN.equals(activeFormId);
    }

    public float controlRatio() {
        return control / ObscurialUiFlags.MAX_METER;
    }

    public float pressureRatio() {
        return pressure / ObscurialUiFlags.MAX_METER;
    }

    public float stressRatio() {
        return stress / ObscurialUiFlags.MAX_METER;
    }

    public String controlTier() {
        if (control <= 25.0f) return "Catastrophic";
        if (control <= 60.0f) return "Fracturing";
        return "Controlled";
    }

    public String stressTier() {
        if (stress >= 70.0f) return "Volatile";
        if (stress >= 40.0f) return "Agitated";
        return "Calm";
    }
}
