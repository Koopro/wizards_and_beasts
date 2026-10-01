package at.koopro.wizardsandbeasts.admin.visual;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.config.AdminSettingProvider;
import at.koopro.wizardsandbeasts.admin.config.SettingBinding;
import at.koopro.wizardsandbeasts.admin.config.SettingType;
import at.koopro.wizardsandbeasts.admin.config.SettingTypes;
import at.koopro.wizardsandbeasts.visual.beam.BeamShapeKind;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisual;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisualDefaults;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisualProperty;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisualService;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * Resolves beam visual settings {@code beam/<spell>/<property>}: one per beam spell and {@link BeamVisualProperty},
 * in the Visuals section. Each defaults to the spell's authored look ({@code BeamVisualDefaults}); setting a value
 * back to that default removes the override, so "reset" always restores the code's look and the default itself is
 * never stored — it cannot be destroyed from the panel or a command.
 *
 * <p>Visual only: no gameplay rule reads these. They reach players through {@code BeamVisualService}'s sync; the one
 * server-read value, {@code impact_intensity}, only scales impact particles and the camera kick.
 */
@NullMarked
public final class BeamVisualSettingProvider implements AdminSettingProvider {

    /** Every beam spell's look values, for profiles and snapshots. */
    @Override
    public java.util.Collection<Identifier> enumerate(@Nullable MinecraftServer server) {
        java.util.List<Identifier> out = new java.util.ArrayList<>();
        for (String spell : BeamVisualDefaults.SPELLS) {
            for (BeamVisualProperty property : BeamVisualProperty.values()) {
                out.add(id(spell, property));
            }
        }
        return out;
    }

    public static final String PREFIX = "beam/";

    public static Identifier id(String spellKey, BeamVisualProperty property) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, PREFIX + spellKey + "/" + property.id());
    }

    public static boolean isBeamSetting(Identifier id) {
        return WizardsAndBeastsMod.MODID.equals(id.getNamespace()) && id.getPath().startsWith(PREFIX);
    }

    /** The spell key and property an id names, or null when it is not a well-formed beam setting id. */
    public static @Nullable Parsed parse(Identifier id) {
        if (!isBeamSetting(id)) {
            return null;
        }
        String[] parts = id.getPath().split("/");
        if (parts.length != 3) {
            return null;
        }
        String spell = BeamVisualDefaults.key(parts[1]);
        BeamVisualProperty property = BeamVisualProperty.byId(parts[2]);
        return spell == null || property == null ? null : new Parsed(spell, property);
    }

    public record Parsed(String spellKey, BeamVisualProperty property) {}

    @Override
    public @Nullable AdminSetting<?> resolve(Identifier id) {
        Parsed parsed = parse(id);
        if (parsed == null) {
            return null;
        }
        BeamVisualProperty property = parsed.property();
        String spell = parsed.spellKey();
        return switch (property.kind()) {
            case BOOL -> build(id, SettingTypes.bool(), spell, property, Boolean::parseBoolean, String::valueOf);
            case INT -> build(id, SettingTypes.integer((int) property.min(), (int) property.max()), spell, property,
                    Integer::parseInt, String::valueOf);
            case DECIMAL -> build(id, SettingTypes.decimal(property.min(), property.max(), property.step()), spell,
                    property, Double::parseDouble, String::valueOf);
            case CHOICE -> build(id, SettingTypes.enumeration(BeamShapeKind.class), spell, property,
                    BeamShapeKind::valueOf, BeamShapeKind::name);
            case COLOR -> build(id, SettingTypes.text(7, BeamVisualProperty.COLOR_TEXT), spell, property,
                    Function.identity(), Function.identity());
        };
    }

    private static <T> AdminSetting<T> build(Identifier id, SettingType<T> type, String spell,
                                             BeamVisualProperty property, Function<String, T> fromText,
                                             Function<T, String> toText) {
        return AdminSetting.builder(id, type, new Binding<>(spell, property, fromText, toText))
                .category(AdminCategory.VISUALS)
                .build();
    }

    private static MinecraftServer server() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException("no server to store a beam visual on");
        }
        return server;
    }

    /** The spell's effective value; storing the authored value removes the override. */
    private record Binding<T>(String spell, BeamVisualProperty property, Function<String, T> fromText,
                              Function<T, String> toText) implements SettingBinding<T> {
        @Override
        public T get() {
            BeamVisual effective = BeamVisualService.effective(server(), spell);
            return fromText.apply(property.text(effective == null ? authored() : effective));
        }

        @Override
        public void set(T value) {
            String canonical = property.canonical(toText.apply(value));
            if (canonical == null) {
                throw new IllegalArgumentException("not a " + property.id() + " value: " + value);
            }
            String shipped = property.text(authored());
            BeamVisualService.setOverride(server(), spell, property, canonical.equals(shipped) ? null : canonical);
        }

        @Override
        public T defaultValue() {
            return fromText.apply(property.text(authored()));
        }

        @Override
        public boolean available() {
            return ServerLifecycleHooks.getCurrentServer() != null;
        }

        private BeamVisual authored() {
            BeamVisual authored = BeamVisualService.authored(spell);
            if (authored == null) {
                throw new IllegalStateException("not a beam spell: " + spell);
            }
            return authored;
        }
    }
}
