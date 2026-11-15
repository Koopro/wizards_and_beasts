package at.koopro.wizardsandbeasts.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * The datapack registries from code that has no level to ask -- an item's {@code getName}, say. The running
 * server's when there is one (single player and dedicated servers), else the client's synced copy, which the
 * client installs at start-up so common code never names a client class.
 */
public final class SidedRegistries {

    private static volatile Supplier<HolderLookup.@Nullable Provider> client = () -> null;

    private SidedRegistries() {}

    /** Called once by the client entry point. */
    public static void installClient(Supplier<HolderLookup.@Nullable Provider> source) {
        client = source;
    }

    public static HolderLookup.@Nullable Provider get() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.registryAccess() : client.get();
    }
}
