package at.koopro.wizardsandbeasts.admin.broom;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.config.AdminSettingProvider;
import at.koopro.wizardsandbeasts.admin.config.SettingBinding;
import at.koopro.wizardsandbeasts.admin.config.SettingTypes;
import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.broom.rules.BroomRules;
import at.koopro.wizardsandbeasts.broom.rules.BroomRulesService;
import at.koopro.wizardsandbeasts.broom.rules.BroomStat;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Resolves broom settings from the authored broom definitions: {@code broom/<ns>/<broom>/enabled} and
 * {@code broom/<ns>/<broom>/<stat>} for every {@link BroomStat}. Each stat is a decimal with the stat's own bounds and
 * step — the Control Center draws it as a slider — defaulting to the authored value. A broom added by any datapack is
 * editable with no code. The server's speed scale applies on top of {@code max_speed} and is its own rule.
 */
@NullMarked
public final class BroomSettingProvider implements AdminSettingProvider {

    /** Every authored broom's switch and flight values, for profiles and snapshots. */
    @Override
    public java.util.Collection<Identifier> enumerate(@Nullable MinecraftServer server) {
        java.util.List<Identifier> out = new java.util.ArrayList<>();
        for (BroomDefinition broom : BroomRules.authoredAll()) {
            out.add(id(broom.id(), ENABLED));
            for (BroomStat stat : BroomStat.values()) {
                out.add(id(broom.id(), stat.id()));
            }
        }
        return out;
    }

    public static final String PREFIX = "broom/";
    public static final String ENABLED = "enabled";

    public static Identifier id(Identifier broom, String property) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
                PREFIX + broom.getNamespace() + "/" + broom.getPath() + "/" + property);
    }

    public static boolean isBroomSetting(Identifier id) {
        return WizardsAndBeastsMod.MODID.equals(id.getNamespace()) && id.getPath().startsWith(PREFIX);
    }

    @Override
    public @Nullable AdminSetting<?> resolve(Identifier id) {
        if (!isBroomSetting(id)) {
            return null;
        }
        String[] parts = id.getPath().split("/");
        if (parts.length != 4) {
            return null;
        }
        Identifier broom = Identifier.fromNamespaceAndPath(parts[1], parts[2]);
        BroomDefinition authored = BroomRules.authored(broom);
        if (authored == null) {
            return null;
        }
        String key = broom.toString();
        if (ENABLED.equals(parts[3])) {
            return AdminSetting.builder(id, SettingTypes.bool(), new EnabledBinding(broom))
                    .category(AdminCategory.TRAVEL)
                    .dangerRule((previous, candidate, shipped, setting) -> previous && !candidate ? setting.warningKey() : null)
                    .build();
        }
        BroomStat stat = BroomStat.byId(parts[3]);
        if (stat == null) {
            return null;
        }
        double shipped = shortest(stat.read(authored));
        // Bounds as written (0.005, not the float's 0.004999999888…), so the slider and tooltip read cleanly.
        var builder = AdminSetting.builder(id,
                        SettingTypes.decimal(shortest(stat.min()), shortest(stat.max()), shortest(stat.step())),
                        new StatBinding(key, stat, shipped))
                .category(AdminCategory.TRAVEL);
        if (stat == BroomStat.MAX_SPEED) {
            // Past twice its authored top speed a broom outflies every collision and landing number tuned for it.
            // Only this stat carries a rule: a rule marks the whole setting as one that may ask for confirmation.
            builder.dangerRule((previous, candidate, authoredValue, setting) ->
                    candidate > authoredValue * 2 ? setting.warningKey() : null);
        }
        return builder.build();
    }

    private static double shortest(float value) {
        return Double.parseDouble(Float.toString(value));
    }

    private static MinecraftServer server() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException("no server to store a broom override on");
        }
        return server;
    }

    private record EnabledBinding(Identifier broom) implements SettingBinding<Boolean> {
        @Override
        public Boolean get() {
            return BroomRules.enabled(broom);
        }

        @Override
        public void set(Boolean value) {
            BroomRulesService.setDisabled(server(), broom.toString(), !value);
        }

        @Override
        public Boolean defaultValue() {
            return Boolean.TRUE;
        }

        @Override
        public boolean available() {
            return ServerLifecycleHooks.getCurrentServer() != null;
        }
    }

    /** The override as stored (before the speed scale); setting the authored value removes it. */
    private record StatBinding(String broom, BroomStat stat, double shipped) implements SettingBinding<Double> {
        @Override
        public Double get() {
            Float value = BroomRules.overridesFor(broom).get(stat);
            return value == null ? shipped : shortest(value);
        }

        @Override
        public void set(Double value) {
            Optional<Float> stored = value.equals(shipped) ? Optional.empty() : Optional.of(value.floatValue());
            BroomRulesService.setStat(server(), broom, stat, stored);
        }

        @Override
        public Double defaultValue() {
            return shipped;
        }

        @Override
        public boolean available() {
            return ServerLifecycleHooks.getCurrentServer() != null;
        }
    }
}
