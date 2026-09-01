package at.koopro.wizardsandbeasts.apparition;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.logging.LogUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;

public final class ApparitionWardRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "wizards_and_beasts_wards.json";
    private static final Map<Identifier, ApparitionWard> WARDS = new LinkedHashMap<>();

    private ApparitionWardRegistry() {
    }

    public static void register(ApparitionWard ward) {
        WARDS.put(ward.wardId(), ward);
    }

    public static boolean remove(Identifier wardId) {
        return WARDS.remove(wardId) != null;
    }

    public static Collection<ApparitionWard> all() {
        return java.util.List.copyOf(WARDS.values());
    }

    public static @Nullable ApparitionWard findBlockingWard(ServerLevel level, CommandSourceStack asSource, AABB playerBoundsAtDestination) {
        Identifier dimensionId = level.dimension().identifier();
        boolean isAdmin = asSource.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        for (ApparitionWard ward : WARDS.values()) {
            if (!ward.dimensionId().equals(dimensionId)) {
                continue;
            }
            if (!ward.bounds().intersects(playerBoundsAtDestination)) {
                continue;
            }
            if (ward.allowAdmins() && isAdmin) {
                continue;
            }
            return ward;
        }
        return null;
    }

    public static void load(MinecraftServer server) {
        WARDS.clear();
        registerDefaults(server);
        Path file = getPath(server);
        if (!Files.exists(file)) {
            save(server);
            return;
        }
        try {
            String raw = Files.readString(file);
            JsonArray arr = JsonParser.parseString(raw).getAsJsonArray();
            for (com.google.gson.JsonElement element : arr) {
                JsonObject obj = element.getAsJsonObject();
                Identifier wardId = Identifier.parse(obj.get("wardId").getAsString());
                Identifier dimensionId = Identifier.parse(obj.get("dimensionId").getAsString());
                double minX = obj.get("minX").getAsDouble();
                double minY = obj.get("minY").getAsDouble();
                double minZ = obj.get("minZ").getAsDouble();
                double maxX = obj.get("maxX").getAsDouble();
                double maxY = obj.get("maxY").getAsDouble();
                double maxZ = obj.get("maxZ").getAsDouble();
                boolean allowAdmins = obj.has("allowAdmins") && obj.get("allowAdmins").getAsBoolean();
                register(new ApparitionWard(
                        wardId,
                        dimensionId,
                        new AABB(minX, minY, minZ, maxX, maxY, maxZ),
                        readMessage(obj),
                        allowAdmins));
            }
        } catch (Exception ex) {
            LOGGER.error("Failed to load apparition wards", ex);
        }
    }

    public static void save(MinecraftServer server) {
        Path file = getPath(server);
        try {
            Files.createDirectories(file.getParent());
            JsonArray arr = new JsonArray();
            for (ApparitionWard ward : WARDS.values()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("wardId", ward.wardId().toString());
                obj.addProperty("dimensionId", ward.dimensionId().toString());
                obj.addProperty("minX", ward.bounds().minX);
                obj.addProperty("minY", ward.bounds().minY);
                obj.addProperty("minZ", ward.bounds().minZ);
                obj.addProperty("maxX", ward.bounds().maxX);
                obj.addProperty("maxY", ward.bounds().maxY);
                obj.addProperty("maxZ", ward.bounds().maxZ);
                writeMessage(obj, ward.blockMessage());
                obj.addProperty("allowAdmins", ward.allowAdmins());
                arr.add(obj);
            }
            Files.writeString(file, GSON.toJson(arr));
        } catch (IOException ex) {
            LOGGER.error("Failed to save apparition wards", ex);
        }
    }

    /** The message shown by a ward whose entry says nothing about one. */
    public static final String DEFAULT_MESSAGE_KEY = "apparition.wizards_and_beasts.ward.default";

    /**
     * Reads a ward's refusal message, preferring a translation key over a baked string.
     *
     * <p>Two shapes, because wards come from two places. The built-ins are code and speak in lang keys, so
     * every language sees its own sentence. An operator adding a ward by command types a sentence, and that
     * sentence is theirs — it is stored as written and never looked up.
     */
    private static Component readMessage(JsonObject obj) {
        if (obj.has("messageKey")) {
            return Component.translatable(obj.get("messageKey").getAsString());
        }
        if (obj.has("message")) {
            return Component.literal(obj.get("message").getAsString());
        }
        return Component.translatable(DEFAULT_MESSAGE_KEY);
    }

    /**
     * Writes a ward's refusal message back in whichever of the two shapes it came in.
     *
     * <p>This exists because the old {@code getString()} round-trip was a one-way door for anything
     * translatable. A server has no mod lang files — those are client assets — so resolving a mod key
     * server-side yields the raw key, which was then saved as literal text and read back as literal text.
     * The first save would have turned every built-in ward's message into the string
     * {@code "apparition.wizards_and_beasts.ward.hogwarts"} on every player's screen, permanently.
     */
    private static void writeMessage(JsonObject obj, Component message) {
        if (message.getContents() instanceof TranslatableContents translatable) {
            obj.addProperty("messageKey", translatable.getKey());
            return;
        }
        obj.addProperty("message", message.getString());
    }

    private static void registerDefaults(MinecraftServer server) {
        Identifier hogwartsDimension = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "hogwarts");
        register(new ApparitionWard(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "hogwarts"),
                hogwartsDimension,
                centered(0, 64, 0, 200, 200, 200),
                Component.translatable("apparition.wizards_and_beasts.ward.hogwarts"),
                true));

        Identifier overworld = ServerLevel.OVERWORLD.identifier();
        register(new ApparitionWard(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "gringotts_vaults"),
                overworld,
                centered(0, 40, 0, 100, 80, 100),
                // Unstyled, like the other default. A style cannot survive the save round-trip (only the
                // key is written) and the refusal toast does its own colouring anyway, so a colour here
                // would appear once and then quietly never again.
                Component.translatable("apparition.wizards_and_beasts.ward.gringotts"),
                false));
    }

    private static AABB centered(double cx, double cy, double cz, double sx, double sy, double sz) {
        double hx = sx / 2.0;
        double hy = sy / 2.0;
        double hz = sz / 2.0;
        return new AABB(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz);
    }

    private static Path getPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE_NAME);
    }
}
