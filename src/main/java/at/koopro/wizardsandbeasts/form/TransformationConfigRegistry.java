package at.koopro.wizardsandbeasts.form;

import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps "fromFormId:toFormId" pairs to {@link TransformationConfig}.
 * If no specific config exists for a pair, {@link TransformationConfig#DEFAULT} is used.
 */
public final class TransformationConfigRegistry {

    private static final Map<String, TransformationConfig> CONFIGS = new HashMap<>();

    static {
        register("werewolf_human", "werewolf_wolf",
                new TransformationConfig(20, TransformationConfig.ScreenEffect.DARK_FADE, true));
        register("werewolf_wolf", "werewolf_human",
                new TransformationConfig(15, TransformationConfig.ScreenEffect.DARK_FADE, true));

        register("obscurial_human", "obscurial_dark",
                new TransformationConfig(25, TransformationConfig.ScreenEffect.SMOKE_CLOUD, true));
        register("obscurial_dark", "obscurial_human",
                new TransformationConfig(20, TransformationConfig.ScreenEffect.SMOKE_CLOUD, true));

        register("veela_human", "veela_harpy",
                new TransformationConfig(15, TransformationConfig.ScreenEffect.PARTICLE_BURST, true));
        register("veela_harpy", "veela_human",
                new TransformationConfig(15, TransformationConfig.ScreenEffect.PARTICLE_BURST, true));

        register("vampire_default", "vampire_bat",
                new TransformationConfig(10, TransformationConfig.ScreenEffect.SMOKE_CLOUD, false));
        register("vampire_bat", "vampire_default",
                new TransformationConfig(10, TransformationConfig.ScreenEffect.SMOKE_CLOUD, false));

        register("merpeople_land", "merpeople_water",
                new TransformationConfig(15, TransformationConfig.ScreenEffect.PARTICLE_BURST, false));
        register("merpeople_water", "merpeople_land",
                new TransformationConfig(15, TransformationConfig.ScreenEffect.PARTICLE_BURST, false));
    }

    private static void register(String fromFormId, String toFormId, TransformationConfig config) {
        CONFIGS.put(key(fromFormId, toFormId), config);
    }

    /**
     * Returns the transformation config for transitioning between two forms,
     * or {@link TransformationConfig#DEFAULT} if no specific config exists.
     */
    public static TransformationConfig getOrDefault(String fromFormId, String toFormId) {
        TransformationConfig config = CONFIGS.get(key(fromFormId, toFormId));
        return config != null ? config : TransformationConfig.DEFAULT;
    }

    /**
     * Returns the transformation config, or null if no specific config exists.
     */
    @Nullable
    public static TransformationConfig get(String fromFormId, String toFormId) {
        return CONFIGS.get(key(fromFormId, toFormId));
    }

    private static String key(String from, String to) {
        return from + ":" + to;
    }

    private TransformationConfigRegistry() {}
}
