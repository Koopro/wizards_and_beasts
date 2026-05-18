package at.koopro.wizardsandbeasts.apparition;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.logging.LogUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
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
                String message = obj.has("message") ? obj.get("message").getAsString() : "Something prevents you from Apparating here.";
                boolean allowAdmins = obj.has("allowAdmins") && obj.get("allowAdmins").getAsBoolean();
                register(new ApparitionWard(
                        wardId,
                        dimensionId,
                        new AABB(minX, minY, minZ, maxX, maxY, maxZ),
                        Component.literal(message),
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
                obj.addProperty("message", ward.blockMessage().getString());
                obj.addProperty("allowAdmins", ward.allowAdmins());
                arr.add(obj);
            }
            Files.writeString(file, GSON.toJson(arr));
        } catch (IOException ex) {
            LOGGER.error("Failed to save apparition wards", ex);
        }
    }

    private static void registerDefaults(MinecraftServer server) {
        Identifier hogwartsDimension = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "hogwarts");
        register(new ApparitionWard(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "hogwarts"),
                hogwartsDimension,
                centered(0, 64, 0, 200, 200, 200),
                Component.literal("You cannot Apparate within the grounds of Hogwarts."),
                true));

        Identifier overworld = ServerLevel.OVERWORLD.identifier();
        register(new ApparitionWard(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "gringotts_vaults"),
                overworld,
                centered(0, 40, 0, 100, 80, 100),
                Component.literal("Gringotts enchantments prevent Apparition.").withStyle(ChatFormatting.YELLOW),
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
